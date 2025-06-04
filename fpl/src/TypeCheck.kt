// Type checking phase of the compiler. This phase takes the AST built in the parser phase and
// converts it to a TypeChecked AST (Tst).

private var currentFunction: Function? = null

// ================================================================
//                            Rvalues
// ================================================================

fun AstExpr.typeCheckRvalue(context:AstBlock) : TstExpr {
    return when (this) {
        is AstIntlit ->
            TstIntLit(location, value, TypeInt)

        is AstCharlit ->
            TstIntLit(location, value.code, TypeChar)

        is AstStringlit ->
            TstStringlit(location, value, TypeString)

        is AstReallit ->
            TstReallit(location, value, TypeReal)

        is AstId -> {
            val symbol = context.lookupOrDefault(location, name)
            when (symbol) {
                is SymbolVar -> TstVariable(location, symbol, symbol.type)
                is SymbolGlobal -> TstGlobalVar(location, symbol, symbol.type)
                is SymbolFunction -> TstFunctionName(location, symbol, symbol.type)
                is SymbolTypeName -> TstError(location, "Cannot use type name '$name' as an expression")
                is SymbolField -> TODO("Member access through this ")
            }
        }

        is AstBinop -> {
            val tcLhs = lhs.typeCheckRvalue(context)
            val tcRhs = rhs.typeCheckRvalue(context)
            if (tcLhs.type == TypeError) return tcLhs
            if (tcRhs.type == TypeError) return tcRhs
            val lhsType = tcLhs.type.defaultPromotions()
            val rhsType = tcRhs.type.defaultPromotions()
            val match = binopTable.find { it.op == op  && it.lhs == lhsType && it.rhs == rhsType }
            if (match == null)
                TstError(location, "Invalid binary operator '$op' for types $lhsType and $rhsType")
            else
                TstBinop(location, match.resultOp, tcLhs, tcRhs, match.resultType)
        }

        is AstBreak ->
            TstBreak(location)

        is AstCall -> {
            val tcArgs = args.map{it.typeCheckRvalue(context)}
            val tcExpr = expr.typeCheckRvalue(context)
            if (tcExpr.type == TypeError) return tcExpr
            if (tcExpr.type !is TypeFunction)
                return TstError(location, "Cannot call expression of type ${tcExpr.type}")
            val paramTypes = tcExpr.type.parameters
            if (paramTypes.size != tcArgs.size)
                return TstError(location, "Invalid number of arguments for function call")
            for (index in tcArgs.indices)
                paramTypes[index].checkCompatibleWith(tcArgs[index])
            TstCall(location, tcExpr, tcArgs, tcExpr.type.returnType)
        }

        is AstAnd -> {
            val tcLhs = lhs.typeCheckRvalue(context)
            val tcRhs = rhs.typeCheckRvalue(context)
            TypeBool.checkCompatibleWith(tcLhs)
            TypeBool.checkCompatibleWith(tcRhs)
            TstAnd(location,tcLhs,tcRhs)
        }

        is AstOr -> {
            val tcLhs = lhs.typeCheckRvalue(context)
            val tcRhs = rhs.typeCheckRvalue(context)
            TypeBool.checkCompatibleWith(tcLhs)
            TypeBool.checkCompatibleWith(tcRhs)
            TstOr(location,tcLhs,tcRhs)
        }

        is AstNot -> {
            val tc = expr.typeCheckRvalue(context)
            TypeBool.checkCompatibleWith(tc)
            TstNot(location,tc)
        }
        is AstContinue ->
            TstContinue(location)

        is AstIndex -> {
            val tcExpr = expr.typeCheckRvalue(context)
            val tcIndex = index.typeCheckRvalue(context)
            if (tcExpr.type == TypeError) return tcExpr
            TypeInt.checkCompatibleWith(tcIndex)

            when (tcExpr.type) {
                is TypeArray -> TstIndex(location, tcExpr, tcIndex, tcExpr.type.elementType)
                is TypeString -> TstIndex(location, tcExpr, tcIndex, TypeChar)
                else -> TstError(location, "Cannot index expression of type ${tcExpr.type}")
            }
        }

        is AstIfExpr -> TODO()

        is AstMember -> {
            val tcExpr = expr.typeCheckRvalue(context)
            when (tcExpr.type) {
                is TypeArray -> {
                    if (name== "size")
                        TstMember(location, tcExpr, sizeField, TypeInt)
                    else
                        TstError(location, "Array does not have a field named '$name'")
                }

                is TypeString -> {
                    if (name == "length")
                        TstMember(location, tcExpr, sizeField, TypeInt)
                    else
                        TstError(location, "String does not have a field named '$name'")
                }

                is TypeClass -> {
                    val field = tcExpr.type.lookupSymbol(name)
                    when (field) {
                        null -> TstError(location,"Class ${tcExpr.type} does not have a field named '$name'")
                        is SymbolField -> TstMember(location, tcExpr, field, field.type)
                        else -> TODO("Method calls")
                    }
                }

                else -> TODO()
            }
        }
        is AstMinus -> TODO()

        is AstRange -> {
            val tcStart = start.typeCheckRvalue(context)
            val tcEnd = end.typeCheckRvalue(context)
            tcStart.type.checkCompatibleWith(tcEnd)
            val tcOp = when(op) {
                TokenKind.LT -> AluOp.LT_I
                TokenKind.LTE -> AluOp.LTE_I
                TokenKind.GT -> AluOp.GT_I
                TokenKind.GTE -> AluOp.GTE_I
                else -> error("Invalid range operator")
            }
            val tcType = when(tcStart.type) {
                is TypeError -> TypeError
                is TypeInt -> TypeRange.create(TypeInt)
                is TypeChar -> TypeRange.create(TypeChar)
                else -> makeTypeError(location, "Cannot create range of type ${tcStart.type}")
            }
            TstRange(location,tcStart,tcEnd, tcOp, tcType)
        }

        is AstReturn -> {
            val tc = expr?.typeCheckRvalue(context)
            val cf = currentFunction
            if (cf == null)
                makeTypeError(location, "Return statement outside of a function")
            else if (cf.returnType != TypeUnit) {
                val returnType = cf.returnType
                if (tc == null)
                    makeTypeError(location, "Return statement with no value in function returning $returnType")
                else if (!returnType.isAssignableFrom(tc.type))
                    makeTypeError(location, "Return value of type ${tc.type} when expecting $returnType")
            } else {
                if (tc != null)
                    makeTypeError(location, "Return statement with value in function returning Unit")
            }
            TstReturn(location, tc)
        }

        is AstNew -> {
            val tcType = type.resolveType(context)
            val tcArgs = args.map{it.typeCheckRvalue(context)}
            when(tcType) {
                is TypeArray -> {
                    if (tcArgs.size != 1)
                        return TstError(location, "new Array requires exactly one argument, not ${tcArgs.size}")
                    val tcSize = tcArgs[0]
                    TypeInt.checkCompatibleWith (tcSize)
                    val tcLambda = lambda?.typeCheckLambda(context, listOf(SymbolVar(location,"it", TypeInt, false)))
                    if (tcLambda!=null)
                        tcType.elementType.checkCompatibleWith(tcLambda.body)
                    if (local && ! tcSize.isCompileTimeConstant())
                        Log.error(location, "Array size must be a compile-time constant")
                    TstNewArray(location, tcSize, tcLambda, local,tcType)
                }

                is TypeClass -> {
                    val constructor = tcType.constructor
                    val parameters = constructor.parameters
                    if (parameters.size != tcArgs.size)
                        return TstError(location, "Invalid number of arguments for constructor")
                    for (index in tcArgs.indices)
                        parameters[index].type.checkCompatibleWith(tcArgs[index])
                    TstNewObject(location, tcArgs, tcType, local)
                }

                else -> TstError(location, "Cannot create instance of type $tcType")
            }
        }
    }
}

