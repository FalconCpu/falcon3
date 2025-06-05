private lateinit var currentFunc : Function

// ================================================================
//                         Rvalues
// ================================================================

fun TstExpr.codeGenRvalue() : Reg {
    return when (this) {
        is TstVariable -> currentFunc.getVar(symbol)
        is TstIntLit -> currentFunc.addMov(value)
        is TstBinop -> {
            val lhsReg = lhs.codeGenRvalue()
            val rhsReg = rhs.codeGenRvalue()
            when(op) {
                AluOp.ADD_I,
                AluOp.SUB_I,
                AluOp.MUL_I,
                AluOp.DIV_I,
                AluOp.MOD_I,
                AluOp.AND_I,
                AluOp.OR_I ,
                AluOp.XOR_I,
                AluOp.SHL_I,
                AluOp.SHR_I,
                AluOp.EQ_I ,
                AluOp.NEQ_I,
                AluOp.LT_I ,
                AluOp.GT_I ,
                AluOp.LTE_I,
                AluOp.GTE_I -> currentFunc.addAlu(op, lhsReg, rhsReg)
                AluOp.ADD_R,
                AluOp.SUB_R,
                AluOp.MUL_R,
                AluOp.DIV_R,
                AluOp.MOD_R,
                AluOp.EQ_R ,
                AluOp.NEQ_R,
                AluOp.LT_R ,
                AluOp.GT_R ,
                AluOp.LTE_R,
                AluOp.GTE_R -> TODO("Floating point")
                AluOp.EQ_S -> {
                    currentFunc.addMov(allMachineRegs[1], lhsReg)
                    currentFunc.addMov(allMachineRegs[2], rhsReg)
                    currentFunc.addCall(Stdlib.strequal)
                }

                AluOp.NEQ_S, -> {
                    currentFunc.addMov(allMachineRegs[1], lhsReg)
                    currentFunc.addMov(allMachineRegs[2], rhsReg)
                    val v1 = currentFunc.addCall(Stdlib.strequal)
                    currentFunc.addAlu(AluOp.XOR_I, v1, 1)
                }

                AluOp.LT_S -> TODO()
                AluOp.GT_S -> TODO()
                AluOp.LTE_S -> TODO()
                AluOp.GTE_S -> TODO()
            }


        }

        is TstCall -> {
            if (expr is TstFunctionName) {
                val argReg = args.map { it.codeGenRvalue() }
                val thisReg = if (currentFunc.thisSymbol!=null) currentFunc.getVar(currentFunc.thisSymbol!!) else null
                codeGenCall(thisReg, argReg, expr.symbol.function, currentFunc.thisSymbol?.type)
            } else if (expr is TstMethod) {
                val thisReg = expr.thisExpr.codeGenRvalue()
                val argReg = args.map { it.codeGenRvalue() }
                codeGenCall(thisReg, argReg, expr.func.function, expr.thisExpr.type )
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
                val sizeRounded = (numElements * elementSize + 3) and -4  // round up to nearest 4 bytes
                val stackOffset = currentFunc.stackAlloc(sizeRounded+4)   // +4 to allow for size field
                val ret = currentFunc.addAlu(AluOp.ADD_I, allMachineRegs[31], stackOffset+4)
                currentFunc.addStoreMem(numElementsReg, ret, sizeField)
                if (initializer==null)
                    clearMem(ret, sizeRounded)
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

        is TstNewObject -> {
            if (local) {
                TODO("Stack allocated objects not yet implemented")
            } else {
                val classType = type as TypeClass
                val classDescriptor = currentFunc.addLea( ValueClassDescriptor(classType))
                currentFunc.addMov(allMachineRegs[1], classDescriptor)
                val ret = currentFunc.addCall(Stdlib.mallocObject)

                val argSyms = args.map { it.codeGenRvalue() }
                var index = 1
                currentFunc.addMov(allMachineRegs[index++], ret)
                for(arg in argSyms)
                    currentFunc.addMov(allMachineRegs[index++], arg)
                currentFunc.addCall(classType.constructor)
                ret
            }
        }

        is TstMethod -> TODO()

        is TstCast -> {
            val ret = expr.codeGenRvalue()
            currentFunc.addMov(ret)
        }
    }
}

private fun codeGenCall(thisReg:Reg?, args:List<Reg>, func:Function, thisType:Type?) : Reg{
    var index = 1

    if (func.thisSymbol!=null) {
        if (thisReg == null)
            error("Attempting to call a method with 'this' undefined")
        else if (!func.thisSymbol.type.isAssignableFrom(thisType!!))
            error("Internal error: Passing 'this' of type $thisType to method that expects ${func.thisSymbol.type}")
        currentFunc.addMov(allMachineRegs[index++], thisReg)
    }

    for (arg in args)
        currentFunc.addMov(allMachineRegs[index++], arg)
    currentFunc.addInstr(InstrCall(func))
    return if (func.returnType == TypeUnit || func.returnType == TypeNothing)
        regZero
    else
        currentFunc.addMov(regResult)
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

private fun clearMem(address: Reg, size: Int) {
    if (size==0)
        return
    if (size%4 != 0)
        error("Size must be a multiple of 4")
    val pointer = currentFunc.newVar()
    currentFunc.addMov(pointer, address)
    val endAddress = currentFunc.addAlu(AluOp.ADD_I, address, size)
    val label = currentFunc.newLabel()
    currentFunc.addLabel(label)
    currentFunc.addStoreMem(4, regZero, pointer, 0)
    currentFunc.addMov(pointer, currentFunc.addAlu(AluOp.ADD_I, pointer, 4))
    currentFunc.addBranch(AluOp.LT_I, pointer, endAddress, label)
}

// ================================================================
//                         Lvalues
// ================================================================

fun TstExpr.codeGenLvalue(value:Reg, op:AluOp)  {
    return when (this) {
        is TstVariable -> {
            if (op==AluOp.EQ_I)
                currentFunc.addMov( currentFunc.getVar(symbol), value)
            else {
                val v1 = currentFunc.addAlu(op,currentFunc.getVar(symbol), value )
                currentFunc.addMov( currentFunc.getVar(symbol), v1)
            }
        }

        is TstIndex -> {
            val exprReg = expr.codeGenRvalue()
            val indexReg = index.codeGenRvalue()
            val lengthReg = currentFunc.addLoadMem(exprReg, sizeField)
            val size = type.sizeInBytes()
            val indexScaled = currentFunc.addIndexOp(size, indexReg, lengthReg)
            val indexAdded = currentFunc.addAlu(AluOp.ADD_I, exprReg, indexScaled)
            if (op==AluOp.EQ_I)
                currentFunc.addStoreMem(size, value, indexAdded, 0)
            else {
                val v = currentFunc.addLoadMem(size, indexAdded, 0)
                val v2 = currentFunc.addAlu(op, v, value)
                currentFunc.addStoreMem(size, v2, indexAdded, 0)
            }
        }

        is TstMember -> {
            val exprReg = expr.codeGenRvalue()
            if (op==AluOp.EQ_I)
                currentFunc.addStoreMem(value, exprReg, field)
            else {
                val v = currentFunc.addLoadMem(exprReg, field)
                val v2 = currentFunc.addAlu(op, v, value)
                currentFunc.addStoreMem(v2, exprReg, field)
            }
        }

        else -> error("Malformed TST: Has assignment to $this")
    }
}

// ================================================================
//                         genCodeBranch
// ================================================================

fun TstExpr.codeGenBranch(trueLabel:Label, falseLabel : Label) {
    return when (this) {
        is TstBinop -> {
            val lhsReg = lhs.codeGenRvalue()
            val rhsReg = rhs.codeGenRvalue()
            when(op) {
                AluOp.ADD_I,
                AluOp.SUB_I,
                AluOp.MUL_I,
                AluOp.DIV_I,
                AluOp.MOD_I,
                AluOp.AND_I,
                AluOp.OR_I ,
                AluOp.XOR_I,
                AluOp.SHL_I,
                AluOp.SHR_I -> error("Got arithmetic operation when expecting branch")

                AluOp.EQ_I ,
                AluOp.NEQ_I,
                AluOp.LT_I ,
                AluOp.GT_I ,
                AluOp.LTE_I,
                AluOp.GTE_I -> {
                    currentFunc.addBranch(op, lhsReg, rhsReg, trueLabel)
                    currentFunc.addJump(falseLabel)
                }

                AluOp.ADD_R,
                AluOp.SUB_R,
                AluOp.MUL_R,
                AluOp.DIV_R,
                AluOp.MOD_R,
                AluOp.EQ_R ,
                AluOp.NEQ_R,
                AluOp.LT_R ,
                AluOp.GT_R ,
                AluOp.LTE_R,
                AluOp.GTE_R -> TODO("Floating point compare")

                AluOp.EQ_S,
                AluOp.NEQ_S -> {
                    currentFunc.addMov(allMachineRegs[1], lhsReg)
                    currentFunc.addMov(allMachineRegs[2], rhsReg)
                    val v1 = currentFunc.addCall(Stdlib.strequal)
                    val bop = if (op==AluOp.EQ_S) AluOp.NEQ_I else AluOp.EQ_I
                    currentFunc.addBranch(bop, v1, regZero, trueLabel)
                    currentFunc.addJump(falseLabel)
                }

                AluOp.LT_S,
                AluOp.GT_S,
                AluOp.LTE_S,
                AluOp.GTE_S -> {
                    currentFunc.addMov(allMachineRegs[1], lhsReg)
                    currentFunc.addMov(allMachineRegs[2], rhsReg)
                    val v1 = currentFunc.addCall(Stdlib.strcmp)
                    val bop = when(op) {
                        AluOp.LT_S -> AluOp.LT_I
                        AluOp.LTE_S -> AluOp.LTE_I
                        AluOp.GT_S -> AluOp.GT_I
                        AluOp.GTE_S -> AluOp.GTE_I
                        else -> error("Internal error")
                    }
                    currentFunc.addBranch(bop, v1, regZero, trueLabel)
                    currentFunc.addJump(falseLabel)
                }
            }
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
            var index = 1
            if (currentFunc.thisSymbol!=null)
                currentFunc.addMov(currentFunc.getVar(currentFunc.thisSymbol!!), allMachineRegs[index++])
            for (param in function.parameters)
                currentFunc.addMov(currentFunc.getVar(param), allMachineRegs[index++])

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
            lhs.codeGenLvalue(rhsVar, op)
        }

        is TstClass -> {
            // Generate the code for the constructor
            currentFunc = classType.constructor

            // Load the parameter symbols from the ABI registers
            currentFunc.addInstr(InstrStart())
            var index = 1
            currentFunc.addMov(currentFunc.getVar(currentFunc.thisSymbol!!), allMachineRegs[index++])
            for (param in currentFunc.parameters)
                currentFunc.addMov(currentFunc.getVar(param), allMachineRegs[index++])

            // Generate the code for the function body
            body.codegen()

            // and return
            currentFunc.addLabel(currentFunc.retLabel)
            currentFunc.addInstr(InstrRet())


            // And run code gen on any methods
            for(method in methods)
                method.codeGen()
        }

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
