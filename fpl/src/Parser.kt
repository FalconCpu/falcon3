import TokenKind.*

class Parser(val lexer: Lexer) {
    private var currentToken = lexer.nextToken()

    private fun nextToken() : Token {
        val ret = currentToken
        currentToken = lexer.nextToken()
        return ret
    }

    private fun match(kind: TokenKind) : Token {
        if (currentToken.kind == kind)
            return nextToken()
        throw ParseError(currentToken.location, "Got `$currentToken` when expecting ${kind.text}")
    }

    private fun match(kind1: TokenKind,kind2:TokenKind) : Token {
        if (currentToken.kind == kind1 || currentToken.kind == kind2)
            return nextToken()
        throw ParseError(currentToken.location, "Got `$currentToken` when expecting ${kind1.text} or ${kind2.text}")
    }


    private fun canTake(kind: TokenKind) : Boolean {
        if (currentToken.kind == kind) {
            nextToken()
            return true
        }
        return false
    }

    private fun skipToEndOfLine() {
        while (currentToken.kind != EOL && currentToken.kind != EOF)
            nextToken()
        nextToken()
    }

    private fun expectEol() {
        if (currentToken.kind != EOL)
            Log.error(currentToken.location, "Got '$currentToken' when expecting end of line")
        skipToEndOfLine()
    }

    // ======================================================================================
    //                               Expressions
    // ======================================================================================

    private fun parseIdentifier() : AstId {
        val ret = match(ID)
        return AstId(ret.location, ret.text)
    }

    private fun parseIntLit() : AstIntlit {
        val ret = match(INTLIT)
        try {
            val value = if (ret.text.startsWith("0x", ignoreCase = true))
                ret.text.drop(2).toLong(16).toInt() else ret.text.toInt()
            return AstIntlit(ret.location, value)
        } catch (_: NumberFormatException) {
            Log.error(ret.location, "Malformed integer literal '$ret'")
            return AstIntlit(ret.location, 0)
        }
    }

    private fun parseRealLit() : AstReallit {
        val ret = match(REALLIT)
        try {
            val value = ret.text.toDouble()
            return AstReallit(ret.location, value)
        } catch (e: NumberFormatException) {
            Log.error(ret.location, "Malformed real literal '$ret'")
            return AstReallit(ret.location, 0.0)
        }
    }

    private fun parseCharLit() : AstCharlit {
        val ret = match(CHARLIT)
        if (ret.text.length != 1)
            Log.error(ret.location, "Malformed character literal '$ret'")
        return AstCharlit(ret.location, ret.text[0])
    }

    private fun parseStringLit() : AstStringlit {
        val ret = match(STRINGLIT)
        return AstStringlit(ret.location, ret.text)
    }

    private fun parseReturn() : AstReturn {
        val ret = match(RETURN)
        val expr = if (currentToken.kind in listOf(EOL, CLOSEB, CLOSESQ, CLOSECL)) null else parseExpression()
        return AstReturn(ret.location, expr)
    }

    private fun parseAbort() : AstAbort {
        val ret = match(ABORT)
        val expr = parseExpression()
        return AstAbort(ret.location, expr)
    }


    private fun parseBreak() : AstBreak {
        val ret = match(BREAK)
        return AstBreak(ret.location)
    }

    private fun parseContinue() : AstContinue {
        val ret = match(CONTINUE)
        return AstContinue(ret.location)
    }

    private fun parseBracketedExpression() : AstExpr {
        match(OPENB)
        val expr = parseExpression()
        match(CLOSEB)
        return expr
    }

    private fun parseNew() : AstExpr {
        val tok = nextToken()  // LOCAL or NEW
        val typeExpr = parseTypeExpr()
        when (currentToken.kind) {
            OPENSQ -> {
                val initializers = parseInitializerList()
                return AstNewWithInitialiser(tok.location, typeExpr, initializers, tok.kind)
            }
            OPENB -> {
                val args = parseExpressionList()
                val lambda = parseOptLambda()
                return AstNew(tok.location, typeExpr, args, lambda, tok.kind)
            }
            else -> {
                val lambda = parseOptLambda()
                return AstNew(tok.location, typeExpr, emptyList(), lambda, tok.kind)
            }
        }
    }