// ================================================================
//                            Binop Table
// ================================================================

private class BinopEntry(val op:TokenKind, val lhs:Type, val rhs:Type, val resultOp:AluOp, val resultType:Type)
private val binopTable = listOf(
    BinopEntry(TokenKind.PLUS,    TypeInt, TypeInt, AluOp.ADD_I, TypeInt),
    BinopEntry(TokenKind.MINUS,   TypeInt, TypeInt, AluOp.SUB_I, TypeInt),
    BinopEntry(TokenKind.STAR,    TypeInt, TypeInt, AluOp.MUL_I, TypeInt),
    BinopEntry(TokenKind.SLASH,   TypeInt, TypeInt, AluOp.DIV_I, TypeInt),
    BinopEntry(TokenKind.PERCENT, TypeInt, TypeInt, AluOp.MOD_I, TypeInt),
    BinopEntry(TokenKind.LEFT,    TypeInt, TypeInt, AluOp.SHL_I, TypeInt),
    BinopEntry(TokenKind.RIGHT,   TypeInt, TypeInt, AluOp.SHR_I, TypeInt),
    BinopEntry(TokenKind.EQ,      TypeInt, TypeInt, AluOp.EQ_I,  TypeBool),
    BinopEntry(TokenKind.NEQ,     TypeInt, TypeInt, AluOp.NEQ_I, TypeBool),
    BinopEntry(TokenKind.LT,      TypeInt, TypeInt, AluOp.LT_I,  TypeBool),
    BinopEntry(TokenKind.LTE,     TypeInt, TypeInt, AluOp.LTE_I, TypeBool),
    BinopEntry(TokenKind.GT,      TypeInt, TypeInt, AluOp.GT_I,  TypeBool),
    BinopEntry(TokenKind.GTE,     TypeInt, TypeInt, AluOp.GTE_I, TypeBool),
    BinopEntry(TokenKind.AMP,     TypeInt, TypeInt, AluOp.AND_I, TypeBool),
    BinopEntry(TokenKind.BAR,     TypeInt, TypeInt, AluOp.OR_I,  TypeBool),
    BinopEntry(TokenKind.CARET,   TypeInt, TypeInt, AluOp.XOR_I, TypeBool),

    BinopEntry(TokenKind.PLUS,    TypeReal, TypeReal, AluOp.ADD_R, TypeReal),
    BinopEntry(TokenKind.MINUS,   TypeReal, TypeReal, AluOp.SUB_R, TypeReal),
    BinopEntry(TokenKind.STAR,    TypeReal, TypeReal, AluOp.MUL_R, TypeReal),
    BinopEntry(TokenKind.SLASH,   TypeReal, TypeReal, AluOp.DIV_R, TypeReal),
    BinopEntry(TokenKind.PERCENT, TypeReal, TypeReal, AluOp.MOD_R, TypeReal),
    BinopEntry(TokenKind.EQ,      TypeReal, TypeReal, AluOp.EQ_R,  TypeBool),
    BinopEntry(TokenKind.NEQ,     TypeReal, TypeReal, AluOp.NEQ_R, TypeBool),
    BinopEntry(TokenKind.LT,      TypeReal, TypeReal, AluOp.LT_R,  TypeBool),
    BinopEntry(TokenKind.LTE,     TypeReal, TypeReal, AluOp.LTE_R, TypeBool),
    BinopEntry(TokenKind.GT,      TypeReal, TypeReal, AluOp.GT_R,  TypeBool),
    BinopEntry(TokenKind.GTE,     TypeReal, TypeReal, AluOp.GTE_R, TypeBool),

    BinopEntry(TokenKind.EQ,      TypeString, TypeString, AluOp.EQ_S,  TypeBool),
    BinopEntry(TokenKind.NEQ,     TypeString, TypeString, AluOp.NEQ_S, TypeBool),
    BinopEntry(TokenKind.LT,      TypeString, TypeString, AluOp.LT_S,  TypeBool),
    BinopEntry(TokenKind.LTE,     TypeString, TypeString, AluOp.LTE_S, TypeBool),
    BinopEntry(TokenKind.GT,      TypeString, TypeString, AluOp.GT_S,  TypeBool),
    BinopEntry(TokenKind.GTE,     TypeString, TypeString, AluOp.GTE_S, TypeBool)
)


