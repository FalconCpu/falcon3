// Type checking phase of the compiler. This phase takes the AST built in the parser phase and
// converts it to a TypeChecked AST (Tst).

private var currentFunction: Function? = null
private var pathContext = emptyPathContext

// list of PathContexts at break/continue locations in current loop
private var breakContext : MutableList<PathContext>? = null
private var continueContext : MutableList<PathContext>? = null

val allGlobalVars = mutableListOf<SymbolGlobal>()
private var allGlobalSize = 0

// ================================================================
//                          createGlobalVar
// ================================================================

fun createGlobalVar(location:Location, name:String, type:Type, mutable:Boolean) : SymbolGlobal {
    if (allGlobalVars.isEmpty())     // If the global table was reset then reset the global size
        allGlobalSize = 0

    val ret = SymbolGlobal(location, name, type, mutable)
    ret.offset = allGlobalSize
    allGlobalSize += 4               // For now all global variables are 4 bytes
    if (allGlobalSize>=1024)            // For now assume a global variable table of 1024 bytes
        error("Too many global variables")
    allGlobalVars.add(ret)
    return ret
}



// ================================================================
//                            getSymbol
// ================================================================

fun TstExpr.isSymbol() : Boolean = when(this) {
    is TstVariable -> true
    is TstGlobalVar -> true
    else -> false
}

fun TstExpr.getSymbol() : Symbol = when(this) {
    is TstVariable -> symbol
    is TstGlobalVar -> symbol
    else -> error("getSymbol called on $this")
}

// ================================================================
//                            Rvalues
// ================================================================