    private fun parsePrimaryExpression() : AstExpr {
        return when(currentToken.kind) {
            ID -> parseIdentifier()
            INTLIT -> parseIntLit()
            REALLIT -> parseRealLit()
            CHARLIT -> parseCharLit()
            STRINGLIT -> parseStringLit()
            OPENB -> parseBracketedExpression()
            RETURN -> parseReturn()
            BREAK -> parseBreak()
            CONTINUE -> parseContinue()
            NEW -> parseNew()
            INLINE -> parseNew()
            ABORT -> parseAbort()
            else -> throw ParseError(currentToken.location, "Got '$currentToken' when expecting primary expression")
        }
    }

    private fun parseIndexExpression(lhs:AstExpr) : AstExpr {
        match(OPENSQ)
        val index = parseExpression()
        match(CLOSESQ)
        return AstIndex(lhs.location, lhs, index)
    }

    private fun parseMemberExpression(lhs:AstExpr) : AstExpr {
        match(DOT)
        val name = match(ID)
        return AstMember(name.location, lhs, name.text)
    }

    private fun parseCallExpression(lhs:AstExpr) : AstExpr {
        val args = parseExpressionList()
        return AstCall(lhs.location, lhs, args)
    }

    private fun parsePostfixExpression() : AstExpr {
        var expr = parsePrimaryExpression()
        while (true)
            expr = when(currentToken.kind)  {
                OPENSQ -> parseIndexExpression(expr)
                DOT -> parseMemberExpression(expr)
                OPENB -> parseCallExpression(expr)
                else -> return expr
            }
    }

    private fun parsePrefixExpression() : AstExpr {
        return when (currentToken.kind) {
            MINUS -> {
                val tok = match(MINUS)
                AstMinus(tok.location, parsePostfixExpression())
            }
            NOT -> {
                val tok = match(NOT)
                AstNot(tok.location, parsePostfixExpression())
            }
            TILDE -> {
                val tok = match(TILDE)
                AstBitwiseNot(tok.location, parsePostfixExpression())
            }
            TRY -> {
                val tok = match(TRY)
                AstTry(tok.location, parsePrefixExpression())
            }
            else ->
                parsePostfixExpression()
        }
    }

    private fun parseMult() : AstExpr {
        var ret = parsePrefixExpression()
        while(currentToken.kind in listOf(STAR, SLASH, PERCENT, LEFT, RIGHT, AMP)) {
            val op = match(currentToken.kind)
            ret = AstBinop(op.location, op.kind, ret, parsePrefixExpression())
        }
        return ret
    }

    private fun parseAdd() : AstExpr {
        var ret = parseMult()
        while(currentToken.kind in listOf(PLUS, MINUS, BAR, CARET)) {
            val op = match(currentToken.kind)
            ret = AstBinop(op.location, op.kind, ret, parseMult())
        }
        return ret
    }

    private fun parseRange() : AstExpr {
        val ret = parseAdd()
        if (currentToken.kind == DOTDOT) {
            val loc = match(DOTDOT)
            val op = if (currentToken.kind in listOf(LT, LTE, GT, GTE)) nextToken().kind else LTE
            return AstRange(loc.location, ret, parseAdd(), op)
        }
        return ret
    }

    private fun parseComp() : AstExpr {
        var ret = parseRange()
        while(currentToken.kind in listOf(LT, GT, LTE, GTE, EQ, NEQ, IS)) {
            val op = match(currentToken.kind)
            ret = when(op.kind) {
                EQ -> AstEq(op.location, ret, parseRange(), false)
                NEQ -> AstEq(op.location, ret, parseRange(), true)
                IS -> AstIsExpr(op.location, ret, parseTypeExpr())
                else -> AstBinop(op.location, op.kind, ret, parseRange())
            }
        }
        return ret
    }

