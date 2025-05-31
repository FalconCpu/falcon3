
sealed class Ast (val location: Location)

// ================================================
//                  Expressions
// ================================================

sealed class AstExpr(location: Location):Ast(location)
class AstIntlit(location: Location, val value: Int) : AstExpr(location)
class AstReallit(location: Location, val value: Double) : AstExpr(location)
class AstStringlit(location: Location, val value: String) : AstExpr(location)
class AstCharlit(location: Location, val value: Char) : AstExpr(location)
class AstId(location: Location, val name:String) : AstExpr(location)
class AstBinop(location: Location, val op: TokenKind, val left: AstExpr, val right: AstExpr) : AstExpr(location)
class AstIndex(location: Location, val expr: AstExpr, val index: AstExpr) : AstExpr(location)
class AstMember(location: Location, val expr: AstExpr, val name: String) : AstExpr(location)
class AstReturn(location: Location, val expr: AstExpr?) : AstExpr(location)
class AstBreak(location: Location) : AstExpr(location)
class AstContinue(location: Location) : AstExpr(location)
class AstNot(location: Location, val expr: AstExpr) : AstExpr(location)
class AstMinus(location: Location, val expr: AstExpr) : AstExpr(location)

class AstCall(location: Location, val expr: AstExpr, val args: List<AstExpr>) : AstExpr(location)

// ================================================
//                  TypeExpressions
// ================================================

sealed class AstTypeExpr(location: Location) : Ast(location)
class AstTypeId(location: Location, val name:String) : AstTypeExpr(location)
class AstTypeArray(location: Location, val base: AstTypeExpr) : AstTypeExpr(location)
class AstTypeNullable(location: Location, val base: AstTypeExpr) : AstTypeExpr(location)

// ================================================
//                  Statements
// ================================================
sealed class AstStmt(location: Location) : Ast(location)
class AstExprStmt(location: Location, val expr: AstExpr) : AstStmt(location)
class AstAssign(location: Location, val left: AstExpr, val right: AstExpr) : AstStmt(location)
class AstNullStmt(location: Location) : AstStmt(location)
class AstDecl(location: Location, val kind:TokenKind, val name: String, val typeExpr: AstTypeExpr?, val expr: AstExpr?) : AstStmt(location)

// ================================================
//                  Blocks
// ================================================
sealed class AstBlock(location: Location, val body:List<AstStmt>) : AstStmt(location) {
    val symbolTable = mutableMapOf<String,Symbol>()
    var parent : AstBlock? = null
}

class AstIfClause(location: Location, val cond: AstExpr?, body: List<AstStmt>) : AstBlock(location, body)
class AstIf(location: Location, body:List<AstIfClause>) : AstBlock(location, body)
class AstWhile(location: Location, val cond: AstExpr, body:List<AstStmt>) : AstBlock(location, body)
class AstRepeat(location: Location, val cond: AstExpr, body:List<AstStmt>) : AstBlock(location, body)
class AstFor(location: Location, val name: String, val expr: AstExpr, body:List<AstStmt>) : AstBlock(location, body)
class AstFunction(location: Location, val name: String, val args: List<AstParameter>, val retType:AstTypeExpr?, body:List<AstStmt>) : AstBlock(location, body)
class AstClass(location: Location, val name: String, val args:List<AstParameter>, body:List<AstStmt>) : AstBlock(location, body)
class AstFile(location: Location, val name:String, body:List<AstStmt>) : AstBlock(location, body)
class AstTop(location: Location, body:List<AstStmt>) : AstBlock(location, body)

// ================================================
//                  Misc
// ================================================
class AstParameter(location: Location, val name: String, val type: AstTypeExpr) : Ast(location)


// ================================================
//                  Pretty Printer
// ================================================

