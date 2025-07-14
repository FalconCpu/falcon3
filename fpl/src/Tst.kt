// This class is used to represent a type checked syntax tree.
// It mirrors the Ast* classes, but including extra information such as types.

sealed class Tst (val location: Location)

// ================================================
//                  Expressions
// ================================================

sealed class TstExpr(location: Location, val type:Type):Tst(location)
class TstIntLit(location: Location, val value: Int, type:Type) : TstExpr(location, type)    // Also used for char/bool etc
class TstReallit(location: Location, val value: Double, type:Type) : TstExpr(location, type)
class TstStringlit(location: Location, val value: String, type:Type) : TstExpr(location, type)
class TstVariable(location: Location, val symbol:SymbolVar, type:Type) : TstExpr(location, type)
class TstInlineVariable(location: Location, val symbol:SymbolInlineVar, type:Type) : TstExpr(location, type)
class TstGlobalVar(location: Location, val symbol:SymbolGlobal, type:Type) : TstExpr(location, type)
class TstTypeName(location: Location, type:Type) : TstExpr(location, type)
class TstFunctionName(location: Location, val symbol:SymbolFunction, type:Type) : TstExpr(location, type)
class TstBinop(location: Location, val op: AluOp, val lhs: TstExpr, val rhs: TstExpr, type:Type) : TstExpr(location, type)
class TstAnd(location: Location, val lhs: TstExpr, val rhs: TstExpr) : TstExpr(location, TypeBool)
class TstOr(location: Location, val lhs: TstExpr, val rhs: TstExpr) : TstExpr(location, TypeBool)
class TstNot(location: Location, val expr: TstExpr) : TstExpr(location, TypeBool)
class TstIndex(location: Location, val expr: TstExpr, val index: TstExpr, type:Type) : TstExpr(location,type)
class TstMember(location: Location, val expr: TstExpr, val field:SymbolField, type:Type) : TstExpr(location,type)
class TstEmbeddedMember(location: Location, val expr: TstExpr, val field:SymbolInlineField, type:Type) : TstExpr(location,type)
class TstReturn(location: Location, val expr: TstExpr?) : TstExpr(location, TypeNothing)
class TstBreak(location: Location) : TstExpr(location, TypeNothing)
class TstContinue(location: Location) : TstExpr(location, TypeNothing)
class TstMinus(location: Location, val expr: TstExpr, type:Type) : TstExpr(location,type)
class TstBitwiseNot(location: Location, val expr: TstExpr, type:Type) : TstExpr(location,type)
class TstIfExpr(location: Location, val cond: TstExpr, val thenExpr: TstExpr, val elseExpr: TstExpr, type:Type) : TstExpr(location,type)
class TstRange(location: Location, val start: TstExpr, val end: TstExpr, val op:AluOp,type:Type) : TstExpr(location,type)
class TstCall(location: Location, val func:Function, val args: List<TstExpr>, val thisArg: TstExpr?, type:Type) : TstExpr(location,type)
class TstIndirectCall(location:Location, val expr:TstExpr, val args: List<TstExpr>, type:Type) : TstExpr(location,type)
class TstNewArray(location: Location, val size: TstExpr, val initializer:TstLambda?, val kind:TokenKind, type:Type) : TstExpr(location,type)
class TstNewArrayInitializer(location: Location, val initializer:List<TstExpr>, val kind:TokenKind, type:Type) : TstExpr(location,type)
//class TstNewFixedArray(location: Location, val initializer:TstLambda?, val local:Boolean, type:Type) : TstExpr(location,type)
class TstNewObject(location: Location, val args:List<TstExpr>, type:TypeClassInstance, val kind:TokenKind) : TstExpr(location,type)
class TstLambda(location: Location, val params: List<SymbolVar>, val body: TstExpr, type:Type) : TstExpr(location,type)
class TstMethod(location: Location, val thisExpr:TstExpr, val func:SymbolFunction, type:Type) : TstExpr(location,type)
class TstCast(location: Location, val expr: TstExpr, type:Type) : TstExpr(location,type)
class TstAbort(location: Location, val expr: TstExpr) : TstExpr(location, TypeNothing)
class TstSetCall(location: Location, val func: Function, val args: List<TstExpr>, val thisArg: TstExpr, type:Type) : TstExpr(location,type)   // A call to something.set(args, RVALUE)
class TstIsExpr(location:Location, val expr:TstExpr, val isType:Type) : TstExpr(location, TypeBool)
class TstMakeUnion(location:Location, val expr:TstExpr, type:Type) : TstExpr(location, type)
class TstExtractUnion(location: Location, val expr:TstExpr, type:Type) : TstExpr(location, type)
class TstGetEnumData(location: Location, val expr:TstExpr, val field:Symbol) : TstExpr(location, field.type)