// ================================================================
//                            Lvalues
// ================================================================

fun AstExpr.typeCheckLvalue(context:AstBlock) : TstExpr = when(this) {
    is AstId -> {
        val symbol = context.lookupOrDefault(location, name)
        when (symbol) {
            is SymbolVar -> TstVariable(location, symbol, symbol.type)
            is SymbolGlobal -> TstGlobalVar(location, symbol, symbol.type)
            is SymbolFunction -> TstError(location, "Cannot use function '$name' as an lvalue")
            is SymbolTypeName -> TstError(location, "Cannot use type name '$name' as an lvalue")
            is SymbolField -> TODO("Fields are not supported yet")
        }
    }

    is AstIndex -> {
        // Since we don't have immutable arrays (yet) we can just use the Rvalue typecheck for an lvalue
        typeCheckRvalue(context)
    }

    is AstMember -> {
        val ret = typeCheckRvalue(context)
        if (ret is TstMember && !ret.field.mutable)
            Log.error(location, "Filed '${ret.field}' is not mutable")
        ret
    }

    else -> {
        TstError(location, "Expression is not an lvalue")
    }
}

// ================================================================
//                         Conditions
// ================================================================

fun AstExpr.typeCheckBool(context:AstBlock) : TstExpr {
    val tc = typeCheckRvalue(context)
    TypeBool.checkCompatibleWith(tc)
    return tc
}