    private fun parseAnd() : AstExpr {
        var ret = parseComp()
        while(currentToken.kind == AND) {
            val op = match(AND)
            ret = AstAnd(op.location, ret, parseComp())
        }
        return ret
    }

    private fun parseOr() : AstExpr {
        var ret = parseAnd()
        while (currentToken.kind == OR) {
            val op = match(OR)
            ret = AstOr(op.location, ret, parseAnd())
        }
        return ret
    }

    private fun parseCastExpr() : AstExpr {
        val ret = parseOr()
        if (currentToken.kind==AS) {
            val loc = nextToken().location
            val typeExpr = parseTypeExpr()
            return AstCast(loc, ret, typeExpr)
        }
        return ret
    }

    private fun parseExpression() : AstExpr {
        if (canTake(IF)) {
            val cond = parseCastExpr()
            match(THEN)
            val thenExpr = parseExpression()
            match(ELSE)
            val elseExpr = parseExpression()
            return AstIfExpr(cond.location, cond, thenExpr, elseExpr)
        } else
            return parseCastExpr()
    }

    private fun parseExpressionList() : List<AstExpr> {
        val ret = mutableListOf<AstExpr>()
        match(OPENB)
        if (currentToken.kind != CLOSEB)
            do {
                ret.add(parseExpression())
            } while (canTake(COMMA))
        match(CLOSEB)
        return ret
    }

    private fun parseOptExpr() : AstExpr? {
        return if (canTake(EQ))
            parseExpression()
        else
            null
    }

    private fun parseOptLambda() : AstLambda? {
        if (currentToken.kind != OPENCL)
            return null
        val tok = match(OPENCL)
        val expr = parseExpression()
        match(CLOSECL)
        return AstLambda(tok.location, expr)
    }

    private fun parseInitializerList() : List<AstExpr> {
        val ret = mutableListOf<AstExpr>()
        match(OPENSQ)
        if (currentToken.kind!=CLOSESQ)
            do {
                ret += parseExpression()
            } while(canTake(COMMA))
        match(CLOSESQ)
        return ret
    }

    // ======================================================================================
    //                               TypeExpressions
    // ======================================================================================

    private fun parseOptTypeArgs() : List<AstTypeExpr> {
        val ret = mutableListOf<AstTypeExpr>()
        if (currentToken.kind!=LT)
            return ret
        match(LT)
        if (currentToken.kind!=GT)
            do {
                ret.add(parseTypeExpr())
            } while(canTake(COMMA))
        match(GT)
        return ret
    }


    private fun parseTypeId() : AstTypeExpr {
        val tok = match(ID)
        val typeArgs = parseOptTypeArgs()
        return AstTypeId(tok.location, tok.text, typeArgs)
    }

    private fun parseTypeArray() : AstTypeExpr {
        val tok = match(ARRAY)
        val ret : AstTypeExpr?
        if (canTake(LT)) {
            ret=parseTypeExpr()
            match(GT)
        } else
            ret = null
        return AstTypeArray(tok.location, ret)
    }

    private fun parseTypeRange() : AstTypeExpr {
        val tok = match(RANGE)
        match(LT)
        val ret = parseTypeExpr()
        match(GT)
        return AstTypeRange(tok.location, ret)
    }

    private fun parseTypeInline() : AstTypeExpr {
        val tok = match(INLINE)
        if (canTake(ARRAY)) {
            match(LT)
            val elementType = parseTypeExpr()
            match(GT)
            match(OPENB)
            val numElements = parseExpression()
            match(CLOSEB)
            return AstTypeInlineArray(tok.location, elementType, numElements)
        } else
            throw ParseError(tok.location, "Expected array type after inline")
    }

