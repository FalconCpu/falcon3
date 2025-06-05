// Type checking phase of the compiler. This phase takes the AST built in the parser phase and
// converts it to a TypeChecked AST (Tst).

private var currentFunction: Function? = null
private var pathContext = emptyPathContext

// list of PathContexts at break/continue locations in current loop
private var breakContext : MutableList<PathContext>? = null
private var continueContext : MutableList<PathContext>? = null

// ================================================================
//                            getSymbol
// ================================================================

fun TstExpr.isSymbol() : Boolean = when(this) {
    is TstVariable -> true
    else -> false
}

fun TstExpr.getSymbol() : Symbol = when(this) {
    is TstVariable -> symbol
    else -> error("getSymbol called on $this")
}

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
                is SymbolVar -> {
                    if (symbol in pathContext.uninitialized)
                        Log.error(location,"Symbol '$name' is uninitialized")
                    else if (symbol in pathContext.maybeUninitialized)
                        Log.error(location,"Symbol '$name' may be uninitialized")
                    TstVariable(location, symbol, pathContext.getType(symbol))
                }
                is SymbolGlobal -> TstGlobalVar(location, symbol, symbol.type)
                is SymbolFunction -> TstFunctionName(location, symbol, symbol.type)
                is SymbolTypeName -> TstError(location, "Cannot use type name '$name' as an expression")
                is SymbolField -> {
                    val thisSymbol = currentFunction?.thisSymbol
                    // do some sanity checks
                    if (thisSymbol==null)error("Accessed a SymbolField when not in a method")
                    assert(symbol in (thisSymbol.type as TypeClass).symbols.values){
                        "Internal Compiler Error: Field '$name' found, but does not belong to 'this' type '${thisSymbol.type}'."
                    }

                    val thisExpr = TstVariable(location, thisSymbol, thisSymbol.type)
                    TstMember(location, thisExpr, symbol, symbol.type)
                }
                is SymbolConstant -> {
                    when(symbol.value) {
                        is ValueClassDescriptor -> TstError(location, "Cannot use type name '$name' as an expression")
                        is ValueInt -> TstIntLit(location, symbol.value.value, symbol.type)
                        is ValueString -> TstStringlit(location, symbol.value.value, symbol.type)
                    }
                }
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
            else if (tcLhs.isIntegerConstant() && tcRhs.isIntegerConstant())
                TstIntLit(location, match.resultOp.evaluate(tcLhs.getIntegerConstant(), tcRhs.getIntegerConstant()), match.resultType)
            else
                TstBinop(location, match.resultOp, tcLhs, tcRhs, match.resultType)
        }

        is AstEq -> {
            val tcLhs = lhs.typeCheckRvalue(context)
            val tcRhs = rhs.typeCheckRvalue(context)
            if (tcLhs.type == TypeError) return tcLhs
            if (tcRhs.type == TypeError) return tcRhs
            val lhsType = tcLhs.type.defaultPromotions()
            val rhsType = tcRhs.type.defaultPromotions()
            val op = if (notEq) AluOp.NEQ_I else AluOp.EQ_I

            if (lhsType.isAssignableFrom(rhsType) || rhsType.isAssignableFrom(lhsType))
                TstBinop(location, op, tcLhs, tcRhs, TypeBool)
            else
                TstError(location, "Invalid binary operator '${if (notEq) "!=" else "="}' for types $lhsType and $rhsType")
        }

        is AstBreak -> {
            if (breakContext==null)
                Log.error(location,"break not inside a loop")
            else
                breakContext!! += pathContext
            pathContext = pathContext.setUnreachable()
            TstBreak(location)
        }

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

        is AstContinue -> {
            if (continueContext==null)
                Log.error(location,"continue not inside a loop")
            else
                continueContext!! += pathContext

            pathContext = pathContext.setUnreachable()
            TstContinue(location)
        }

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
                        is SymbolFunction -> TstMethod(location, tcExpr, field, field.type)
                        else -> error("Unexpected Symbol type")
                    }
                }

                is TypeNullable -> TstError(location, "Cannot access '$name' as expression may be null")

                else -> TstError(location, "Cannot access field '$name' of expression of type ${tcExpr.type}")
            }
        }

        is AstMinus -> {
            val tcExpr = expr.typeCheckRvalue(context)
            if (tcExpr.type == TypeError)
                return tcExpr
            else if (tcExpr.type==TypeInt)
                if (tcExpr.isIntegerConstant())
                    TstIntLit(location,-tcExpr.getIntegerConstant(), tcExpr.type)
                else
                    TstBinop(location, AluOp.SUB_I, TstIntLit(location, 0, TypeInt), tcExpr, TypeInt)
            else
                TstError(location,"Invalid type for unary minus: ${tcExpr.type}")
        }

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
            pathContext = pathContext.setUnreachable()
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
        is AstCast -> {
            val tcExpr = expr.typeCheckRvalue(context)
            val tcType = typeExpr.resolveType(context)
            TstCast(location, tcExpr, tcType)
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
            is SymbolVar -> if (symbol.mutable || symbol in pathContext.uninitialized)
                TstVariable(location, symbol, symbol.type)
            else if (symbol in pathContext.maybeUninitialized) {
                Log.error(location, "'$name' may already be initialised")
                TstVariable(location, symbol, symbol.type)
            } else
                TstError(location, "Cannot assign to a non-mutable variable '$name'")

            is SymbolGlobal ->
                if (!symbol.mutable)
                    TstError(location, "Cannot assign to a non-mutable global variable '$name'")
                else
                    TstGlobalVar(location, symbol, symbol.type)

            is SymbolFunction -> TstError(location, "Cannot use function '$name' as an lvalue")
            is SymbolTypeName -> TstError(location, "Cannot use type name '$name' as an lvalue")
            is SymbolConstant -> TstError(location, "Cannot use constant '$name' as an lvalue")

            is SymbolField -> {
                    val thisSymbol = currentFunction?.thisSymbol

                    // do some sanity checks
                    if (thisSymbol==null)error("Accessed a SymbolField when not in a method")
                    assert(symbol in (thisSymbol.type as TypeClass).symbols.values){
                        "Internal Compiler Error: Field '$name' found, but does not belong to 'this' type '${thisSymbol.type}'."
                    }

                    val thisExpr = TstVariable(location, thisSymbol, thisSymbol.type)
                    if (!symbol.mutable)
                        TstError(location, "Cannot assign to a non-mutable field '${symbol.name}'")
                    else
                        TstMember(location, thisExpr, symbol, symbol.type)
                }
            }
    }

    is AstIndex -> {
        // Since we don't have immutable arrays (yet) we can just use the Rvalue typecheck for a lvalue
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
// Type check a condition expression. Return the typechecked tree,
// and also two path context objects - one for the true branch,
// and one for the false branch.


private fun inferFromCondition(notEq:Boolean, lhs:TstExpr, rhs:TstExpr) : Pair<PathContext,PathContext> {
    // See if we can update the pathContext based on a conditional expression.
    // result is Pair(pathContext if expression is true,pathContext if expression is false)
    // before this is called we have ascertained that lhs is an expression that represents a symbol
    val sym = lhs.getSymbol()
    val ret =
        if (lhs.type is TypeNullable && rhs.type is TypeNull)
            pathContext.refineType(sym,TypeNull) to pathContext.refineType(sym,lhs.type.elementType)
        else
            pathContext to pathContext

    return if (notEq)  ret.second to ret.first  else  ret
}

fun AstExpr.typeCheckBool(context:AstBlock) : Triple<TstExpr,PathContext,PathContext> {
    when(this) {
        is AstEq -> {
            val tc = typeCheckRvalue(context)
            val paths = if (tc is TstBinop && (tc.op==AluOp.EQ_I || tc.op==AluOp.NEQ_I)) {
                if (tc.lhs.isSymbol())
                    inferFromCondition(tc.op==AluOp.NEQ_I, tc.lhs, tc.rhs)
                else if (tc.rhs.isSymbol())
                    inferFromCondition(tc.op==AluOp.NEQ_I, tc.rhs, tc.lhs)
                else
                    pathContext to pathContext
            } else
                pathContext to pathContext
            return Triple(tc, paths.first, paths.second)
        }

        is AstAnd -> {
            val tcLhs = lhs.typeCheckBool(context)
            pathContext = tcLhs.second   // the true pathContext output from the lhs is the input pathContext of rhs
            val tcRhs = rhs.typeCheckBool(context)
            val tcResult = TstAnd(location, tcLhs.first, tcRhs.first)
            val falsePath = listOf(tcLhs.third, tcRhs.third).merge()
            return Triple(tcResult, tcRhs.second, falsePath)
        }

        is AstOr -> {
            val tcLhs = lhs.typeCheckBool(context)
            pathContext = tcLhs.third   // the false pathContext output from the lhs is the input pathContext of rhs
            val tcRhs = rhs.typeCheckBool(context)
            val tcResult = TstOr(location, tcLhs.first, tcRhs.first)
            val truePath = listOf(tcLhs.second, tcRhs.second).merge()
            return Triple(tcResult, truePath, tcRhs.third)
        }

        is AstNot -> {
            val tc = expr.typeCheckBool(context)
            return Triple(tc.first, tc.third, tc.second)
        }

        else -> {
            val tc = typeCheckRvalue(context)
            TypeBool.checkCompatibleWith(tc)
            return Triple(tc,pathContext,pathContext)
        }
    }
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
        TypeNullable.create(location, elementType)
    }
}