fun AstExpr.typeCheckRvalue(context:AstBlock, allowFreeUse:Boolean=false, allowTypeNames:Boolean=false) : TstExpr {
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
                    if (symbol in pathContext.freedVars && !allowFreeUse)
                        Log.warn(location,"Symbol '$name' may point to a freed object")
                    TstVariable(location, symbol, pathContext.getType(symbol))
                }
                is SymbolGlobal -> TstGlobalVar(location, symbol, pathContext.getType(symbol))
                is SymbolFunction -> TstFunctionName(location, symbol, symbol.type)
                is SymbolTypeName ->
                    if (allowTypeNames)
                        TstTypeName(location, symbol.type)
                    else
                        TstError(location, "Cannot use type name '$name' as an expression")
                is SymbolField -> {
                    val thisSymbol = currentFunction?.thisSymbol
                    // do some sanity checks
                    if (thisSymbol==null)error("Accessed a SymbolField when not in a method  $currentFunction")
                    assert(symbol in (thisSymbol.type as TypeClassGeneric).symbols.values){
                        "Internal Compiler Error: Field '$name' found, but does not belong to 'this' type '${thisSymbol.type}'."
                    }

                    val thisExpr = TstVariable(location, thisSymbol, thisSymbol.type)
                    TstMember(location, thisExpr, symbol, symbol.type)
                }

                is SymbolEmbeddedField -> {
                    val thisSymbol = currentFunction?.thisSymbol
                    // do some sanity checks
                    if (thisSymbol==null)    error("Accessed a SymbolField when not in a method")
                    assert(symbol in (thisSymbol.type as TypeClassGeneric).symbols.values){
                        "Internal Compiler Error: Field '$name' found, but does not belong to 'this' type '${thisSymbol.type}'."
                    }

                    val thisExpr = TstVariable(location, thisSymbol, thisSymbol.type)
                    TstEmbeddedMember(location, thisExpr, symbol, symbol.type)
                }
                is SymbolConstant -> symbol.toExpression()
                is SymbolInlineVar -> TstInlineVariable(location, symbol, symbol.type)
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
            when (tcExpr) {
                is TstFunctionName -> tcExpr.symbol.resolveOverload(location, tcArgs, null)

                is TstMethod ->  tcExpr.func.resolveOverload(location, tcArgs, tcExpr.thisExpr)

                else -> {
                    if (tcExpr.type !is TypeFunction)
                        return TstError(location, "Cannot call expression of type ${tcExpr.type}")
                    val paramTypes = tcExpr.type.parameters
                    checkParameters(location, paramTypes, tcArgs)
                    TODO("Indirect function calls")
                }
            }
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

        is AstBitwiseNot -> {
            val tc = expr.typeCheckRvalue(context)
            TypeInt.checkCompatibleWith(tc)
            TstBitwiseNot(location,tc, TypeInt)
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
                is TypeInlineArray -> TstIndex(location, tcExpr, tcIndex, tcExpr.type.elementType)
                is TypeString -> TstIndex(location, tcExpr, tcIndex, TypeChar)
                is TypeClassInstance -> {
                    val sf = tcExpr.type.lookupSymbol("get")
                    if (sf is SymbolFunction && sf.functions.size==1 && sf.functions[0].parameters.size==1 && sf.functions[0].parameters[0].type==TypeInt)
                        TstCall(location, sf.functions[0].function, listOf(tcIndex), tcExpr, sf.functions[0].returnType)
                    else
                        TstError(location, "Cannot index expression of type ${tcExpr.type}")
                }
                else -> TstError(location, "Cannot index expression of type ${tcExpr.type}")
            }
        }

        is AstIfExpr -> {
            val (tcCond, truePathContext, falsePathContext) = cond.typeCheckBool(context)
            pathContext = truePathContext
            val tcTrue = thenExpr.typeCheckRvalue(context)
            val pathContext1 = pathContext
            pathContext = falsePathContext
            val tcFalse = elseExpr.typeCheckRvalue(context)
            pathContext = listOf(pathContext,pathContext1).merge()
            tcTrue.type.checkCompatibleWith(tcFalse)
            TstIfExpr(location, tcCond, tcTrue, tcFalse, tcTrue.type)
        }

        is AstMember -> {
            val tcExpr = expr.typeCheckRvalue(context, allowTypeNames=true)
            if (tcExpr is TstTypeName) {
                // Accessing static members of a type
                when (tcExpr.type) {
                    is TypeEnum -> {
                        val field = tcExpr.type.lookupSymbol(name)
                        when (field) {
                            null -> TstError(location, "Enum ${tcExpr.type} does not have a field named '$name'")
                            is SymbolConstant -> field.toExpression()
                            else -> error("Unexpected Symbol type")
                        }
                    }
                    else -> TstError(location, "Cannot access static members of type '$tcExpr.type}'")
                }
            } else when (tcExpr.type) {
                is TypeArray -> {
                    if (name== "size")
                         TstMember(location, tcExpr, sizeField, TypeInt)
                    else
                        TstError(location, "Array does not have a field named '$name'")
                }

                is TypeInlineArray -> {
                    if (name== "size")
                        TstIntLit(location, tcExpr.type.numElements, TypeInt)
                    else
                        TstError(location, "Inline array does not have a field named '$name'")
                }

                is TypeString -> {
                    if (name == "length")
                        TstMember(location, tcExpr, sizeField, TypeInt)
                    else
                        TstError(location, "String does not have a field named '$name'")
                }

                is TypeClassGeneric -> TstError(location, "Cannot access members of a generic class")

                is TypeClassInstance -> {
                    val field = tcExpr.type.lookupSymbol(name)
                    when (field) {
                        null -> TstError(location,"Class ${tcExpr.type} does not have a field named '$name'")
                        is SymbolField -> TstMember(location, tcExpr, field, field.type)
                        is SymbolFunction -> TstMethod(location, tcExpr, field, field.type)
                        is SymbolEmbeddedField -> TstEmbeddedMember(location, tcExpr, field, field.type)
                        else -> error("Unexpected Symbol type")
                    }
                }

                is TypeNullable -> TstError(location, "Cannot access '$name' as expression may be null")

                is TypeError -> tcExpr

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
            var tcType = type.resolveType(context)
            val tcArgs = args.map{it.typeCheckRvalue(context)}
            when(tcType) {
                is TypeArray -> {
                    if (tcArgs.size != 1)
                        return TstError(location, "new Array requires exactly one argument, not ${tcArgs.size}")
                    val tcSize = tcArgs[0]
                    TypeInt.checkCompatibleWith (tcSize)
                    val tcLambda = lambda?.typeCheckLambda(context, listOf(SymbolVar(location,"it", TypeInt, false)))
                    if (tcType.elementType==TypeNothing) {
                        if (tcLambda!=null)
                            tcType = TypeArray.create(tcLambda.body.type)
                        else
                            Log.error(location,"Cannot determine type for array")
                    }
                    if (tcLambda!=null)
                        tcType.elementType.checkCompatibleWith(tcLambda.body)
                    if (kind==TokenKind.INLINE) {
                        if (tcSize.isCompileTimeConstant())
                            tcType = TypeInlineArray.create(tcType.elementType, tcSize.getIntegerConstant())
                        else
                            Log.error(location, "Array size must be a compile-time constant")
                    }
                    TstNewArray(location, tcSize, tcLambda, kind,tcType)
                }

                is TypeClassInstance -> {
                    checkParameters(location, tcType.constructor.parameters.map{it.type}, tcArgs)
                    TstNewObject(location, tcArgs, tcType, kind)
                }

                is TypeClassGeneric -> TstError(location, "Cannot create instance of generic class")

                is TypeError -> TstError(location)

                else -> TstError(location, "Cannot create instance of type $tcType")
            }
        }

        is AstNewWithInitialiser -> {
            val tcType = type.resolveType(context)
            if (tcType !is TypeArray)
                return TstError(location,"Initializer list only supported for arrays")
            var elementType = tcType.elementType
            val tcInit = initializer.map {it.typeCheckRvalue(context)}
            if (elementType==TypeNothing && tcInit.isNotEmpty())
                elementType = tcInit[0].type
            if (elementType==TypeNothing)
                Log.error(location,"Cannot determine type for array")
            tcInit.forEach { elementType.checkCompatibleWith(it) }
            TstNewArrayInitializer(location, tcInit, kind, TypeArray.create(elementType))
        }

        is AstCast -> {
            val tcExpr = expr.typeCheckRvalue(context)
            val tcType = typeExpr.resolveType(context)
            TstCast(location, tcExpr, tcType)
        }

        is AstAbort ->{
            val tcExpr = expr.typeCheckRvalue(context)
            TypeInt.checkCompatibleWith(tcExpr)
            pathContext = pathContext.setUnreachable()
            TstAbort(location, tcExpr)
        }
    }
}


