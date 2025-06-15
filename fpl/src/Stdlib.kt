
object Stdlib {
    private val intArg = SymbolVar(nullLocation, "a", TypeInt, false)
    private val stringArg = SymbolVar(nullLocation, "s", TypeString, false)
    private val charArg = SymbolVar(nullLocation, "c", TypeChar, false)

    val printInt =  Function("printInt", listOf(intArg), null, false, TypeUnit)
    val printString =  Function("printString", listOf(stringArg), null, false, TypeUnit)
    val printChar =  Function("printChar", listOf(charArg), null, false, TypeUnit)
    val mallocArray =  Function("mallocArray(Int,Int,Bool)", listOf(intArg, intArg), null, false, TypeInt)
    val mallocObject =  Function("mallocObject(ClassDescriptor)", listOf(intArg), null, false, TypeInt)
    val strequal =  Function("strequal", listOf(stringArg,stringArg), null, false, TypeInt)
    val strcmp =  Function("strcmp", listOf(stringArg,stringArg), null, false, TypeInt)
    val free =  Function("free(Int)", listOf(intArg), null, false, TypeUnit)
    val memcpy =  Function("memcpy", listOf(intArg,intArg,intArg), null, false, TypeInt)

    const val SYS_ABORT = 1
}