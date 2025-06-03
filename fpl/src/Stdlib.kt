
object Stdlib {
    private val intArg = SymbolVar(nullLocation, "a", TypeInt, false)
    private val stringArg = SymbolVar(nullLocation, "s", TypeString, false)
    private val charArg = SymbolVar(nullLocation, "c", TypeChar, false)

    val printInt = Function("printInt", listOf(intArg), TypeUnit)
    val printString = Function("printString", listOf(stringArg), TypeUnit)
    val printChar = Function("printChar", listOf(charArg), TypeUnit)
    val mallocArray = Function("mallocArray", listOf(intArg, intArg), TypeInt)
    val callocArray = Function("callocArray", listOf(intArg, intArg), TypeInt)
}