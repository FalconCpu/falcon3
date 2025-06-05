
object Stdlib {
    private val intArg = SymbolVar(nullLocation, "a", TypeInt, false)
    private val stringArg = SymbolVar(nullLocation, "s", TypeString, false)
    private val charArg = SymbolVar(nullLocation, "c", TypeChar, false)

    val printInt = Function("printInt", listOf(intArg), null, TypeUnit)
    val printString = Function("printString", listOf(stringArg), null, TypeUnit)
    val printChar = Function("printChar", listOf(charArg), null, TypeUnit)
    val mallocArray = Function("mallocArray", listOf(intArg, intArg), null, TypeInt)
    val callocArray = Function("callocArray", listOf(intArg, intArg), null, TypeInt)
    val mallocObject = Function("mallocObject", listOf(intArg), null, TypeInt)
    val strequal = Function("strequal", listOf(stringArg,stringArg), null, TypeInt)
    val strcmp = Function("strcmp", listOf(stringArg,stringArg), null, TypeInt)

}