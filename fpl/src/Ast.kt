
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
class AstBinop(location: Location, val op: TokenKind, val lhs: AstExpr, val rhs: AstExpr) : AstExpr(location)
class AstEq(location: Location, val lhs: AstExpr, val rhs: AstExpr, val notEq:Boolean) : AstExpr(location)
class AstAnd(location: Location, val lhs: AstExpr, val rhs: AstExpr) : AstExpr(location)
class AstOr(location: Location, val lhs: AstExpr, val rhs: AstExpr) : AstExpr(location)
class AstIndex(location: Location, val expr: AstExpr, val index: AstExpr) : AstExpr(location)
class AstMember(location: Location, val expr: AstExpr, val name: String) : AstExpr(location)
class AstReturn(location: Location, val expr: AstExpr?) : AstExpr(location)
class AstBreak(location: Location) : AstExpr(location)
class AstContinue(location: Location) : AstExpr(location)
class AstNot(location: Location, val expr: AstExpr) : AstExpr(location)
class AstMinus(location: Location, val expr: AstExpr) : AstExpr(location)
class AstIfExpr(location: Location, val cond: AstExpr, val thenExpr: AstExpr, val elseExpr: AstExpr) : AstExpr(location)
class AstRange(location: Location, val start: AstExpr, val end: AstExpr, val op:TokenKind) : AstExpr(location)
class AstNew(location: Location, val type:AstTypeExpr, val args: List<AstExpr>, val lambda:AstLambda?, val local:Boolean) : AstExpr(location)
class AstCast(location: Location, val expr:AstExpr, val typeExpr:AstTypeExpr) : AstExpr(location)

class AstCall(location: Location, val expr: AstExpr, val args: List<AstExpr>) : AstExpr(location)

// ================================================
//                  TypeExpressions
// ================================================

sealed class AstTypeExpr(location: Location) : Ast(location)
class AstTypeId(location: Location, val name:String) : AstTypeExpr(location)
class AstTypeArray(location: Location, val base: AstTypeExpr) : AstTypeExpr(location)
class AstTypeRange(location: Location, val base: AstTypeExpr) : AstTypeExpr(location)
class AstTypeNullable(location: Location, val base: AstTypeExpr) : AstTypeExpr(location)

// ================================================
//                  Statements
// ================================================
sealed class AstStmt(location: Location) : Ast(location)
class AstExprStmt(location: Location, val expr: AstExpr) : AstStmt(location)
class AstAssign(location: Location, val lhs: AstExpr, val rhs: AstExpr, val op:TokenKind) : AstStmt(location)
class AstNullStmt(location: Location) : AstStmt(location)
class AstDecl(location: Location, val kind:TokenKind, val name: String, val typeExpr: AstTypeExpr?, val expr: AstExpr?) : AstStmt(location)
class AstConst(location: Location, val name:String, val typeExpr:AstTypeExpr?, val expr:AstExpr) : AstStmt(location)
class AstPrint(location: Location, val exprs: List<AstExpr>) : AstStmt(location)

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
class AstClass(location: Location, val name: String, val params:AstParameterList, body:List<AstStmt>) : AstBlock(location, body) {
    lateinit var classType : TypeClass
    // We typecheck the body of classes in an early type checking pass as there may be forward references to fields.
    // But this means we may generate field initializer statements before we are ready to generate the constructor.
    // So we store it in this list, until we get to typechecking the constructor body.
    val constructorBody = mutableListOf<TstStmt>()
}
class AstWhen(location: Location, val expr:AstExpr, body:List<AstWhenClause>) : AstBlock(location,body)
class AstWhenClause(location: Location, val clauses:List<AstExpr>, body:List<AstStmt>) : AstBlock(location,body)
class AstLambda(location: Location, val expr:AstExpr) : AstBlock(location, emptyList())

class AstFunction(location: Location, val name: String, val params: AstParameterList, val retType:AstTypeExpr?, body:List<AstStmt>)
    : AstBlock(location, body) {
    lateinit var function : Function
}

class AstFile(location: Location, val name:String, body:List<AstStmt>) : AstBlock(location, body)
class AstTop(location: Location, body:List<AstStmt>) : AstBlock(location, body)

// ================================================
//                  Misc
// ================================================
class AstParameter(location: Location, val kind:TokenKind, val name: String, val type: AstTypeExpr) : Ast(location)
class AstParameterList(val params:List<AstParameter>, val isVararg:Boolean)


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
            lhs.prettyPrint(sb, indent+1)
            rhs.prettyPrint(sb, indent+1)
        }
        is AstEq -> {
            sb.append("Binop ${if(notEq) "!=" else "="}\n")
            lhs.prettyPrint(sb, indent+1)
            rhs.prettyPrint(sb, indent+1)
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
            for (arg in params.params)
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
            for (arg in params.params)
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

        is AstTypeRange -> {
            sb.append("TypeRange\n")
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
            lhs.prettyPrint(sb, indent+1)
            rhs.prettyPrint(sb, indent+1)
        }

        is AstIfExpr -> {
            sb.append("IfExpr\n")
            cond.prettyPrint(sb, indent+1)
            thenExpr.prettyPrint(sb, indent+1)
            elseExpr.prettyPrint(sb, indent+1)
        }

        is AstRange -> {
            sb.append("Range $op\n")
            start.prettyPrint(sb, indent+1)
            end.prettyPrint(sb, indent+1)
        }

        is AstAnd -> {
            sb.append("And\n")
            lhs.prettyPrint(sb, indent+1)
            rhs.prettyPrint(sb, indent+1)
        }

        is AstOr -> {
            sb.append("Or\n")
            lhs.prettyPrint(sb, indent+1)
            rhs.prettyPrint(sb, indent+1)
        }

        is AstPrint -> {
            sb.append("Print\n")
            for (expr in exprs)
                expr.prettyPrint(sb, indent+1)
        }

        is AstNew -> {
            sb.append("New $local\n")
            type.prettyPrint(sb, indent+1)
            for (arg in args)
                arg.prettyPrint(sb, indent+1)
            lambda?.prettyPrint(sb, indent+1)
        }

        is AstLambda -> {
            sb.append("Lambda\n")
            expr.prettyPrint(sb, indent+1)
        }

        is AstConst -> {
            sb.append("Const $name\n")
            expr.prettyPrint(sb, indent+1)
        }

        is AstCast -> {
            sb.append("Cast\n")
            typeExpr.prettyPrint(sb, indent+1)
            expr.prettyPrint(sb, indent+1)
        }

        is AstWhen -> {
            sb.append("When\n")
            expr.prettyPrint(sb, indent+1)
            for (clause in body)
                clause.prettyPrint(sb, indent+1)
        }
        is AstWhenClause -> {
            sb.append("WhenClause\n")
            for(clause in clauses)
                clause.prettyPrint(sb,indent+1)
            for(stmt in body)
                stmt.prettyPrint(sb,indent+1)
        }
    }
}

fun Ast.prettyPrint() : String {
    val sb = StringBuilder()
    prettyPrint(sb, 0)
    return sb.toString()
}