private fun SymbolConstant.toExpression(): TstExpr {
    return when(value) {
        is ValueClassDescriptor -> TstError(location, "Cannot use type name '$name' as an expression")
        is ValueInt -> TstIntLit(location, value.value, type)
        is ValueString -> TstStringlit(location, value.value, type)
        is ValueFunctionName -> TstFunctionName(location, value.value, type )
    }
}

private fun checkParameters(location:Location, parameters:List<Type>, args:List<TstExpr>) {
// TODO : Varargs should be handled here
//    if (parameters.isNotEmpty() && parameters.last() is TypeVararg) {
//        if (args.size < parameters.size-1) {
//            Log.error(location, "Got ${args.size} arguments when expecting at least ${parameters.size - 1}")
//            return
//        }
//        for (index in 0..<parameters.size-1)
//            parameters[index].checkCompatibleWith(args[index])
//        val varargType = (parameters.last() as TypeVararg).elementType
//        for (index in parameters.size-1..<args.size)
//            varargType.checkCompatibleWith(args[index])
//    } else {
        // Not a vararg
        if (args.size != parameters.size)
            Log.error(location, "Got ${args.size} arguments when expecting ${parameters.size}")
        else for (index in args.indices)
            parameters[index].checkCompatibleWith(args[index])
//    }
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
    BinopEntry(TokenKind.AMP,     TypeInt, TypeInt, AluOp.AND_I, TypeInt),
    BinopEntry(TokenKind.BAR,     TypeInt, TypeInt, AluOp.OR_I,  TypeInt),
    BinopEntry(TokenKind.CARET,   TypeInt, TypeInt, AluOp.XOR_I, TypeInt),

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

fun AstExpr.typeCheckLvalue(context:AstBlock) : TstExpr {
    return when (this) {
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
                    if (thisSymbol == null) error("Accessed a SymbolField when not in a method")
                    assert(symbol in (thisSymbol.type as TypeClassGeneric).symbols.values) {
                        "Internal Compiler Error: Field '$name' found, but does not belong to 'this' type '${thisSymbol.type}'."
                    }

                    val thisExpr = TstVariable(location, thisSymbol, thisSymbol.type)
                    if (!symbol.mutable)
                        TstError(location, "Cannot assign to a non-mutable field '${symbol.name}'")
                    else
                        TstMember(location, thisExpr, symbol, symbol.type)
                }

                is SymbolEmbeddedField -> {
                    TODO("Lvalue of embedded field '$name' not yet implemented")
                }
                is SymbolInlineVar -> TODO()
            }
        }

        is AstIndex -> {
            val tcExpr = expr.typeCheckRvalue(context)
            val tcIndex = index.typeCheckRvalue(context)
            if (tcExpr.type == TypeError) return tcExpr
            TypeInt.checkCompatibleWith(tcIndex)

            when (tcExpr.type) {
                is TypeArray -> TstIndex(location, tcExpr, tcIndex, tcExpr.type.elementType)
                is TypeInlineArray -> TstIndex(location, tcExpr, tcIndex, tcExpr.type.elementType)
                is TypeString -> TstIndex(location, tcExpr, tcIndex, TypeChar)

                is TypeClassInstance -> {
                    val sf = tcExpr.type.lookupSymbol("set")
                    if (sf is SymbolFunction && sf.functions.size == 1 && sf.functions[0].parameters.size == 2 && sf.functions[0].parameters[0].type == TypeInt) {
                        TstSetCall(location, sf.functions[0].function, listOf(tcIndex), tcExpr, sf.functions[0].parameters[1].type)
                    }
                    else
                        TstError(location, "Cannot index expression of type ${tcExpr.type}")
                }

                else -> TstError(location, "Cannot index expression of type ${tcExpr.type}")
            }
        }


        is AstMember -> {
            val ret = typeCheckRvalue(context)
            if (ret is TstMember && !ret.field.mutable)
                Log.error(location, "Field '${ret.field}' is not mutable")
            ret
        }

        else -> {
            TstError(location, "Expression is not an lvalue")
        }
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

