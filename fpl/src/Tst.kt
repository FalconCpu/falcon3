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
class TstGlobalVar(location: Location, val symbol:SymbolGlobal, type:Type) : TstExpr(location, type)
class TstFunctionName(location: Location, val symbol:SymbolFunction, type:Type) : TstExpr(location, type)
class TstBinop(location: Location, val op: AluOp, val lhs: TstExpr, val rhs: TstExpr, type:Type) : TstExpr(location, type)
class TstAnd(location: Location, val lhs: TstExpr, val rhs: TstExpr) : TstExpr(location, TypeBool)
class TstOr(location: Location, val lhs: TstExpr, val rhs: TstExpr) : TstExpr(location, TypeBool)
class TstNot(location: Location, val expr: TstExpr) : TstExpr(location, TypeBool)
class TstIndex(location: Location, val expr: TstExpr, val index: TstExpr, type:Type) : TstExpr(location,type)
class TstMember(location: Location, val expr: TstExpr, val field:SymbolField, type:Type) : TstExpr(location,type)
class TstReturn(location: Location, val expr: TstExpr?) : TstExpr(location, TypeNothing)
class TstBreak(location: Location) : TstExpr(location, TypeNothing)
class TstContinue(location: Location) : TstExpr(location, TypeNothing)
class TstMinus(location: Location, val expr: TstExpr, type:Type) : TstExpr(location,type)
class TstIfExpr(location: Location, val cond: TstExpr, val thenExpr: TstExpr, val elseExpr: TstExpr, type:Type) : TstExpr(location,type)
class TstRange(location: Location, val start: TstExpr, val end: TstExpr, val op:AluOp,type:Type) : TstExpr(location,type)
class TstCall(location: Location, val expr: TstExpr, val args: List<TstExpr>, type:Type) : TstExpr(location,type)
class TstNewArray(location: Location, val size: TstExpr, val initializer:TstLambda?, val local:Boolean, type:Type) : TstExpr(location,type)
class TstNewObject(location: Location, val args:List<TstExpr>, type:TypeClass, val local: Boolean) : TstExpr(location,type)
class TstLambda(location: Location, val params: List<SymbolVar>, val body: TstExpr, type:Type) : TstExpr(location,type)

class TstError(location: Location, val message: String) : TstExpr(location, TypeError) {
    init {
        Log.error(location, message)
    }
}

// ================================================
//                  Statements
// ================================================
sealed class TstStmt(location: Location) : Tst(location)
class TstExprStmt(location: Location, val expr: TstExpr) : TstStmt(location)
class TstAssign(location: Location, val lhs: TstExpr, val rhs: TstExpr) : TstStmt(location)
class TstNullStmt(location: Location) : TstStmt(location)
class TstDecl(location: Location, val symbol: Symbol, val expr: TstExpr?) : TstStmt(location)
class TstPrint(location: Location, val exprs: List<TstExpr>) : TstStmt(location)

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
class TstClass(location: Location, val classType:TypeClass, body:List<TstStmt>) : TstBlock(location, body)
class TstFile(location: Location, val name:String, body:List<TstStmt>) : TstBlock(location, body)
class TstTop(location: Location, body:List<TstStmt>) : TstBlock(location, body)

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

        is TstCall -> {
            sb.append("call ($type)\n")
            expr.prettyPrint(sb, indent+1)
            args.forEach { it.prettyPrint(sb, indent+1) }
        }

        is TstContinue -> {
            sb.append("continue ($type)\n")
        }

        is TstError -> {
            sb.append("error: $message\n")
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

        is TstAssign -> {
            sb.append("assign\n")
            lhs.prettyPrint(sb, indent+1)
            rhs.prettyPrint(sb, indent+1)
        }

        is TstClass -> {
            sb.append("class: ${classType}\n")
            body.forEach { it.prettyPrint(sb, indent+1) }
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

        is TstLambda -> {
            sb.append("lambda ($type)\n")
            body.prettyPrint(sb, indent+1)
        }

        is TstNewObject -> {
            sb.append("new-object ($type)\n")
            for (arg in args)
                arg.prettyPrint(sb, indent+1)
        }
    }
}

fun Tst.prettyPrint(): String {
    val sb = StringBuilder()
    prettyPrint(sb, 0)
    return sb.toString()
}

fun TstExpr.isCompileTimeConstant() = this is TstIntLit || this is TstReallit || this is TstStringlit
