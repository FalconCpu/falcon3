
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

        is TstAnd -> TODO()
        is TstBreak -> TODO()
        is TstContinue -> TODO()
        is TstError -> TODO()
        is TstFunctionName -> TODO()
        is TstGlobalVar -> TODO()
        is TstIfExpr -> TODO()
        is TstIndex -> TODO()
        is TstMember -> TODO()
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
    }
}

// ================================================================
//                         Lvalues
// ================================================================

fun TstExpr.codeGenLvalue(value:Reg)  {
    return when (this) {
        is TstVariable -> currentFunc.addMov( currentFunc.getVar(symbol), value)
        is TstIndex -> TODO()
        is TstMember -> TODO()
        else -> error("Malformed TST: Has assignment to $this")
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

        is TstFor -> TODO()
        is TstIf -> TODO()
        is TstIfClause -> TODO()
        is TstRepeat -> TODO()
        is TstWhile -> TODO()

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