fun Type.applyTypeArguments(location:Location, typeArguments:List<Type>) : Type {
    if (this !is TypeClassGeneric) {
        if (typeArguments.isNotEmpty())
            Log.error(location, "Type '$this' does not take type parameters")
        return this
    }

    if (typeArguments.size != typeParameters.size)
        Log.error(location, "Got ${typeArguments.size} type arguments when expecting $typeParameters.size")
    for(arg in typeArguments)
        if (arg.sizeInBytes()!=4)
            Log.error(location, "Currently generic types must be exactly word sized")

    val map = typeParameters.zip(typeArguments).associate{it.first to it.second}
    return TypeClassInstance.create(this, map)
}

fun AstTypeExpr.resolveType(context:AstBlock) : Type = when(this) {
    is AstTypeId -> {
        val sym = context.lookupSymbol(name)
        val args = astTypeArgs.map { it.resolveType(context) }
        when (sym) {
            null -> makeTypeError(location, "Unknown type '$name'")
            is SymbolTypeName -> sym.type.applyTypeArguments(location, args)
            else -> makeTypeError(location, "'$name' is not a type")
        }
    }

    is AstTypeArray -> {
        val elementType = base?.resolveType(context) ?: TypeNothing
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

    is AstTypeInlineArray -> {
        val elementType = base.resolveType(context)
        val tcNumElement = numElements.typeCheckRvalue(context)
        if (tcNumElement.isIntegerConstant())
            TypeInlineArray.create(elementType, tcNumElement.getIntegerConstant())
        else
            makeTypeError(location, "Inline array size must be a constant")
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
            val symbol = if (context is AstFile) {
                if (type.isInline())
                    TODO("Not yet implemented : Inline types as global variables")
                else
                    createGlobalVar(location, name, type, mutable)
            } else {
                if (type.isInline())
                    SymbolInlineVar(location, name, type, mutable)
                else
                    SymbolVar(location, name, type, mutable)
            }
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
                val value = tcExpr.getValue()
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
            if (tcLhs.isSymbol()) {
                val sym = tcLhs.getSymbol()
                pathContext = pathContext.reassignVar(sym).refineType(sym,tcRhs.type)
            }
            var tcOp = AluOp.EQ_I
            if (op==TokenKind.PLUSEQ || op==TokenKind.MINUSEQ)
                if (tcLhs.type==TypeInt || tcLhs.type==TypeChar || tcLhs.type==TypeError)
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
            val oldBreakContext = breakContext
            val oldContinueContext = continueContext

            breakContext = mutableListOf()
            continueContext = mutableListOf()
            val (tcCond, truePath, falsePath) = cond.typeCheckBool(context)
            pathContext = (continueContext!!+truePath).merge()
            val tcBody = body.map { it.typeCheck(context) }
            pathContext = (breakContext!! + pathContext +falsePath).merge()
            val ret = TstWhile(location, tcCond, tcBody)
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
                is TypeInlineArray -> tcExpr.type.elementType
                is TypeRange -> tcExpr.type.elementType
                is TypeString -> TypeChar
                is TypeClassInstance -> {
                    // We consider a class to be iterable if it has a field called 'size' which is an integer
                    // and a field called 'get' which takes an integer and returns an object
                    val sz = tcExpr.type.lookupSymbol("size")
                    val gt = tcExpr.type.lookupSymbol("get")
                    if (sz is SymbolField && gt is SymbolFunction && sz.type==TypeInt &&
                        gt.functions.size==1 && gt.functions[0].parameters.size==1 && gt.functions[0].parameters[0].type==TypeInt)
                        gt.functions[0].returnType
                    else
                        makeTypeError(location, "Cannot iterate over type '${tcExpr.type}'")
                }
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
            val clauses = body.filterIsInstance<AstIfClause>()
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
                if (tcExpr.type != TypeInt && tcExpr.type != TypeString && tcExpr.type != TypeChar && tcExpr.type != TypeBool
                    && tcExpr.type != TypeError && tcExpr.type !is TypeEnum)
                    Log.error(tcExpr.location, "Got type ${tcExpr.type} but print only supports Int / Char/ String")
            TstPrint(location, tcExprs)
        }

        is AstLambda -> TODO()
        is AstWhen -> {
            val tcExpr = expr.typeCheckRvalue(context)
            if (tcExpr.type!=TypeError && tcExpr.type!=TypeInt && tcExpr.type!=TypeString && tcExpr.type !is TypeEnum)
                Log.error(location,"When only supports expressions of type Int or String or Enum")
            val tcClauses = mutableListOf<TstWhenClause>()
            for (clause in body.filterIsInstance<AstWhenClause>()) {
                val tcArgs = clause.clauses.map{ it.typeCheckRvalue(context)}
                for(arg in tcArgs)
                    tcExpr.type.checkCompatibleWith(arg)
                val tcBody = clause.body.map {it.typeCheck(clause)}
                tcClauses += TstWhenClause(clause.location, tcArgs, tcBody)
            }
            tcClauses.checkForDuplicates()
            TstWhen(location, tcExpr, tcClauses)
        }

        is AstFree -> {
            val tcExpr = expr.typeCheckRvalue(context, allowFreeUse = true)
            if (tcExpr==TypeError)
                return TstNullStmt(location)
            val type = if (tcExpr.type is TypeNullable) tcExpr.type.elementType else tcExpr.type
            if (type !is TypeClassInstance && type !is TypeArray && type != TypeString)
                Log.error(location, "Free only supports expressions of type Class or Array")
            if (tcExpr.isSymbol()) {
                val sym = tcExpr.getSymbol()
                if (sym in pathContext.freedVars)
                    Log.warn(location, "Possible double free of '$sym'")
                pathContext = pathContext.freeVar(tcExpr.getSymbol())
            }
            TstFree(location, tcExpr)
        }

        is AstWhenClause -> error("AstWhenClause outside of when")
        is AstEnum -> TstNullStmt(location) // Do nothing here as enums are handled in the identify Fields stage
    }
}

