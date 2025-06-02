//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {
}

enum class StopAt{PARSE, TYPECHECK, CODEGEN, REG_ALLOC, ASSEMBLY}

fun compile(lexers:List<Lexer>, stopAt: StopAt) : String {
    Log.clear()

    val astTop = Parser.parse(lexers)
    if (Log.hasErrors())   return Log.getErrors()
    if (stopAt == StopAt.PARSE) return astTop.prettyPrint()

    val tstTop = astTop.typeCheck()
    if (Log.hasErrors())   return Log.getErrors()
    if (stopAt == StopAt.TYPECHECK) return tstTop.prettyPrint()

    TODO("CodeGen")
}