// ================================================================
//                         Statements
// ================================================================

fun AstStmt.typeCheck(context:AstBlock) : TstStmt {
    return when (this) {
        is AstDecl -> {
            val tcExpr = expr?.typeCheckRvalue(context)
            val type = typeExpr?.resolveType(context) ?: tcExpr?.type ?: makeTypeError(
                location,
                "Cannot resolve type for '$name'"
            )
            val mutable = (kind == TokenKind.VAR)
            val symbol = if (context is AstFile)
                SymbolGlobal(location, name, type, mutable)
            else
                SymbolVar(location, name, type, mutable)
            pathContext = if (tcExpr == null)
                pathContext.addUninitialized(symbol)
            else
                pathContext.refineType(symbol, tcExpr.type)
            context.addSymbol(symbol)
            TstDecl(location, symbol, tcExpr)
        }

        is AstConst -> {
            val tcExpr = expr.typeCheckRvalue(context)
            val type = typeExpr?.resolveType(context) ?: tcExpr.type
            type.checkCompatibleWith(tcExpr)
            if (tcExpr.isCompileTimeConstant()) {
                val value = when(tcExpr) {
                    is TstIntLit -> ValueInt(tcExpr.value, tcExpr.type)
                    is TstStringlit -> ValueString.create(tcExpr.value, tcExpr.type)
                    is TstReallit -> TODO()
                    else -> error("Invalid type in AstConst $tcExpr")
                }
                val symbol = SymbolConstant(location, name, type, value)
                context.addSymbol(symbol)
            } else {
                Log.error(tcExpr.location,"Expression is not compile time constant")
                val value=ValueInt(0,TypeError)
                val symbol = SymbolConstant(location, name, TypeError,value)
                context.addSymbol(symbol)
            }
            TstNullStmt(location)
        }

        is AstFunction -> {
            pathContext = emptyPathContext
            val oldFunction = currentFunction
            currentFunction = this.function
            val tcBody = body.map { it.typeCheck(this) }
            currentFunction = oldFunction
            TstFunction(location, function, tcBody)
        }

        is AstAssign -> {
            val tcLhs = lhs.typeCheckLvalue(context)
            val tcRhs = rhs.typeCheckRvalue(context)
            tcLhs.type.checkCompatibleWith(tcRhs)
            if (tcLhs.isSymbol())
                pathContext = pathContext.refineType(tcLhs.getSymbol(), tcRhs.type)
            var tcOp = AluOp.EQ_I
            if (op==TokenKind.PLUSEQ || op==TokenKind.MINUSEQ)
                if (tcLhs.type==TypeInt || tcLhs.type==TypeChar)
                    tcOp = if (op==TokenKind.PLUSEQ) AluOp.ADD_I else AluOp.SUB_I
                else
                    Log.error(location,"+= and -= currently only supported on integers")
            TstAssign(location, tcLhs, tcRhs, tcOp)
        }

        is AstClass -> {
            // Type check any methods
            val methods = body.filterIsInstance<AstFunction>().map { it.typeCheck(this) }

            // The body of the class has already been type checked in the identifyFields pass - so all we do here is
            // package it up into a TstClass
            TstClass(location, classType, constructorBody, methods)
        }

        is AstFile -> {
            val tcBody = body.map { it.typeCheck(this) }
            TstFile(location, name, tcBody)
        }

        is AstWhile -> {
            val pathContextIn = pathContext
            var pathContextLoop = pathContext
            val oldBreakContext = breakContext
            val oldContinueContext = continueContext

            var iterationCount = 0
            var ret : TstStmt? = null

            while (ret==null) {
                breakContext = mutableListOf()
                continueContext = mutableListOf()
                pathContext = listOf(pathContextIn, pathContextLoop).merge()
                val (tcCond, truePath, falsePath) = cond.typeCheckBool(context)
                pathContext = (continueContext!!+truePath).merge()
                val tcBody = body.map { it.typeCheck(context) }
                // Run to fixed-point. If the path context as we branch back to the loop is different to the one coming in
                if (pathContext != pathContextLoop && !Log.hasErrors() && iterationCount<10) {
                    // If there are any errors already reported then stop iterating
                    pathContextLoop = pathContext
                    iterationCount ++
                } else {
                    pathContext = (breakContext!! + pathContext +falsePath).merge()
                    ret = TstWhile(location, tcCond, tcBody)
                }
            }

            breakContext = oldBreakContext
            continueContext = oldContinueContext
            ret
        }

        is AstFor -> {
            val oldBreakContext = breakContext
            val oldContinueContext = continueContext
            breakContext = mutableListOf()
            continueContext = mutableListOf()
            val tcExpr = expr.typeCheckRvalue(context)
            val elementType = when (tcExpr.type) {
                is TypeError -> TypeError
                is TypeArray -> tcExpr.type.elementType
                is TypeRange -> tcExpr.type.elementType
                is TypeString -> TypeChar
                else -> makeTypeError(location, "Cannot iterate over type '${tcExpr.type}'")
            }
            val symbol = SymbolVar(location, name, elementType, false)
            addSymbol(symbol)
            val tcBody = body.map { it.typeCheck(this) }
            breakContext = oldBreakContext
            continueContext = oldContinueContext
            TstFor(location, symbol, tcExpr, tcBody)
        }

        is AstIf -> {
            val clauses = body as List<AstIfClause>
            val pathContextOut = mutableListOf<PathContext>()
            val tcClauses = mutableListOf<TstIfClause>()
            for (clause in clauses) {
                if (clause.cond != null) {
                    val (tcCond, thenPath, elsePath) = clause.cond.typeCheckBool(context)
                    pathContext = thenPath
                    val tcBody = clause.body.map { it.typeCheck(this) }
                    pathContextOut += pathContext       // Save the path context after this clause has completed
                    tcClauses += TstIfClause(location, tcCond, tcBody)
                    pathContext = elsePath
                } else { // An else clause
                    val tcBody = clause.body.map { it.typeCheck(this) }
                    tcClauses += TstIfClause(location, null, tcBody)
                }
            }
            pathContextOut += pathContext   // the path context after 'else' or if we just fall through

            pathContext = pathContextOut.merge()
            TstIf(location, tcClauses)
        }

        is AstIfClause -> error("Internal error: Got ifClause outside if")

        is AstRepeat -> {
            val oldBreakContext = breakContext
            val oldContinueContext = continueContext
            breakContext = mutableListOf()
            continueContext = mutableListOf()
            val tcBody = body.map { it.typeCheck(this) }
            val (tcCond, truePath, falsePath) = cond.typeCheckBool(this) // Note that we use 'this' here, not 'context' to allow the condition to refer to variables in the loop
            pathContext = truePath
            breakContext = oldBreakContext
            continueContext = oldContinueContext
            TstRepeat(location, tcCond, tcBody)
        }

        is AstTop -> error("AstTop should not appear on internals of AST")

        is AstExprStmt -> {
            TstExprStmt(location, expr.typeCheckRvalue(context))
        }

        is AstNullStmt -> TstNullStmt(location)

        is AstPrint -> {
            val tcExprs = exprs.map { it.typeCheckRvalue(context) }
            for (tcExpr in tcExprs)
                if (tcExpr.type != TypeInt && tcExpr.type != TypeString && tcExpr.type != TypeChar && tcExpr.type != TypeError)
                    Log.error(tcExpr.location, "Got type ${tcExpr.type} but print only supports Int / Char/ String")
            TstPrint(location, tcExprs)
        }

        is AstLambda -> TODO()
    }
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
    val thisSymbol = if (context is AstClass) SymbolVar(location, "this", context.classType, false) else null
    val funcName = if (context is AstClass) "${context.name}/$name" else name
    function = Function(funcName, paramSymbols, thisSymbol, retType)
    allFunctions += function

    // Create a symbol for this function and add it to the current scope
    val funcType = TypeFunction.create(paramSymbols.map{it.type}, retType)
    val symbol = SymbolFunction(location, name, funcType, function)
    context.addSymbol(symbol)
    if (context is AstClass)
        context.classType.addSymbol(symbol)
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
            constructorBody += TstAssign(param.location, fieldExpr, paramExpr, AluOp.EQ_I)
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
            constructorBody += TstAssign(sym.location, fieldExpr, tcExpr, AluOp.EQ_I)
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