private fun List<TstWhenClause>.checkForDuplicates() {
    val elseClauses = filter { it.exprs.isEmpty() }
    for (e in elseClauses)
        if (e != last())
            Log.error(e.location, "else clause must be the last clause in when")

    val duplicateInt = mutableSetOf<Int>()
    val duplicateString = mutableSetOf<String>()
    for (clause in this)
        for (expr in clause.exprs)
            if (expr.isIntegerConstant()) {
                val v = expr.getIntegerConstant()
                if (v in duplicateInt)
                    Log.error(expr.location, "Duplicate value '$v'")
                duplicateInt += v
            } else if (expr.isStringConstant()) {
                val v = expr.getStringConstant()
                if (v in duplicateString)
                    Log.error(expr.location, "Duplicate value '$v'")
                duplicateString += v
            }
}

// ===============================================================================
//                              Function Overloads
// ===============================================================================

private fun FunctionInstance.canReceiveArgs(args:List<TstExpr>) : Boolean{
    if (isVararg) {
        if (args.size < parameters.size - 1)
            return false
        for(i in 0 until parameters.size-1)
            if (! parameters[i].type.isAssignableFrom(args[i].type))
                return false
        val varargType = ((parameters.last().type) as TypeArray).elementType
        for(i in parameters.size until args.size)
            if (! varargType.isAssignableFrom(args[i].type))
                return false
        return true
    } else {
        if (args.size != parameters.size)
            return false
        for(i in parameters.indices)
            if (! parameters[i].type.isAssignableFrom(args[i].type))
                return false
        return true
    }
}

