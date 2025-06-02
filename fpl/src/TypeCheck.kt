// Type checking phase of the compiler. This phase takes the AST built in the parser phase and
// converts it to a TypeChecked AST (Tst).

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

        is AstCall -> TODO()

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

        is AstIfExpr -> TODO()
        is AstIndex -> TODO()
        is AstMember -> TODO()
        is AstMinus -> TODO()
        is AstRange -> TODO()
        is AstReturn -> TODO()
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
    is AstId -> TODO()
    is AstIndex -> TODO()
    is AstMember -> TODO()
    else -> {
        TstError(location, "Expression is not an lvalue")
    }
}

// ================================================================
//                         Conditions
// ================================================================

fun AstExpr.typeCheckBool(context:AstBlock) {
    val tc = typeCheckRvalue(context)
    TypeBool.checkCompatibleWith(tc)
}

// ================================================================
//                         Types
// ================================================================

fun AstTypeExpr.resolveType(context:AstBlock) : Type = when(this) {
    is AstTypeArray -> TODO()
    is AstTypeId -> TODO()
    is AstTypeNullable -> TODO()
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

    is AstFunction -> TODO()
    is AstAssign -> TODO()
    is AstClass -> TODO()

    is AstFile -> {
        val tcBody = body.map{it.typeCheck(this)}
        TstFile(location, name, tcBody)
    }

    is AstWhile -> {
        val tcCond = cond.typeCheckRvalue(context)
        val tcBody = body.map{it.typeCheck(context)}
        TstWhile(location, tcCond, tcBody)
    }

    is AstFor -> TODO()
    is AstIf -> TODO()
    is AstIfClause -> TODO()
    is AstRepeat -> TODO()
    is AstTop -> error("AstTop should not appear on internals of AST")
    is AstExprStmt -> TODO()
    is AstNullStmt -> TstNullStmt(location)
}

fun AstTop.typeCheck() : TstTop {
    for (stmt in body.filterIsInstance<AstBlock>())
        stmt.setParent(this)

    val tcBody = body.map{it.typeCheck(this)}
    return TstTop(location, tcBody)
}