class TstError(location: Location, val message: String = "") : TstExpr(location, TypeError) {
    init {
        if (message != "")
            Log.error(location, message)
    }
}

// ================================================
//                  Statements
// ================================================
sealed class TstStmt(location: Location) : Tst(location)
class TstExprStmt(location: Location, val expr: TstExpr) : TstStmt(location)
class TstAssign(location: Location, val lhs: TstExpr, val rhs: TstExpr, val op:AluOp) : TstStmt(location)
class TstNullStmt(location: Location) : TstStmt(location)
class TstDecl(location: Location, val symbol: Symbol, val expr: TstExpr?) : TstStmt(location)
class TstPrint(location: Location, val exprs: List<TstExpr>) : TstStmt(location)
class TstFree(location: Location, val expr: TstExpr) : TstStmt(location)

// ================================================
//                  Blocks
// ================================================
sealed class TstBlock(location: Location, val body:List<TstStmt>) : TstStmt(location)
class TstIfClause(location: Location, val cond: TstExpr?, body: List<TstStmt>) : TstBlock(location, body)
class TstIf(location: Location, body:List<TstIfClause>) : TstBlock(location, body)
class TstWhile(location: Location, val cond: TstExpr, body:List<TstStmt>) : TstBlock(location, body)
class TstRepeat(location: Location, val cond: TstExpr, body:List<TstStmt>) : TstBlock(location, body)
class TstFor(location: Location, val sym: Symbol, val expr: TstExpr, body:List<TstStmt>) : TstBlock(location, body)
class TstFunction(location: Location, val function:Function, body:List<TstStmt>) : TstBlock(location, body)
class TstClass(location: Location, val classType:TypeClassGeneric, body:List<TstStmt>, val methods:List<TstStmt>) : TstBlock(location, body)
class TstFile(location: Location, val name:String, body:List<TstStmt>) : TstBlock(location, body)
class TstTop(location: Location, body:List<TstStmt>) : TstBlock(location, body)
class TstWhen(location: Location, val expr:TstExpr, body:List<TstWhenClause>) : TstBlock(location, body)
class TstWhenClause(location: Location, val exprs:List<TstExpr>, body:List<TstStmt>) : TstBlock(location,body)
class TstUnsafe(location: Location, body:List<TstStmt>) : TstBlock(location, body)

// ================================================
//                  Pretty Printing
// ================================================