// ================================================================
//                         Lambda
// ================================================================

fun AstLambda.typeCheckLambda(context:AstBlock, params:List<SymbolVar>) : TstLambda {
    // The automatic parent tracing doesn't find the lambda, so we need to do it manually
    setParent(context)

    // Add the parameters to the Lambda's symbol table
    for (param in params)
        addSymbol(param)

    // Now type check the expression in the context of the lambda
    val tcExpr = expr.typeCheckRvalue(this)

    return TstLambda(location, params, tcExpr, tcExpr.type)
}

// ================================================================
//                         Types
// ================================================================

fun AstTypeExpr.resolveType(context:AstBlock) : Type = when(this) {
    is AstTypeId -> {
        val sym = context.lookupSymbol(name)
        when (sym) {
            null -> makeTypeError(location, "Unknown type '$name'")
            is SymbolTypeName -> sym.type
            else -> makeTypeError(location, "'$name' is not a type")
        }
    }

    is AstTypeArray -> {
        val elementType = base.resolveType(context)
        TypeArray.create(elementType)
    }

    is AstTypeRange -> {
        val elementType = base.resolveType(context)
        TypeRange.create(elementType)
    }

    is AstTypeNullable -> {
        val elementType = base.resolveType(context)
        TypeNullable.create(elementType)
    }
}

// ================================================================
//                         Statements
// ================================================================

