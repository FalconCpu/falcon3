private lateinit var currentFunc : Function

// ================================================================
//                         Rvalues
// ================================================================

fun TstExpr.codeGenRvalue() : Reg {
    return when (this) {
        is TstVariable -> currentFunc.getVar(symbol)
        is TstIntLit -> currentFunc.addMov(value)
        is TstBinop -> currentFunc.addAlu(op, lhs.codeGenRvalue(), rhs.codeGenRvalue())

        is TstCall -> {
            if (expr is TstFunctionName) {
                val argSym = args.map { it.codeGenRvalue() }
                for ((index, arg) in argSym.withIndex())
                    currentFunc.addMov(allMachineRegs[index + 1], arg)
                currentFunc.addInstr(InstrCall(expr.symbol.function))
                if (type == TypeUnit || type == TypeNothing)
                    regZero
                else
                    currentFunc.addMov(regResult)
            } else {
                TODO("Indirect function call")
            }
        }

        is TstIndex -> {
            val exprReg = expr.codeGenRvalue()
            val indexReg = index.codeGenRvalue()
            val lengthReg = currentFunc.addLoadMem(exprReg, sizeField)
            val size = type.sizeInBytes()
            val indexScaled = currentFunc.addIndexOp(size, indexReg, lengthReg)
            val indexAdded = currentFunc.addAlu(AluOp.ADD_I, exprReg, indexScaled)
            currentFunc.addLoadMem(size, indexAdded, 0)
        }

        is TstAnd -> TODO()
        is TstBreak -> TODO()
        is TstContinue -> TODO()
        is TstError -> TODO()
        is TstFunctionName -> TODO()
        is TstGlobalVar -> TODO()
        is TstIfExpr -> TODO()

        is TstMember -> {
            val exprReg = expr.codeGenRvalue()
            currentFunc.addLoadMem(exprReg, field)
        }

        is TstMinus -> TODO()
        is TstNot -> TODO()
        is TstOr -> TODO()
        is TstRange -> TODO()
        is TstReallit -> TODO()

        is TstReturn -> {
            if (expr!=null)
                currentFunc.addMov(regResult, expr.codeGenRvalue())
            currentFunc.addJump(currentFunc.retLabel)
            regZero   // return value is not used, but we need a value.
        }

        is TstStringlit -> currentFunc.addLea(ValueString.create(value, TypeString))

        is TstNewArray -> {
            if (local) {
                require(size is TstIntLit)
                val numElements = size.value
                val numElementsReg = currentFunc.addMov(numElements)
                val elementSize = (type as TypeArray).elementType.sizeInBytes()
                val stackOffset = currentFunc.stackAlloc(numElements * elementSize+4)  // +4 to allow for size field
                val ret = currentFunc.addAlu(AluOp.ADD_I, allMachineRegs[31], stackOffset+4)
                currentFunc.addStoreMem(numElementsReg, ret, sizeField)
                if (initializer==null)
                    TODO()
                else
                    initializeArray(ret, initializer, elementSize, numElementsReg)
                ret
            } else {
                val numElementsReg = size.codeGenRvalue()
                val elementSize = (type as TypeArray).elementType.sizeInBytes()
                val elementSizeReg = currentFunc.addMov(elementSize)
                currentFunc.addMov(allMachineRegs[1], numElementsReg)
                currentFunc.addMov(allMachineRegs[2], elementSizeReg)
                if (initializer==null) {
                    currentFunc.addCall(Stdlib.callocArray)
                } else {
                    val ret = currentFunc.addCall(Stdlib.mallocArray)
                    initializeArray(ret, initializer, elementSize, numElementsReg)
                    ret
                }
            }
        }

        is TstLambda ->
            body.codeGenRvalue()
    }
}

private fun initializeArray(
    arrayAddress: Reg,
    initializer: TstLambda,
    elementSize: Int,
    numElementsReg: Reg
) {
    val indexReg = currentFunc.newVar()   // Needs to be a var not a temp, so that we can mutate it
    val pointer = currentFunc.newVar()
    val itVar = currentFunc.getVar(initializer.params[0])

    currentFunc.addMov(indexReg, regZero)
    currentFunc.addMov(pointer, arrayAddress)
    val labelStart = currentFunc.newLabel()
    val labelCond = currentFunc.newLabel()
    currentFunc.addJump(labelCond)
    currentFunc.addLabel(labelStart)
    currentFunc.addMov(itVar, indexReg)
    val valueReg = initializer.codeGenRvalue()
    currentFunc.addStoreMem(elementSize, valueReg, pointer, 0)
    currentFunc.addMov(indexReg, currentFunc.addAlu(AluOp.ADD_I, indexReg, 1))
    currentFunc.addMov(pointer, currentFunc.addAlu(AluOp.ADD_I, pointer, elementSize))
    currentFunc.addLabel(labelCond)
    currentFunc.addBranch(AluOp.LT_I, indexReg, numElementsReg, labelStart)
}