private fun FunctionInstance.hasSameParametersAs(other:Function): Boolean {
    if (isVararg != other.isVararg)
        return false
    if (parameters.size != other.parameters.size)
        return false
    for (i in parameters.indices)
        if (parameters[i].type!=other.parameters[i].type)
            return false
    return true
}

private fun SymbolFunction.resolveOverload(location: Location, args:List<TstExpr>, thisArg:TstExpr?) : TstExpr {
    val matching = functions.filter { it.canReceiveArgs(args) }
    if (matching.isEmpty()) {
        val candidates = functions.joinToString(separator = "\n") { it.name }
        val argTypes = args.joinToString{it.type.name}
        return TstError(location,"No functions match '$name($argTypes)'\nCandidates are:-\n$candidates")
    } else if (matching.size>1) {
        val duplicates = matching.joinToString(separator = "\n") { it.name }
        val argTypes = args.joinToString{it.type.name}
        return TstError(location, "Ambiguous overloads for '$name($argTypes)'\nCandidates are:-\n$duplicates")
    } else
        return TstCall(location, matching[0].function, args, thisArg, matching[0].returnType)
}


private fun AstParameterList.createSymbols(context:AstBlock) : List<SymbolVar> {
    val ret = mutableListOf<SymbolVar>()

    for(param in params) {
        var type = param.type.resolveType(context)
        if (isVararg && param==params.last())
            type = TypeArray.create(type)
        ret += SymbolVar(param.location, param.name, type, false)
    }
    return ret
}

private fun AstFunction.createFunctionSymbol(context:AstBlock) {
    // Generate symbols for parameters and add them to the functions symbol table
    val paramSymbols = params.createSymbols(context)
    val retType = this.retType?.resolveType(context) ?: TypeUnit
    for (param in paramSymbols)
        addSymbol(param)

    // Build the mangled name for the function

    val funcName = if (context is AstClass) context.name+"/"+name else name
    val paramTypeNames = paramSymbols.joinToString(prefix = "(", postfix = ")", separator = ",") {
        if (params.isVararg && it == paramSymbols.last()) (it.type as TypeArray).elementType.name + "..." else it.type.name
    }

    // Create the Function object to represent this function in the back end
    val thisSymbol = if (context is AstClass) SymbolVar(location, "this", context.classType, false) else null
    function = Function(funcName+paramTypeNames, paramSymbols, thisSymbol, params.isVararg, retType, extern)

    allFunctions += function

    // Special case - destructors are just methods with the name `free`. They are not allowed to take parameters
    if (thisSymbol!=null && name=="free" && paramSymbols.isNotEmpty())
            Log.error(location, "Destructor cannot take parameters")

    // Create a symbol for this function and add it to the current scope
    // Allow for file-scope non-private symbols can get promoted to global scope
    val existingSymbol = context.symbolTable[name] ?:
            if (context is AstFile) context.parent?.symbolTable[name] else null

    val sym = when(existingSymbol) {
        null -> {
            val new = SymbolFunction(location, name, TypeUnit, mutableListOf())
            context.addSymbol(new)
            if (context is AstClass)
                context.classType.addSymbol(new)
            new
        }
        is SymbolFunction -> existingSymbol
        else -> {
            Log.error(location, "Duplicate symbol '$name', first defined at ${existingSymbol.location}")
            val new = SymbolFunction(location, name, TypeUnit, mutableListOf())
            context.symbolTable[name] = new
            new
        }
    }

    // check to see if there is already a function with the same parameters
    val duplicates = sym.functions.filter{it.hasSameParametersAs(function)}
    if (duplicates.isNotEmpty())
        Log.error(location, "Multiple definitions of '$name' with the same parameters")

    sym.functions += FunctionInstance.create(function)
}