fun Tst.prettyPrint(sb: StringBuilder, indent:Int) {
    sb.append("  ".repeat(indent))
    when (this) {
        is TstAnd -> {
            sb.append("and ($type)\n")
            lhs.prettyPrint(sb, indent+1)
            rhs.prettyPrint(sb, indent+1)
        }

        is TstBinop -> {
            sb.append("$op ($type)\n")
            lhs.prettyPrint(sb, indent+1)
            rhs.prettyPrint(sb, indent+1)
        }

        is TstBreak -> {
            sb.append("break ($type)\n")
        }

        is TstMakeUnion -> {
            sb.append("mkunion ($type)\n")
            expr.prettyPrint(sb, indent+1)
        }

        is TstExtractUnion -> {
            sb.append("extractUnion ($type)\n")
            expr.prettyPrint(sb, indent+1)
        }

        is TstCall -> {
            sb.append("call $func\n")
            thisArg?.prettyPrint(sb, indent+1)
            args.forEach { it.prettyPrint(sb, indent+1) }
        }

        is TstContinue -> {
            sb.append("continue ($type)\n")
        }

        is TstError -> {
            sb.append("error: $message\n")
        }

        is TstIsExpr -> {
            sb.append("isExpr $isType ($type)\n")
            expr.prettyPrint(sb, indent+1)
        }

        is TstFunctionName -> {
            sb.append("function: ${symbol.name} ($type)\n")
        }

        is TstGlobalVar -> {
            sb.append("global: ${symbol.name} ($type)\n")
        }

        is TstIfExpr -> {
            sb.append("if-expr ($type)\n")
            cond.prettyPrint(sb, indent+1)
            thenExpr.prettyPrint(sb, indent+1)
            elseExpr.prettyPrint(sb, indent+1)
        }

        is TstIndex -> {
            sb.append("index ($type)\n")
            expr.prettyPrint(sb, indent+1)
            index.prettyPrint(sb, indent+1)
        }

        is TstIntLit -> {
            sb.append("int: $value ($type)\n")
        }

        is TstMember -> {
            sb.append("member: $field ($type)\n")
            expr.prettyPrint(sb, indent+1)
        }

        is TstEmbeddedMember -> {
            sb.append("embeddedMember: $field ($type)\n")
            expr.prettyPrint(sb, indent+1)
        }

        is TstMinus -> {
            sb.append("minus ($type)\n")
            expr.prettyPrint(sb, indent+1)
        }

        is TstNot -> {
            sb.append("not ($type)\n")
            expr.prettyPrint(sb, indent+1)
        }

        is TstOr -> {
            sb.append("or ($type)\n")
            lhs.prettyPrint(sb, indent+1)
            rhs.prettyPrint(sb, indent+1)
        }

        is TstRange -> {
            sb.append("range: $op ($type)\n")
            start.prettyPrint(sb, indent+1)
            end.prettyPrint(sb, indent+1)
        }

        is TstReallit -> {
            sb.append("real: $value ($type)\n")
        }

        is TstReturn -> {
            sb.append("return ($type)\n")
            expr?.prettyPrint(sb, indent+1)
        }

        is TstStringlit -> {
            sb.append("string: \"$value\" ($type)\n")
        }

        is TstVariable -> {
            sb.append("var: $symbol ($type)\n")
        }

        is TstInlineVariable -> {
            sb.append("var: $symbol ($type)\n")
        }

        is TstAssign -> {
            sb.append("assign $op\n")
            lhs.prettyPrint(sb, indent+1)
            rhs.prettyPrint(sb, indent+1)
        }

        is TstClass -> {
            sb.append("class: ${classType}\n")
            body.forEach { it.prettyPrint(sb, indent+1) }
            methods.forEach { it.prettyPrint(sb, indent+1) }
        }

        is TstFile -> {
            sb.append("file: $name\n")
            body.forEach { it.prettyPrint(sb, indent+1) }
        }

        is TstFor -> {
            sb.append("for: $sym\n")
            expr.prettyPrint(sb, indent+1)
            body.forEach { it.prettyPrint(sb, indent+1) }
        }

        is TstFunction -> {
            sb.append("function: $function\n")
            body.forEach { it.prettyPrint(sb, indent+1) }
        }

        is TstIf -> {
            sb.append("if\n")
            body.forEach { it.prettyPrint(sb, indent+1) }
        }

        is TstIfClause -> {
            sb.append("if-clause\n")
            cond?.prettyPrint(sb, indent+1)
            body.forEach { it.prettyPrint(sb, indent+1) }
        }

        is TstRepeat -> {
            sb.append("repeat\n")
            cond.prettyPrint(sb, indent+1)
            body.forEach { it.prettyPrint(sb, indent+1) }
        }

        is TstTop -> {
            sb.append("top\n")
            body.forEach { it.prettyPrint(sb, indent+1) }
        }

        is TstWhile -> {
            sb.append("while\n")
            cond.prettyPrint(sb, indent+1)
            body.forEach { it.prettyPrint(sb, indent+1) }
        }

        is TstDecl -> {
            sb.append("decl: ${symbol.details()}\n")
            expr?.prettyPrint(sb, indent+1)
        }

        is TstExprStmt -> {
            sb.append("expr-stmt\n")
            expr.prettyPrint(sb, indent+1)
        }

        is TstNullStmt -> {
            sb.append("null-stmt\n")
        }

        is TstPrint -> {
            sb.append("print\n")
            for(expr in exprs)
                expr.prettyPrint(sb, indent+1)
        }

        is TstNewArray -> {
            sb.append("new-array ($type)\n")
            size.prettyPrint(sb, indent+1)
            initializer?.prettyPrint(sb, indent+1)
        }

        is TstNewArrayInitializer -> {
            sb.append("new-array-init ($type)\n")
            for(e in initializer)
                e.prettyPrint(sb, indent+1)
        }

        is TstLambda -> {
            sb.append("lambda ($type)\n")
            body.prettyPrint(sb, indent+1)
        }

        is TstNewObject -> {
            sb.append("new-object ($type)\n")
            for (arg in args)
                arg.prettyPrint(sb, indent+1)
        }

        is TstMethod -> {
            sb.append("method: $func\n")
            thisExpr.prettyPrint(sb, indent+1)
        }

        is TstCast -> {
            sb.append("cast ($type)\n")
            expr.prettyPrint(sb, indent+1)
        }

        is TstWhen -> {
            sb.append("when \n")
            expr.prettyPrint(sb, indent+1)
            body.forEach { it.prettyPrint(sb, indent+1) }
        }
        is TstWhenClause -> {
            sb.append("when-clause\n")
            for(expr in exprs)
                expr.prettyPrint(sb, indent+1)
            for(stmt in body)
                stmt.prettyPrint(sb,indent+1)
        }

        is TstFree -> {
            sb.append("free\n")
            expr.prettyPrint(sb, indent+1)
        }

        is TstAbort -> {
            sb.append("abort\n")
            expr.prettyPrint(sb, indent+1)
        }

        is TstTypeName -> {
            sb.append("type-name: $type\n")
        }

        is TstIndirectCall -> {
            sb.append("indirect-call\n")
            expr.prettyPrint(sb, indent+1)
            for(arg in args)
                arg.prettyPrint(sb, indent+1)
        }

//        is TstNewFixedArray -> {
//            sb.append("new-fixed-array ($type)\n")
//            initializer?.prettyPrint(sb, indent+1)
//        }

        is TstSetCall -> {
            sb.append("set-call  $func\n")
            thisArg.prettyPrint(sb, indent+1)
            for(arg in args)
                arg.prettyPrint(sb, indent+1)
        }

        is TstBitwiseNot -> {
            sb.append("bitwise-not\n")
            expr.prettyPrint(sb, indent+1)
        }

        is TstUnsafe -> {
            sb.append("unsafe\n")
            for(stmt in body)
                stmt.prettyPrint(sb, indent+1)
        }

        is TstGetEnumData -> {
            sb.append("get-enum-data $field ($type)\n")
            expr.prettyPrint(sb, indent+1)
        }
    }
}

fun Tst.prettyPrint(): String {
    val sb = StringBuilder()
    prettyPrint(sb, 0)
    return sb.toString()
}

fun TstExpr.isCompileTimeConstant() : Boolean = this is TstIntLit || this is TstReallit || this is TstStringlit ||
            this is TstError || (this is TstCast && this.expr.isCompileTimeConstant())
fun TstExpr.getValue() : Value = when(this) {
    is TstIntLit -> ValueInt(value, type)
    is TstStringlit -> ValueString.create(value, type)
    is TstCast -> expr.getValue()
    is TstReallit -> TODO()
    is TstError -> ValueInt(0, type)
    else -> error("Invalid type in AstConst $this")
}

fun TstExpr.isIntegerConstant() = this is TstIntLit
fun TstExpr.getIntegerConstant() = (this as TstIntLit).value
fun TstExpr.isStringConstant() = this is TstStringlit
fun TstExpr.getStringConstant() = (this as TstStringlit).value