// ================================================================
//                         Lvalues
// ================================================================

fun TstExpr.codeGenLvalue(value:Reg)  {
    return when (this) {
        is TstVariable -> currentFunc.addMov( currentFunc.getVar(symbol), value)

        is TstIndex -> {
            val exprReg = expr.codeGenRvalue()
            val indexReg = index.codeGenRvalue()
            val lengthReg = currentFunc.addLoadMem(exprReg, sizeField)
            val size = type.sizeInBytes()
            val indexScaled = currentFunc.addIndexOp(size, indexReg, lengthReg)
            val indexAdded = currentFunc.addAlu(AluOp.ADD_I, exprReg, indexScaled)
            currentFunc.addStoreMem(size, value, indexAdded, 0)
        }

        is TstMember -> TODO()
        else -> error("Malformed TST: Has assignment to $this")
    }
}

// ================================================================
//                         genCodeBranch
// ================================================================

fun TstExpr.codeGenBranch(trueLabel:Label, falseLabel : Label) {
    return when (this) {
        is TstBinop if (op.isIntCompare())-> {
            val lhsReg = lhs.codeGenRvalue()
            val rhsReg = rhs.codeGenRvalue()
            currentFunc.addBranch(op, lhsReg, rhsReg, trueLabel)
            currentFunc.addJump(falseLabel)
        }

        is TstAnd -> {
            val midLabel = currentFunc.newLabel()
            lhs.codeGenBranch(midLabel, falseLabel)
            currentFunc.addLabel(midLabel)
            rhs.codeGenBranch(trueLabel, falseLabel)
        }

        is TstOr -> {
            val midLabel = currentFunc.newLabel()
            lhs.codeGenBranch(trueLabel, midLabel)
            currentFunc.addLabel(midLabel)
            rhs.codeGenBranch(trueLabel, falseLabel)
        }

        is TstNot ->
            expr.codeGenBranch(falseLabel, trueLabel)

        else -> {
            val result = codeGenRvalue()
            currentFunc.addBranch(AluOp.NEQ_I, result, regZero, trueLabel)
            currentFunc.addJump(falseLabel)
        }
    }
}


// ================================================================
//                         Statements
// ================================================================