private fun AstClass.createClassSymbol(context:AstBlock) {
    val typeParameters = astTypeParameters.map { TypeParameter(it.name) }
    for ((ast,type) in astTypeParameters.zip(typeParameters))
        addSymbol( SymbolTypeName(ast.location, type.name, type))

    // TODO - handle superclasses
    classType = TypeClassGeneric.create(name, typeParameters, null)
    val symbol = SymbolTypeName(location, name, classType)
    context.addSymbol(symbol)
}

private fun AstClass.createFieldSymbols(context: AstBlock) {
    // build a function for the constructor
    val paramSymbols = params.createSymbols(this)
    val thisSym = SymbolVar(location, "this", classType, false)
    val thisExpr = TstVariable(thisSym.location, thisSym, thisSym.type)
    classType.constructor = Function(name, paramSymbols, thisSym, params.isVararg, TypeUnit)
    allFunctions += classType.constructor
    val astConstructor = AstFunction(location, name, params, null, emptyList())  // provide a scope for the constructor
    astConstructor.setParent(this)
    astConstructor.addSymbol(thisSym)
    for (sym in paramSymbols)
        astConstructor.addSymbol(sym)

    // Parameters could be marked VAL or VAR - in which case they define a field as well as being constructor parameters
    for ((param, sym) in params.params.zip(paramSymbols))
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
    val oldFunction = currentFunction
    currentFunction = classType.constructor

    for (stmt in body.filterIsInstance<AstDecl>()) {
        val tcExpr = stmt.expr?.typeCheckRvalue(astConstructor)
        val type = stmt.typeExpr?.resolveType(this) ?:
                   tcExpr?.type ?:
                   makeTypeError(location,"Cannot determine type for '${stmt.name}'")
        val mutable = stmt.kind==TokenKind.VAR
        val sym = if (type.isInline())
            SymbolEmbeddedField(stmt.location, stmt.name, type, mutable)
        else
            SymbolField(stmt.location, stmt.name, type, mutable)
        classType.addSymbol(sym)
        addSymbol(sym)
        if (tcExpr!=null) {
            sym.type.checkCompatibleWith(tcExpr)
            val fieldExpr = when(sym) {
                is SymbolField -> TstMember(sym.location, thisExpr, sym, sym.type)
                is SymbolEmbeddedField -> TstEmbeddedMember(sym.location, thisExpr, sym, sym.type)
                else -> error("Invalid symbol")
            }
            constructorBody += TstAssign(sym.location, fieldExpr, tcExpr, AluOp.EQ_I)
        }
    }

    currentFunction = oldFunction
}

private fun AstEnum.createEnumSymbol(context: AstBlock) {
    val symbol = SymbolTypeName(location, name, enumType)
    context.addSymbol(symbol)
}

private fun AstEnum.createFieldSymbols(context: AstBlock) {
    // Create a symbol for each enum value
    for ((index,name) in values.withIndex()) {
        val value = ValueInt(index, enumType)
        val symbol = SymbolConstant(location, name.name, enumType, value)
        enumType.addSymbol(symbol)
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
    for (stmt in body)
        when (stmt) {
            is AstClass -> stmt.createFieldSymbols(this)
            is AstEnum -> stmt.createFieldSymbols(this)
            is AstBlock -> stmt.identifyFields()
            else -> {}
        }
}


private fun AstBlock.identifyClasses() {
    // Recursively scan the AST for functions and create symbols for them
    for (stmt in body)
        when (stmt) {
            is AstClass -> stmt.createClassSymbol(this)
            is AstEnum -> stmt.createEnumSymbol(this)
            is AstBlock -> stmt.identifyClasses()
            else -> {}
        }
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