    private fun parseTypeExpr() : AstTypeExpr {
        var ret = when(currentToken.kind) {
            ID -> parseTypeId()
            ARRAY -> parseTypeArray()
            RANGE -> parseTypeRange()
            INLINE -> parseTypeInline()
            else -> throw ParseError(currentToken.location, "Got '$currentToken' when expecting type expression")
        }

        if (canTake(QMARK))
            ret = AstTypeNullable(ret.location, ret)
        if (canTake(EMARK))
            ret = AstTypeErrable(ret.location, ret)
        return ret
    }

    private fun parseOptType() : AstTypeExpr? {
        return if (canTake(COLON))
            parseTypeExpr()
        else
            null
    }

    private fun parseOptTypeParameterList() : List<AstTypeParameter> {
        val ret = mutableListOf<AstTypeParameter>()
        if (currentToken.kind != LT)
            return ret
        match(LT)
        if (currentToken.kind != GT)
            do {
                val tok = match(ID)
                ret += AstTypeParameter(tok.location, tok.text)
            } while (canTake(COMMA))
        match(GT)
        return ret
    }


    // ======================================================================================
    //                               Statements
    // ======================================================================================

    private fun parseDeclaration() : AstDecl {
        val tok = nextToken()
        val name = match(ID)
        val optType = parseOptType()
        val optExpr = parseOptExpr()
        expectEol()
        return AstDecl(name.location, tok.kind, name.text, optType, optExpr)
    }

    private fun parseConst() : AstConst {
        match(CONST)
        val name = match(ID)
        val optType = parseOptType()
        match(EQ)
        val expr = parseExpression()
        expectEol()
        return AstConst(name.location, name.text, optType, expr)
    }


    private fun parseParam(forConstructor: Boolean) : AstParameter {
        val kind = if (currentToken.kind==VAR || currentToken.kind==VAL) {
            if (!forConstructor)
                Log.error(currentToken.location,"'$currentToken' only allowed in constructors")
            nextToken().kind
        } else EOF
        val name = match(ID)
        match(COLON)
        val type = parseTypeExpr()
        return AstParameter(name.location, kind, name.text, type)   // EOF as a marker to indicate a plain parameter
    }

    private fun parseParamList(forConstructor:Boolean=false) : AstParameterList {
        val ret = mutableListOf<AstParameter>()
        match(OPENB)
        if (currentToken.kind != CLOSEB)
            do {
                ret.add(parseParam(forConstructor))
            } while (canTake(COMMA))
        val varargs = canTake(DOTDOTDOT)
        match(CLOSEB)
        if (ret.isEmpty() && varargs)
            Log.error(currentToken.location, "Cannot have varargs with an empty parameter list")
        return AstParameterList(ret,varargs)
    }

    private fun parseEnumParamList() : AstParameterList {
        val ret = mutableListOf<AstParameter>()
        if (currentToken.kind != OPENB)
            return AstParameterList(ret,false)
        match(OPENB)
        if (canTake(CLOSEB))
            return AstParameterList(ret,false)
        do {
            ret.add(parseParam(false))
        } while (canTake(COMMA))
        match(CLOSEB)
        return AstParameterList(ret,false)
    }


    private fun checkEnd(kind:TokenKind) {
        // End statements are optional - they can be infered from indentation. But if they are present,
        // they must be correct.
        if (currentToken.kind!=END)
            return
        val loc = match(END).location
        val tok = nextToken()
        if (tok.kind==EOL)
            return
        if (tok.kind != kind)
            Log.error(loc, "Got 'end $tok' when expecting 'end ${kind.text}'")
        expectEol()
    }

    private fun parseFunction() : AstFunction {
        val isExtern = canTake(EXTERN)
        match(FUN)
        val name = match(ID,FREE)
        val params = parseParamList()
        val retType = if (canTake(ARROW)) parseTypeExpr() else null
        expectEol()
        val body = if (isExtern) emptyList() else  parseStatementBlock()
        checkEnd(FUN)
        return AstFunction(name.location, name.text, params, retType, body, isExtern)
    }