fun TstStmt.codeGen()  {
    return when (this) {

        is TstFunction -> {
            currentFunc = function
            // Load the parameter symbols from the ABI registers
            currentFunc.addInstr(InstrStart())
            for ((index, param) in function.parameters.withIndex())
                currentFunc.addMov(currentFunc.getVar(param), allMachineRegs[index + 1])

            // Generate the code for the function body
            body.codegen()

            // and return
            currentFunc.addLabel(currentFunc.retLabel)
            currentFunc.addInstr(InstrRet())
        }

        is TstDecl -> {
            if (expr!=null)
                currentFunc.addMov(currentFunc.getVar(symbol), expr.codeGenRvalue())
            else {
                val dummy = currentFunc.getVar(symbol)  // Just declare the variable without initializing it
            }
        }

        is TstAssign -> {
            val rhsVar = rhs.codeGenRvalue()
            lhs.codeGenLvalue(rhsVar)
        }

        is TstClass -> TODO()

        is TstFile -> {
            body.codegen()
        }

        is TstFor -> {
            if (this.expr is TstRange) {
                // For-loop with directly specified range

                // Evaluate the start and end values
                val regIterator = currentFunc.getVar(sym)
                currentFunc.addMov( regIterator, expr.start.codeGenRvalue() )
                val regEnd = currentFunc.addMov( expr.end.codeGenRvalue() )

                // Generate the loop body
                val labelStart = currentFunc.newLabel()
                val labelEnd = currentFunc.newLabel()
                val labelCond = currentFunc.newLabel()
                currentFunc.addJump(labelCond)
                currentFunc.addLabel(labelStart)
                body.codegen()

                // Increment the iterator and check if we are done
                val nextOp = when(expr.op) {
                    AluOp.LT_I -> AluOp.ADD_I
                    AluOp.LTE_I -> AluOp.ADD_I
                    AluOp.GT_I -> AluOp.SUB_I
                    AluOp.GTE_I -> AluOp.SUB_I
                    else -> error("Invalid operator for range")
                }
                val nextVal = currentFunc.addAlu(nextOp, regIterator, 1)
                currentFunc.addMov(regIterator, nextVal)
                currentFunc.addLabel(labelCond)
                currentFunc.addBranch(expr.op, regIterator, regEnd, labelStart)
                currentFunc.addJump(labelEnd)
                currentFunc.addLabel(labelEnd)

            } else if (expr.type is TypeArray || expr.type is TypeString) {
                // calculate the range we need to iterate over
                val elementSize = if (expr.type is TypeArray) expr.type.elementType.sizeInBytes() else 1
                val regArray = expr.codeGenRvalue()
                val regNumElements = currentFunc.addLoadMem(regArray, sizeField)
                val scaledNumElements = currentFunc.addAlu(AluOp.MUL_I, regNumElements, elementSize)
                val endPointer = currentFunc.addAlu(AluOp.ADD_I, regArray, scaledNumElements)
                val regIterator = currentFunc.newVar()      // Needs to be a var not a temp, so that we can mutate it
                currentFunc.addMov(regIterator, regArray)
                val regSym = currentFunc.getVar(sym)

                // Generate the loop body
                val labelStart = currentFunc.newLabel()
                val labelEnd = currentFunc.newLabel()
                val labelCond = currentFunc.newLabel()
                currentFunc.addJump(labelCond)

                // Fetch the element at the current iterator position, and generate the code for the body
                currentFunc.addLabel(labelStart)
                currentFunc.addMov(regSym, currentFunc.addLoadMem(elementSize, regIterator, 0))
                body.codegen()

                // Increment the iterator and check if we are done
                currentFunc.addMov(regIterator, currentFunc.addAlu(AluOp.ADD_I, regIterator, elementSize))
                currentFunc.addLabel(labelCond)
                currentFunc.addBranch(AluOp.LT_I, regIterator, endPointer, labelStart)
                currentFunc.addJump(labelEnd)
                currentFunc.addLabel(labelEnd)
            } else {
                TODO("For loop with range expression")
            }
        }

        is TstIf -> {
            val clauses = body.map{it as TstIfClause}
            val endLabel = currentFunc.newLabel()
            val clauseLabels = mutableListOf<Label>()
            // Generate the code for each clause condition
            for (clause in clauses) {
                val clauseLabel = currentFunc.newLabel()
                clauseLabels.add(clauseLabel)
                if (clause.cond==null)
                    currentFunc.addJump(clauseLabel)
                else {
                    val nextClauseLabel = currentFunc.newLabel()
                    clause.cond.codeGenBranch(clauseLabel, nextClauseLabel)
                    currentFunc.addLabel(nextClauseLabel)
                }
            }
            // If no else case then jump to endLabel
            if (clauses.none{it.cond==null})
                currentFunc.addJump(endLabel)

            // Generate the code for each clause body
            for ((index, clause) in clauses.withIndex()) {
                currentFunc.addLabel(clauseLabels[index])
                clause.body.codegen()
                currentFunc.addJump(endLabel)
            }
            currentFunc.addLabel(endLabel)
        }

        is TstIfClause -> error("Malformed TST: Has an if clause outside an if")

        is TstRepeat -> {
            val labelStart = currentFunc.newLabel()
            val labelEnd = currentFunc.newLabel()
            val labelCond = currentFunc.newLabel()
            currentFunc.addLabel(labelStart)
            body.codegen()
            currentFunc.addLabel(labelCond)
            cond.codeGenBranch(labelEnd, labelStart)
            currentFunc.addLabel(labelEnd)
        }

        is TstWhile -> {
            val labelStart = currentFunc.newLabel()
            val labelEnd = currentFunc.newLabel()
            val labelCond = currentFunc.newLabel()
            currentFunc.addJump(labelCond)
            currentFunc.addLabel(labelStart)
            body.codegen()
            currentFunc.addLabel(labelCond)
            cond.codeGenBranch(labelStart,labelEnd)
            currentFunc.addLabel(labelEnd)
        }

        is TstTop ->
            body.codegen()

        is TstExprStmt -> {
            val dummy = expr.codeGenRvalue()
        }

        is TstNullStmt -> {
            // Do nothing
        }

        is TstPrint -> {
            for(arg in exprs) {
                val argReg = arg.codeGenRvalue()
                currentFunc.addMov(allMachineRegs[1], argReg)
                when(arg.type) {
                    is TypeInt -> currentFunc.addCall(Stdlib.printInt)
                    is TypeChar -> currentFunc.addCall(Stdlib.printChar)
                    is TypeString -> currentFunc.addCall(Stdlib.printString)
                    else -> error("Unsupported type ${arg.type}")
                }
            }
        }

    }
}

fun List<TstStmt>.codegen() {
    for(stmt in this)
        stmt.codeGen()
}