fun AstStmt.typeCheck(context:AstBlock) : TstStmt = when(this) {
    is AstDecl -> {
        val tcExpr = expr?.typeCheckRvalue(context)
        val type = typeExpr?.resolveType(context) ?: tcExpr?.type ?:
                   makeTypeError(location,"Cannot resolve type for '$name'")
        val mutable = (kind == TokenKind.VAR)
        val symbol = if (context is AstFile)
                         SymbolGlobal(location, name, type, mutable)
                     else
                         SymbolVar(location, name, type, mutable)
        context.addSymbol(symbol)
        TstDecl(location, symbol, tcExpr)
    }

    is AstFunction -> {
        val oldFunction = currentFunction
        currentFunction = this.function
        val tcBody = body.map{it.typeCheck(this)}
        currentFunction = oldFunction
        TstFunction(location, function, tcBody)
    }

    is AstAssign -> {
        val tcLhs = lhs.typeCheckLvalue(context)
        val tcRhs = rhs.typeCheckRvalue(context)
        tcLhs.type.checkCompatibleWith(tcRhs)
        TstAssign(location, tcLhs, tcRhs)
    }

    is AstClass -> {
        // The body of the class has already been type checked in the identifyFields pass - so all we do here is
        // package it up into a TstClass
        TstClass(location, classType, constructorBody)
    }

    is AstFile -> {
        val tcBody = body.map{it.typeCheck(this)}
        TstFile(location, name, tcBody)
    }

    is AstWhile -> {
        val tcCond = cond.typeCheckBool(context)
        val tcBody = body.map{it.typeCheck(context)}
        TstWhile(location, tcCond, tcBody)
    }

    is AstFor -> {
        val tcExpr = expr.typeCheckRvalue(context)
        val elementType = when(tcExpr.type) {
            is TypeError -> TypeError
            is TypeArray -> tcExpr.type.elementType
            is TypeRange -> tcExpr.type.elementType
            is TypeString -> TypeChar
            else -> makeTypeError(location, "Cannot iterate over type '${tcExpr.type}'")
        }
        val symbol = SymbolVar(location, name, elementType, false)
        addSymbol(symbol)
        val tcBody = body.map{it.typeCheck(this)}
        TstFor(location, symbol, tcExpr, tcBody)
    }

    is AstIf -> {
        val tcClauses = body.map{it.typeCheck(context) as TstIfClause}
        TstIf(location, tcClauses)
    }

    is AstIfClause -> {
        val tcCond = cond?.typeCheckBool(context)
        val tcBody = body.map{it.typeCheck(this)}
        TstIfClause(location, tcCond, tcBody)
    }

    is AstRepeat -> {
        val tcBody = body.map{it.typeCheck(this)}
        val tcCond = cond.typeCheckBool(this) // Note that we use 'this' here, not 'context' to allow the condition to refer to variables in the loop
        TstRepeat(location, tcCond, tcBody)
    }

    is AstTop -> error("AstTop should not appear on internals of AST")

    is AstExprStmt -> {
        TstExprStmt(location, expr.typeCheckRvalue(context))
    }

    is AstNullStmt -> TstNullStmt(location)

    is AstPrint -> {
        val tcExprs = exprs.map{it.typeCheckRvalue(context)}
        for (tcExpr in tcExprs)
            if (tcExpr.type != TypeInt && tcExpr.type != TypeString && tcExpr.type!=TypeChar)
                Log.error(tcExpr.location, "Got type ${tcExpr.type} but print only supports Int / Char/ String")
        TstPrint(location, tcExprs)
    }

    is AstLambda -> TODO()
}

private fun AstParameter.typeCheckParameter(context:AstBlock) : SymbolVar {
    val type = this.type.resolveType(context)
    val symbol = SymbolVar(location, name, type, false)
    return symbol
}

private fun AstFunction.createFunctionSymbol(context:AstBlock) {
    // Generate symbols for parameters and add them to the functions symbol table
    val paramSymbols = params.map{it.typeCheckParameter(context)}
    val retType = this.retType?.resolveType(context) ?: TypeUnit
    for (param in paramSymbols)
        addSymbol(param)

    // Create the Function object to represent this function in the back end
    // TODO - add thisSymbol for methods
    function = Function(name, paramSymbols, null, retType)
    allFunctions += function

    // Create a symbol for this function and add it to the current scope
    val funcType = TypeFunction.create(paramSymbols.map{it.type}, retType)
    val symbol = SymbolFunction(location, name, funcType, function)
    context.addSymbol(symbol)
}