    private fun parseExpressionStatement() : AstStmt  {
        val loc = currentToken.location
        val expr = parsePostfixExpression()
        if (currentToken.kind == EQ || currentToken.kind==PLUSEQ || currentToken.kind==MINUSEQ) {
            val op = nextToken()
            val rhs = parseExpression()
            expectEol()
            return AstAssign(op.location, expr, rhs, op.kind)
        }
        expectEol()
        return AstExprStmt(loc,expr)
    }

    private fun parseWhen() : AstWhen {
        val loc = match(WHEN)
        val expr = parseExpression()
        expectEol()
        val body = parseWhenBlock()
        checkEnd(WHEN)
        return AstWhen(loc.location, expr, body)
    }

    private fun parseWhenBlock() : List<AstWhenClause> {
        val ret = mutableListOf<AstWhenClause>()
        if (!canTake(INDENT)) {
            Log.error(currentToken.location,"Expected indented block after when")
            return ret
        }

        while(currentToken.kind!=DEDENT && currentToken.kind!=EOF) {
            ret += parseWhenClause()
        }
        match(DEDENT)
        return ret
    }

    private fun parseWhenClause() : AstWhenClause {
        val location = currentToken.location
        val exprs = mutableListOf<AstExpr>()
        if (!canTake(ELSE))
            do {
                exprs += parseExpression()
            } while (canTake(COMMA))
        match(ARROW)
        val body = if (currentToken.kind==EOL) {
            expectEol()
            parseStatementBlock()
        } else {
            listOf(parseStatement())
        }
        return AstWhenClause(location, exprs, body)
    }

    private fun parseThenOrIndentedBlock() : List<AstStmt> {
        if (canTake(THEN)) {
            val ret = parseStatement()
            return listOf(ret)
        } else if (canTake(EOL))
            return parseStatementBlock()
        else
            throw ParseError(currentToken.location, "Expected 'then' or 'end of line'")
    }

    private fun parseIfClause() : AstIfClause {
        val tok = nextToken()  // If or Elsif
        val expr = parseExpression()
        val body = parseThenOrIndentedBlock()
        return AstIfClause(tok.location, expr, body)
    }

    private fun parseElseClause() : AstIfClause {
        val tok = nextToken()
        val body = if (canTake(EOL)) parseStatementBlock() else listOf(parseStatement())
        return AstIfClause(tok.location, null, body)
    }

    private fun parseIf() : AstIf {
        val loc = currentToken.location
        val clauses = mutableListOf<AstIfClause>()
        do {
            clauses.add(parseIfClause())
        } while(currentToken.kind==ELSIF)
        if (currentToken.kind==ELSE) {
            clauses.add(parseElseClause())
        }
        checkEnd(IF)
        return AstIf(loc, clauses)
    }

    private fun parseWhile() : AstWhile {
        val loc = match(WHILE).location
        val expr = parseExpression()
        val body = parseThenOrIndentedBlock()
        checkEnd(WHILE)
        return AstWhile(loc, expr, body)
    }

    private fun parseUnsafe() : AstUnsafe {
        val loc = match(UNSAFE).location
        expectEol()
        val body = parseStatementBlock()
        checkEnd(UNSAFE)
        return AstUnsafe(loc, body)
    }

    private fun parseFor() : AstFor {
        match(FOR).location
        val name = match(ID)
        match(IN)
        val expr = parseExpression()
        val body = parseThenOrIndentedBlock()
        checkEnd(FOR)
        return AstFor(name.location, name.text, expr, body)
    }

    private fun parseRepeat() : AstRepeat {
        val loc = match(REPEAT).location
        expectEol()
        val body = parseStatementBlock()
        match(UNTIL)
        val expr = parseExpression()
        expectEol()
        return AstRepeat(loc, expr, body)
    }

    private fun parsePrint() : AstStmt {
        val loc = match(PRINT).location
        val args = parseExpressionList()
        expectEol()
        return AstPrint(loc, args)
    }