fun Ast.prettyPrint(sb: StringBuilder, indent:Int) {
    sb.append(" ".repeat(indent))
    when (this) {
        is AstIntlit -> sb.append("Intlit $value\n")
        is AstReallit -> sb.append("Reallit $value\n")
        is AstStringlit -> sb.append("Stringlit $value\n")
        is AstCharlit -> sb.append("Charlit $value\n")
        is AstBinop -> {
            sb.append("Binop $op\n")
            left.prettyPrint(sb, indent+1)
            right.prettyPrint(sb, indent+1)
        }
        is AstId ->
            sb.append("Id $name\n")
        is AstCall -> {
            sb.append("Call\n")
            expr.prettyPrint(sb, indent+1)
            for (arg in args)
                arg.prettyPrint(sb, indent+1)
        }
        is AstIndex -> {
            sb.append("Index\n")
            expr.prettyPrint(sb, indent+1)
            index.prettyPrint(sb, indent+1)
        }

        is AstParameter -> {
            sb.append("Parameter $name\n")
            type.prettyPrint(sb, indent+1)
        }
        is AstClass -> {
            sb.append("Class $name\n")
            for (arg in args)
                arg.prettyPrint(sb, indent+1)
            for (stmt in body)
                stmt.prettyPrint(sb, indent+1)
        }
        is AstMember -> {
            sb.append("Member $name\n")
            expr.prettyPrint(sb, indent+1)
        }
        is AstFor -> {
            sb.append("For $name\n")
            expr.prettyPrint(sb, indent+1)
            for (stmt in body)
                stmt.prettyPrint(sb, indent+1)
        }
        is AstFunction -> {
            sb.append("Function $name\n")
            for (arg in args)
                arg.prettyPrint(sb, indent+1)
            retType?.prettyPrint(sb, indent+1)
            for (stmt in body)
                stmt.prettyPrint(sb, indent+1)
        }
        is AstIf -> {
            sb.append("If\n")
            for (clause in body)
                clause.prettyPrint(sb, indent+1)
        }
        is AstIfClause -> {
            sb.append("IfClause\n")
            cond?.prettyPrint(sb, indent+1)
            for (stmt in body)
                stmt.prettyPrint(sb, indent+1)
        }
        is AstRepeat -> {
            sb.append("Repeat\n")
            cond.prettyPrint(sb, indent+1)
            for (stmt in body)
                stmt.prettyPrint(sb, indent+1)
        }
        is AstWhile -> {
            sb.append("While\n")
            cond.prettyPrint(sb, indent+1)
            for (stmt in body)
                stmt.prettyPrint(sb, indent+1)
        }
        is AstNot -> {
            sb.append("Not\n")
            expr.prettyPrint(sb, indent+1)
        }
        is AstMinus -> {
            sb.append("Minus\n")
            expr.prettyPrint(sb, indent+1)
        }
        is AstBreak ->
            sb.append("Break\n")

        is AstContinue ->
            sb.append("Continue\n")

        is AstDecl -> {
            sb.append("Decl $kind $name\n")
            typeExpr?.prettyPrint(sb, indent+1)
            expr?.prettyPrint(sb, indent+1)
        }
        is AstExprStmt -> {
            sb.append("ExprStmt\n")
            expr.prettyPrint(sb, indent+1)
        }

        is AstReturn -> {
            sb.append("Return\n")
            expr?.prettyPrint(sb, indent+1)
        }

        is AstTypeArray -> {
            sb.append("TypeArray\n")
            base.prettyPrint(sb, indent+1)
        }
        is AstTypeId ->
            sb.append("Type $name\n")
        is AstTypeNullable -> {
            sb.append("TypeNullable\n")
            base.prettyPrint(sb, indent+1)
        }

        is AstFile -> {
            sb.append("File $name\n")
            for (stmt in body)
                stmt.prettyPrint(sb, indent+1)
        }
        is AstTop -> {
            for (stmt in body)
                stmt.prettyPrint(sb, indent)
        }

        is AstNullStmt ->
            sb.append("NullStmt\n")

        is AstAssign -> {
            sb.append("Assign\n")
            left.prettyPrint(sb, indent+1)
            right.prettyPrint(sb, indent+1)
        }
    }
}

fun Ast.prettyPrint() : String {
    val sb = StringBuilder()
    prettyPrint(sb, 0)
    return sb.toString()
}