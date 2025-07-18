private lateinit var currentFunc : Function

private var breakLabel : Label? = null
private var continueLabel : Label? = null
private var inUnsafeBlock = false

// ================================================================
//                         Rvalues
// ================================================================

fun TstExpr.codeGenRvalue() : Reg {
    return when (this) {
        is TstVariable -> currentFunc.getVar(symbol)
        is TstIntLit -> currentFunc.addMov(value)
        is TstInlineVariable -> currentFunc.addAlu(AluOp.ADD_I, regSP, symbol.offset)
        is TstBinop -> {
            val lhsReg = lhs.codeGenRvalue()
            val rhsReg = rhs.codeGenRvalue()
            when (op) {
                AluOp.ADD_I,
                AluOp.SUB_I,
                AluOp.MUL_I,
                AluOp.DIV_I,
                AluOp.MOD_I,
                AluOp.AND_I,
                AluOp.OR_I,
                AluOp.XOR_I,
                AluOp.SHL_I,
                AluOp.SHR_I,
                AluOp.EQ_I,
                AluOp.NEQ_I,
                AluOp.LT_I,
                AluOp.GT_I,
                AluOp.LTE_I,
                AluOp.GTE_I -> currentFunc.addAlu(op, lhsReg, rhsReg)

                AluOp.ADD_R,
                AluOp.SUB_R,
                AluOp.MUL_R,
                AluOp.DIV_R,
                AluOp.MOD_R,
                AluOp.EQ_R,
                AluOp.NEQ_R,
                AluOp.LT_R,
                AluOp.GT_R,
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

        is TstIsExpr -> {
            TODO()
        }

        is TstExtractUnion -> {
            val union = expr.codeGenRvalue() as RegUnion
            union.value
        }

        is TstMakeUnion -> {
            val reg = expr.codeGenRvalue()
            val typeReg = if (expr.type==errorEnum) currentFunc.addMov(1) else currentFunc.addMov(0)
            RegUnion("£UNION",typeReg,reg)
        }

        is TstCall -> {
            val argReg = evaluateArgs(args, func.isVararg, func.parameters.map { it.type })
            val thisReg: Reg?
            val thisType: Type?
            if (thisArg != null) {
                thisReg = thisArg.codeGenRvalue()
                thisType = thisArg.type
            } else if (currentFunc.thisSymbol != null) {
                thisReg = currentFunc.getVar(currentFunc.thisSymbol!!)
                thisType = currentFunc.thisSymbol!!.type
            } else {
                thisReg = null
                thisType = null
            }
            codeGenCall(thisReg, argReg, func, thisType)
            // TODO - fix variadic functions
            // TODO - FIx for method calls a.x()
            // TODO("Indirect function call")
        }

        is TstIndex -> {
            val exprReg = expr.codeGenRvalue()
            val indexReg = index.codeGenRvalue()
            val lengthReg = getArrayLength(exprReg, expr.type)
            val size = type.sizeInBytes()
            val indexScaled = if (inUnsafeBlock)
                // In unsafe code, we skip the bounds check
                currentFunc.addAlu(AluOp.MUL_I, indexReg, size)
            else
                currentFunc.addIndexOp(size, indexReg, lengthReg)
            val indexAdded = currentFunc.addAlu(AluOp.ADD_I, exprReg, indexScaled)
            currentFunc.addLoadMem(size, indexAdded, 0)
        }

        is TstAnd -> TODO()

        is TstBreak -> {
            assert(breakLabel != null) { "Internal error - got break outside a loop" }
            currentFunc.addJump(breakLabel!!)
            regZero  // dummy return value
        }

        is TstContinue -> {
            assert(continueLabel != null) { "Internal error - got continue outside a loop" }
            currentFunc.addJump(continueLabel!!)
            regZero   // dummy return value
        }

        is TstError -> TODO()
        is TstFunctionName -> {
            currentFunc.addLea(ValueFunctionName(symbol))
        }

        is TstGlobalVar -> {
            currentFunc.addLoadGlobal(symbol)
        }

        is TstIfExpr -> {
            val ret = currentFunc.newVar()
            val trueLabel = currentFunc.newLabel()
            val falseLabel = currentFunc.newLabel()
            val endLabel = currentFunc.newLabel()
            val condReg = cond.codeGenBranch(trueLabel, falseLabel)
            currentFunc.addLabel(trueLabel)
            val trueReg = thenExpr.codeGenRvalue()
            currentFunc.addMov(ret, trueReg)
            currentFunc.addJump(endLabel)
            currentFunc.addLabel(falseLabel)
            val falseReg = elseExpr.codeGenRvalue()
            currentFunc.addMov(ret, falseReg)
            currentFunc.addLabel(endLabel)
            ret
        }

        is TstMember -> {
            val exprReg = expr.codeGenRvalue()
            currentFunc.addLoadMem(exprReg, field)
        }

        is TstEmbeddedMember -> {
            val exprReg = expr.codeGenRvalue()
            currentFunc.addAlu(AluOp.ADD_I, exprReg, field.offset)
        }

        is TstMinus -> TODO()
        is TstNot -> TODO()
        is TstOr -> TODO()
        is TstRange -> TODO()
        is TstReallit -> TODO()

        is TstReturn -> {
            if (expr != null)
                if (expr.type is TypeErrable)
                    currentFunc.addMov(unionResult, expr.codeGenRvalue())
                else
                    currentFunc.addMov(regResult, expr.codeGenRvalue())
            currentFunc.addJump(currentFunc.retLabel)
            regZero   // return value is not used, but we need a value.
        }

        is TstStringlit -> currentFunc.addLea(ValueString.create(value, TypeString))

        is TstNewArray -> {
            when (type) {
                is TypeInlineArray -> error("New Inline array as rvalue")

                is TypeArray -> {
                    val numElementsReg = size.codeGenRvalue()
                    val elementSize = type.elementType.sizeInBytes()
                    val elementSizeReg = currentFunc.addMov(elementSize)
                    val clearMemReg = currentFunc.addMov(if (initializer == null) 1 else 0)
                    val args = listOf(numElementsReg, elementSizeReg, clearMemReg)
                    val ret = currentFunc.addCall(Stdlib.mallocArray, args)
                    if (initializer != null)
                        initializeArray(ret, initializer, elementSize, numElementsReg)
                    ret
                }

            else -> error("Internal error - unknown array type: $type")
        }
    }


        is TstNewArrayInitializer -> {
            val elementSize = (type as TypeArray).elementType.sizeInBytes()
            val ret = if (kind==TokenKind.INLINE) {
                val reqSize = initializer.size * elementSize + 4
                val offset = currentFunc.stackAlloc(reqSize)
                val numElementsReg = currentFunc.addMov(initializer.size)
                currentFunc.addStoreMem(4, numElementsReg, allMachineRegs[31], offset)
                currentFunc.addAlu(AluOp.ADD_I, allMachineRegs[31], offset+4)
            } else {
                val numElementsReg = currentFunc.addMov(initializer.size)
                val elementSizeReg = currentFunc.addMov(elementSize)
                currentFunc.addCall(Stdlib.mallocArray, listOf(numElementsReg,elementSizeReg))
            }
            for((index,expr) in initializer.withIndex()) {
                val v = expr.codeGenRvalue()
                currentFunc.addStoreMem(elementSize, v, ret, index*elementSize)
            }
            ret
        }

        is TstLambda ->
            body.codeGenRvalue()

        is TstNewObject -> {
            if (kind==TokenKind.INLINE) {
                TODO("Stack allocated objects not yet implemented")
            } else {
                val classType = type as TypeClassInstance
                val classDescriptor = currentFunc.addLea( ValueClassDescriptor(classType.genericClass))
                currentFunc.addMov(allMachineRegs[1], classDescriptor)
                val ret = currentFunc.addCall(Stdlib.mallocObject)

                val argRegs = evaluateArgs(args, classType.constructor.isVararg, classType.constructor.parameters.map{it.type})
                codeGenCall(ret, argRegs, classType.constructor, classType )
                ret
            }
        }

        is TstMethod -> TODO("Method calls without object at $location")

        is TstCast -> {
            val ret = expr.codeGenRvalue()
            currentFunc.addMov(ret)
        }

        is TstAbort -> {
            val argReg = expr.codeGenRvalue()
            currentFunc.addMov(allMachineRegs[1], argReg)
            currentFunc.addSystemCall(Stdlib.SYS_ABORT)
            regZero
        }

        is TstTypeName -> error("Type names should not appear as rvalues")

        is TstIndirectCall -> TODO()
        is TstSetCall -> error("Set calls should not appear as rvalues")

        is TstBitwiseNot -> {
            val exprReg = expr.codeGenRvalue()
            currentFunc.addAlu(AluOp.XOR_I, exprReg, -1)
        }

        is TstGetEnumData -> {
            val index = expr.codeGenRvalue()
            val enum = expr.type as TypeEnum
            val array = currentFunc.addLea(enum.enumData[field.name]!!)
            val shifted = currentFunc.addAlu(AluOp.SHL_I, index, 2)
            val add = currentFunc.addAlu(AluOp.ADD_I, array, shifted)
            currentFunc.addLoadMem(4, add, 0)
        }

        is TstTry -> {
            val reg = expr.codeGenRvalue() as RegUnion
            val label = currentFunc.newLabel()
            currentFunc.addBranch(AluOp.EQ_I, reg.typeIndex, regZero, label)  // If no error, continue
            currentFunc.addMov(unionResult, reg)
            currentFunc.addJump(currentFunc.retLabel)
            currentFunc.addLabel(label)
            reg.value
        }
    }
}

private fun evaluateArgs(args:List<TstExpr>, isVarargs:Boolean, types:List<Type>) : List<Reg>{
    if (isVarargs) {
        val numRegularArgs = types.size-1
        val numVarargs = args.size - numRegularArgs

        // Allocate some space on the stack, evaluate the args and store in the space
        val stackSpace = currentFunc.stackAlloc(4*numVarargs+4)
        val numElements = currentFunc.addMov(numVarargs)
        currentFunc.addStoreMem(4, numElements, allMachineRegs[31], stackSpace)
        for(index in 0..< numVarargs) {
            val arg = args[numRegularArgs+index]
            val reg = arg.codeGenRvalue()
            if (arg.type.sizeInBytes()>4)
                Log.error(arg.location,"Varargs must be 4 bytes or less")
            currentFunc.addStoreMem(4, reg, allMachineRegs[31], stackSpace+4+4*index)
        }

        val regularArgRegs = args.subList(0,numRegularArgs).map{it.codeGenRvalue()}
        val varargReg = currentFunc.addAlu(AluOp.ADD_I, allMachineRegs[31], stackSpace+4)
        return regularArgRegs + varargReg
    } else
        // Not a vararg - so just create all the symbols
        return args.map { it.codeGenRvalue() }
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

    // Extract the return value
    return if (func.returnType == TypeUnit || func.returnType == TypeNothing)
        regZero
    else if (func.returnType is TypeErrable)
        currentFunc.addMov(unionResult)
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

private fun getArrayLength(exprReg:Reg, type:Type) : Reg{
    return when(type) {
        is TypeArray -> currentFunc.addLoadMem(exprReg, sizeField)
        is TypeString -> currentFunc.addLoadMem(exprReg, sizeField)
        is TypeInlineArray -> currentFunc.addMov(type.numElements)
        else -> error("Internal error: Unsupported type for indexing")
    }
}

private fun initializeArray(
    arrayAddress: Reg,
    initializer: TstLambda,
    elementSize: Int,
    numElements: Int
) {
    val numElementsReg = currentFunc.addMov(numElements)
    initializeArray(arrayAddress, initializer, elementSize, numElementsReg)
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
                currentFunc.addMov(currentFunc.getVar(symbol), value)
            else {
                val v1 = currentFunc.addAlu(op,currentFunc.getVar(symbol), value )
                currentFunc.addMov( currentFunc.getVar(symbol), v1)
            }
        }

        is TstIndex -> {
            val exprReg = expr.codeGenRvalue()
            val indexReg = index.codeGenRvalue()
            val lengthReg =  getArrayLength(exprReg, expr.type)
            val size = type.sizeInBytes()
            val indexScaled = if (inUnsafeBlock)
                currentFunc.addAlu(AluOp.MUL_I, indexReg, size)
            else
                currentFunc.addIndexOp(size, indexReg, lengthReg)
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

        is TstEmbeddedMember -> {
            TODO("Assignment to embedded member")
        }

        is TstGlobalVar -> {
            if (op==AluOp.EQ_I)
                currentFunc.addStoreGlobal(value, symbol)
            else {
                val v1 = currentFunc.addLoadGlobal(symbol)
                val v2 = currentFunc.addAlu(op,v1, value )
                currentFunc.addStoreGlobal(v2, symbol)
            }

        }

        is TstSetCall -> {
            // An expression of the form x.set(y)=z is translated into a call to the function x.set(y,z)
            val argSym = listOf(this.thisArg.codeGenRvalue()) + this.args.map { it.codeGenRvalue() }  + listOf(value)
            val dummy = currentFunc.addCall(this.func, argSym)
        }

        else -> error("Malformed TST: Has assignment to $this")
    }
}

// ================================================================
//                  Code Gen Lvalue Inline
// ================================================================
// For inline assignements - calcluate the address of the destination
// and evaluate the RHS into it.

fun TstExpr.codeGenLvalueInline() : Reg  {
    when(this) {
        is TstInlineVariable -> {
            return currentFunc.addAlu(AluOp.ADD_I, regSP, symbol.offset)
        }

        is TstEmbeddedMember -> {
            val base = expr.codeGenRvalue()
            return currentFunc.addAlu(AluOp.ADD_I, base, field.offset)
        }

        else -> TODO("Inline Assignment to $this")
    }
}

fun TstExpr.codeGenRvalueInline(dest:Reg) {
    when(this) {
        is TstNewArray -> {
            require(type is TypeInlineArray)
            val numElementsReg = currentFunc.addMov(type.numElements)
            val elementSize = type.elementType.sizeInBytes()
            if (initializer != null)
                initializeArray(dest, initializer, elementSize, numElementsReg)
            else
                clearMem(dest, elementSize * type.numElements)
        }

        else -> TODO("Inline Rvalue to $this")
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

        is TstIsExpr -> {
            if (expr.type is TypeErrable) {
                val union = expr.codeGenRvalue() as RegUnion
                if (isType==errorEnum) {
                    currentFunc.addBranch(AluOp.EQ_I, union.typeIndex, regZero, falseLabel)
                    currentFunc.addJump(trueLabel)
                } else {
                    currentFunc.addBranch(AluOp.EQ_I, union.typeIndex, regZero, trueLabel)
                    currentFunc.addJump(falseLabel)
                }
            } else
                TODO()
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
            if (currentFunc.thisSymbol != null)
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
            if (expr != null) {
                if (symbol is SymbolInlineVar) {
                    symbol.offset = currentFunc.stackAlloc(symbol.type.sizeInBytes())
                    val lhsReg = currentFunc.addAlu(AluOp.ADD_I, regSP, symbol.offset)
                    expr.codeGenRvalueInline(lhsReg)
                } else
                    currentFunc.addMov(currentFunc.getVar(symbol), expr.codeGenRvalue())
            } else {
                val dummy = currentFunc.getVar(symbol)  // Just declare the variable without initializing it
            }
        }

        is TstAssign -> {
            if (rhs.type.isInline()) {
                val lhsVar = lhs.codeGenLvalueInline()
                rhs.codeGenRvalueInline(lhsVar)
            } else {
                val rhsVar = rhs.codeGenRvalue()
                lhs.codeGenLvalue(rhsVar, op)
            }
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
            for (method in methods)
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
                currentFunc.addMov(regIterator, expr.start.codeGenRvalue())
                val regEnd = currentFunc.addMov(expr.end.codeGenRvalue())

                // Generate the loop body
                val labelStart = currentFunc.newLabel()
                val labelEnd = currentFunc.newLabel()
                val labelContinue = currentFunc.newLabel()
                val labelCond = currentFunc.newLabel()
                val oldBreakLabel = breakLabel
                val oldContinueLabel = continueLabel
                breakLabel = labelEnd
                continueLabel = labelContinue
                currentFunc.addJump(labelCond)
                currentFunc.addLabel(labelStart)
                body.codegen()

                // Increment the iterator and check if we are done
                currentFunc.addLabel(labelContinue)
                val nextOp = when (expr.op) {
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
                breakLabel = oldBreakLabel
                continueLabel = oldContinueLabel

            } else if (expr.type is TypeArray || expr.type is TypeString || expr.type is TypeInlineArray) {
                // calculate the range we need to iterate over
                val elementSize = when (expr.type) {
                    is TypeArray -> expr.type.elementType.sizeInBytes()
                    is TypeString -> 1
                    is TypeInlineArray -> expr.type.elementType.sizeInBytes()
                    else -> error("Invalid type for array")
                }
                val regArray = expr.codeGenRvalue()
                val regNumElements = getArrayLength(regArray, expr.type)
                val scaledNumElements = currentFunc.addAlu(AluOp.MUL_I, regNumElements, elementSize)
                val endPointer = currentFunc.addAlu(AluOp.ADD_I, regArray, scaledNumElements)
                val regIterator = currentFunc.newVar()      // Needs to be a var not a temp, so that we can mutate it
                currentFunc.addMov(regIterator, regArray)
                val regSym = currentFunc.getVar(sym)

                // Generate the loop body
                val labelStart = currentFunc.newLabel()
                val labelEnd = currentFunc.newLabel()
                val labelCond = currentFunc.newLabel()
                val labelContinue = currentFunc.newLabel()
                val oldBreakLabel = breakLabel
                val oldContinueLabel = continueLabel
                breakLabel = labelEnd
                continueLabel = labelContinue
                currentFunc.addJump(labelCond)

                // Fetch the element at the current iterator position, and generate the code for the body
                currentFunc.addLabel(labelStart)
                currentFunc.addMov(regSym, currentFunc.addLoadMem(elementSize, regIterator, 0))
                body.codegen()

                // Increment the iterator and check if we are done
                currentFunc.addLabel(labelContinue)
                currentFunc.addMov(regIterator, currentFunc.addAlu(AluOp.ADD_I, regIterator, elementSize))
                currentFunc.addLabel(labelCond)
                currentFunc.addBranch(AluOp.LT_I, regIterator, endPointer, labelStart)
                currentFunc.addJump(labelEnd)
                currentFunc.addLabel(labelEnd)
                breakLabel = oldBreakLabel
                continueLabel = oldContinueLabel

            } else if (expr.type is TypeClassInstance) {
                // get the size and get symbols. The presence and types of these symbols has already been verified.
                val sz = expr.type.lookupSymbol(location, "size") as SymbolField
                val gt = expr.type.lookupSymbol(location, "get") as SymbolFunction
                val instReg = expr.codeGenRvalue()
                val sizeReg = currentFunc.addLoadMem(instReg, sz)
                val regIterator = currentFunc.newVar()      // Needs to be a var not a temp, so that we can mutate it
                currentFunc.addMov(regIterator, regZero)
                val regSym = currentFunc.getVar(sym)

                // Generate the loop body
                val labelStart = currentFunc.newLabel()
                val labelEnd = currentFunc.newLabel()
                val labelCond = currentFunc.newLabel()
                val labelContinue = currentFunc.newLabel()
                val oldBreakLabel = breakLabel
                val oldContinueLabel = continueLabel
                breakLabel = labelEnd
                continueLabel = labelContinue
                currentFunc.addJump(labelCond)

                // Fetch the element at the current iterator position, and generate the code for the body
                currentFunc.addLabel(labelStart)
                val vx = currentFunc.addCall(gt.functions[0], listOf(instReg, regIterator))     // Call the get function
                currentFunc.addMov(regSym, vx)
                body.codegen()

                // Increment the iterator and check if we are done
                currentFunc.addLabel(labelContinue)
                currentFunc.addMov(regIterator, currentFunc.addAlu(AluOp.ADD_I, regIterator, 1))
                currentFunc.addLabel(labelCond)
                currentFunc.addBranch(AluOp.LT_I, regIterator, sizeReg, labelStart)
                currentFunc.addJump(labelEnd)
                currentFunc.addLabel(labelEnd)
                breakLabel = oldBreakLabel
                continueLabel = oldContinueLabel
            } else {
                TODO("For loop with range expression")
            }
        }

        is TstIf -> {
            val clauses = body.map { it as TstIfClause }
            val endLabel = currentFunc.newLabel()
            val clauseLabels = mutableListOf<Label>()
            // Generate the code for each clause condition
            for (clause in clauses) {
                val clauseLabel = currentFunc.newLabel()
                clauseLabels.add(clauseLabel)
                if (clause.cond == null)
                    currentFunc.addJump(clauseLabel)
                else {
                    val nextClauseLabel = currentFunc.newLabel()
                    clause.cond.codeGenBranch(clauseLabel, nextClauseLabel)
                    currentFunc.addLabel(nextClauseLabel)
                }
            }
            // If no else case then jump to endLabel
            if (clauses.none { it.cond == null })
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
            val oldBreakLabel = breakLabel
            val oldContinueLabel = continueLabel
            breakLabel = labelEnd
            continueLabel = labelCond
            currentFunc.addLabel(labelStart)
            body.codegen()
            currentFunc.addLabel(labelCond)
            cond.codeGenBranch(labelEnd, labelStart)
            currentFunc.addLabel(labelEnd)
            breakLabel = oldBreakLabel
            continueLabel = oldContinueLabel
        }

        is TstWhile -> {
            val labelStart = currentFunc.newLabel()
            val labelEnd = currentFunc.newLabel()
            val labelCond = currentFunc.newLabel()
            val oldBreakLabel = breakLabel
            val oldContinueLabel = continueLabel
            breakLabel = labelEnd
            continueLabel = labelCond
            currentFunc.addJump(labelCond)
            currentFunc.addLabel(labelStart)
            body.codegen()
            currentFunc.addLabel(labelCond)
            cond.codeGenBranch(labelStart, labelEnd)
            currentFunc.addLabel(labelEnd)
            breakLabel = oldBreakLabel
            continueLabel = oldContinueLabel
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
            for (arg in exprs) {
                val argReg = arg.codeGenRvalue()
                currentFunc.addMov(allMachineRegs[1], argReg)
                when (arg.type) {
                    is TypeBool -> currentFunc.addCall(Stdlib.printInt)
                    is TypeInt -> currentFunc.addCall(Stdlib.printInt)
                    is TypeChar -> currentFunc.addCall(Stdlib.printChar)
                    is TypeString -> currentFunc.addCall(Stdlib.printString)
                    is TypeEnum -> currentFunc.addCall(Stdlib.printInt)
                    else -> error("Unsupported type ${arg.type}")
                }
            }
        }

        is TstWhen -> {
            val expReg = expr.codeGenRvalue()
            val clauses = body.filterIsInstance<TstWhenClause>()
            val labelEnd = currentFunc.newLabel()

            // Generate code for the comparisons
            val clauseLabels = mutableListOf<Label>()
            for (clause in clauses) {
                val clauseLabel = currentFunc.newLabel()
                clauseLabels += clauseLabel
                if (clause.exprs.isEmpty())
                    currentFunc.addJump(clauseLabel)
                else for (expr in clause.exprs)
                    branchIfEqual(expReg, expr.codeGenRvalue(), clauseLabel, expr.type)
            }
            currentFunc.addJump(labelEnd)

            // Generate code for the clause bodies
            for (index in clauses.indices) {
                currentFunc.addLabel(clauseLabels[index])
                for (stmt in clauses[index].body)
                    stmt.codeGen()
                currentFunc.addJump(labelEnd)
            }
            currentFunc.addLabel(labelEnd)
        }

        is TstWhenClause -> error("WhenClause outside of when")

        is TstFree -> {
            val argReg = expr.codeGenRvalue()
            val label = currentFunc.newLabel()
            // Do a null check
            currentFunc.addBranch(AluOp.EQ_I, argReg, regZero, label)

            // See if the class has a method called free - if so call it
            val typex = if (expr.type is TypeNullable) expr.type.elementType else expr.type
            if (typex is TypeClassInstance) {
                val destructor = typex.lookupSymbol(location, "free")
                if (destructor is SymbolFunction) {
                    // Call the destructor
                    currentFunc.addCall(destructor.functions[0], listOf(argReg))
                }
            }

            // Free the memory
            currentFunc.addCall(Stdlib.free, listOf(argReg))
            currentFunc.addLabel(label)
        }

        is TstUnsafe -> {
            val oldUnsafe = inUnsafeBlock
            inUnsafeBlock = true
            body.codegen()
            inUnsafeBlock = oldUnsafe
        }
    }
}

private fun branchIfEqual(a:Reg, b:Reg, label:Label, type:Type) {
    when(type) {
        TypeInt,
        TypeBool,
        is TypeEnum,
        TypeChar -> currentFunc.addBranch(AluOp.EQ_I, a, b, label)
        TypeString -> {
            currentFunc.addMov(allMachineRegs[1], a)
            currentFunc.addMov(allMachineRegs[2], b)
            val r = currentFunc.addCall(Stdlib.strequal)
            currentFunc.addBranch(AluOp.NEQ_I, r, regZero, label)
        }
        else -> error("$type not supported in branchIfEqual")
    }
}

fun List<TstStmt>.codegen() {
    for(stmt in this)
        stmt.codeGen()
}