private fun AstClass.createClassSymbol(context:AstBlock) {
    classType = TypeClass.create(name, null)
    val symbol = SymbolTypeName(location, name, classType)
    context.addSymbol(symbol)
}

private fun AstClass.createFieldSymbols(context: AstBlock) {
    // build a function for the constructor
    val paramSymbols = params.map { it.typeCheckParameter(context) }
    val thisSym = SymbolVar(location, "this", classType, false)
    val thisExpr = TstVariable(thisSym.location, thisSym, thisSym.type)
    classType.constructor = Function(name, paramSymbols, thisSym, TypeUnit)
    allFunctions += classType.constructor
    val astConstructor = AstFunction(location, name, params, null, emptyList())  // provide a scope for the constructor
    astConstructor.setParent(this)
    astConstructor.addSymbol(thisSym)
    for (sym in paramSymbols)
        astConstructor.addSymbol(sym)

    // Parameters could be marked VAL or VAR - in which case they define a field as well as being constructor parameters
    for ((param, sym) in params.zip(paramSymbols))
        if (param.kind == TokenKind.VAL || param.kind == TokenKind.VAR) {
            val fieldSymbol = SymbolField(param.location, param.name, sym.type, param.kind == TokenKind.VAR)
            classType.addSymbol(fieldSymbol)   // Add the field to the class
            addSymbol(fieldSymbol) // Also add it to the current scope (so methods can refer to it)
            // Create an expression to assign the parameter to the field
            val paramExpr = TstVariable(param.location, sym, sym.type)
            val fieldExpr = TstMember(param.location, thisExpr, fieldSymbol, fieldSymbol.type)
            constructorBody += TstAssign(param.location, fieldExpr, paramExpr)
        }

    // Scan through the class body and identify any more fields
    for (stmt in body.filterIsInstance<AstDecl>()) {
        val tcExpr = stmt.expr?.typeCheckRvalue(astConstructor)
        val type = stmt.typeExpr?.resolveType(context) ?:
                   tcExpr?.type ?:
                   makeTypeError(location,"Cannot determine type for '${stmt.name}'")
        val sym = SymbolField(stmt.location, stmt.name, type, stmt.kind==TokenKind.VAR)
        classType.addSymbol(sym)
        addSymbol(sym)
        if (tcExpr!=null) {
            sym.type.checkCompatibleWith(tcExpr)
            val fieldExpr = TstMember(sym.location, thisExpr, sym, sym.type)
            constructorBody += TstAssign(sym.location, fieldExpr, tcExpr)
        }
    }
}


private fun AstBlock.identifyFunctions() {
    // Recursively scan the AST for functions and create symbols for them
    for (stmt in body.filterIsInstance<AstBlock>())
        if (stmt is AstFunction)
            stmt.createFunctionSymbol(this)
        else
            stmt.identifyFunctions()
}

private fun AstBlock.identifyFields() {
    // Recursively scan the AST for class fields
    for (stmt in body.filterIsInstance<AstBlock>())
        if (stmt is AstClass)
            stmt.createFieldSymbols(this)
        else
            stmt.identifyFields()
}


private fun AstBlock.identifyClasses() {
    // Recursively scan the AST for functions and create symbols for them
    for (stmt in body.filterIsInstance<AstBlock>())
        if (stmt is AstClass)
            stmt.createClassSymbol(this)
        else
            stmt.identifyClasses()
}


fun AstTop.typeCheck() : TstTop {
    for (stmt in body.filterIsInstance<AstBlock>())
        stmt.setParent(this)
    identifyClasses()
    identifyFunctions()
    identifyFields()

    val tcBody = body.map{it.typeCheck(this)}
    return TstTop(location, tcBody)
}

