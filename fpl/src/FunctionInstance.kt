// A function instance is a wrapper around a function to allow for mapping of generic types to concrete types.

class FunctionInstance(
    val name:String,
    val parameters:List<SymbolVar>,
    val thisSymbol : SymbolVar?,
    val isVararg:Boolean,
    val returnType:Type,
    val function : Function
) {

    override fun toString() = name

    fun mapType(typeArgs:Map<TypeParameter,Type>) : FunctionInstance {
        val newParams = parameters.map { it.mapType(typeArgs) as SymbolVar }
        val newThis = thisSymbol?.mapType(typeArgs) as SymbolVar?
        val newReturnType = returnType.substitute(typeArgs)
        return FunctionInstance(name, newParams, newThis, isVararg, newReturnType, function)
    }

    companion object {
        fun create(func:Function, typeArgs:Map<TypeParameter,Type> = emptyMap()) : FunctionInstance {
            val newParams = func.parameters.map {
                val type = it.type.substitute(typeArgs)
                SymbolVar(it.location, it.name, type, it.mutable)
            }
            val newThis = func.thisSymbol?.let {
                val type = it.type.substitute(typeArgs)
                SymbolVar(it.location, it.name, type, it.mutable)
            }
            val newReturnType = func.returnType.substitute(typeArgs)
            return FunctionInstance(func.name, newParams, newThis, func.isVararg, newReturnType, func)
        }
    }
}