    private fun parseFree() : AstStmt {
        val loc = match(FREE).location
        val args = parseExpression()
        expectEol()
        return AstFree(loc, args)
    }

    private fun parseClass() : AstStmt {
        match(CLASS)
        val name = match(ID)
        val typeParams = parseOptTypeParameterList()
        val params = if (currentToken.kind==OPENB) parseParamList(forConstructor = true) else AstParameterList(emptyList(),false)
        expectEol()
        val body = if (currentToken.kind==INDENT) parseClassStatementBlock() else emptyList()
        checkEnd(CLASS)
        return AstClass(name.location, name.text, typeParams, params, body)
    }

    private fun parseEnum(): AstEnum {
        val values = mutableListOf<AstEnumEntry>()
        match(ENUM)
        val name = match(ID)
        val params = parseEnumParamList()
        match(OPENSQ)
        do {
            val id = match(ID)
            val arg = if (currentToken.kind==OPENB) parseExpressionList() else emptyList()
            val entry = AstEnumEntry(id.location, id.text, arg)
            values.add(entry)
        } while (canTake(COMMA))
        match(CLOSESQ)
        expectEol()
        return AstEnum(name.location, name.text, params,values)
    }

    private fun parseStatement() : AstStmt {
        val loc = currentToken.location
        try {
            return when (currentToken.kind) {
                VAL, VAR -> parseDeclaration()
                IF -> parseIf()
                WHILE -> parseWhile()
                REPEAT -> parseRepeat()
                FOR -> parseFor()
                FUN -> parseFunction()
                EXTERN -> parseFunction()
                PRINT -> parsePrint()
                WHEN -> parseWhen()
                FREE -> parseFree()
                CLASS -> parseClass()
                CONST -> parseConst()
                ENUM -> parseEnum()
                UNSAFE -> parseUnsafe()
                else -> parseExpressionStatement()
            }
        } catch (e: ParseError) {
            Log.error(e.message!!)
            skipToEndOfLine()
            return AstNullStmt(loc)
        }
    }

    private fun parseStatementBlock() : List<AstStmt> {
        val ret = mutableListOf<AstStmt>()
        if (currentToken.kind != INDENT) {
            Log.error(currentToken.location, "Missing indented block")
            return ret
        }
        match(INDENT)
        while (currentToken.kind != DEDENT && currentToken.kind != EOF)
            ret.add(parseStatement())
        match(DEDENT)
        return ret
    }

    private fun parseClassStatement() : AstStmt {
        val loc = currentToken.location
        try {
            return when (currentToken.kind) {
                VAL, VAR -> parseDeclaration()
                FUN -> parseFunction()
                else -> throw ParseError(loc, "$currentToken not allowed in Class body")
            }
        } catch (e: ParseError) {
            Log.error(e.message!!)
            skipToEndOfLine()
            return AstNullStmt(loc)
        }
    }

    private fun parseClassStatementBlock() : List<AstStmt> {
        val ret = mutableListOf<AstStmt>()
        if (currentToken.kind != INDENT) {
            Log.error(currentToken.location, "Missing indented block")
            return ret
        }
        match(INDENT)
        while (currentToken.kind != DEDENT && currentToken.kind != EOF)
            ret.add(parseClassStatement())
        match(DEDENT)
        return ret
    }



    private fun parseFile(): AstFile {
        val loc = currentToken.location

        val fileName = currentToken.location.filename.substringAfterLast('/').removeSuffix(".fpl")

        val stmts = mutableListOf<AstStmt>()
        while (currentToken.kind != EOF) {
            stmts.add(parseStatement())
        }
        return AstFile(loc, fileName, stmts)
    }

    companion object {
        fun parse(lexers: List<Lexer>) : AstTop {
            val astFiles = lexers.map { Parser(it).parseFile() }
            return AstTop(nullLocation, astFiles)
        }
    }
}

class ParseError(location: Location, message:String) : Exception("$location: $message")