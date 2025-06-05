sealed class Type(val name:String) {
    override fun toString() = name
}

object TypeUnit : Type("Unit")
object TypeNull : Type("Null")
object TypeBool : Type("Bool")
object TypeChar : Type("Char")
object TypeInt : Type("Int")
object TypeReal : Type("Real")
object TypeString : Type("String")
object TypeAny : Type("Any")
object TypeNothing : Type("Nothing")
object TypeError : Type("Error")

fun makeTypeError(location: Location, message:String) : Type {
    Log.error(location, message)
    return TypeError
}

class TypeArray private constructor(name:String, val elementType: Type) : Type(name) {
    companion object {
        val allArrayTypes = mutableMapOf<Type, TypeArray>()
        fun create(elementType: Type) = allArrayTypes.getOrPut(elementType) {
            val name = "Array<$elementType>"
            TypeArray(name, elementType)
        }
    }
}

class TypeRange private constructor(name:String, val elementType: Type) : Type(name) {
    companion object {
        val allRangeTypes = mutableMapOf<Type, TypeRange>()
        fun create(elementType: Type) = allRangeTypes.getOrPut(elementType) {
            val name = "Range<$elementType>"
            TypeRange(name, elementType)
        }
    }
}

class TypeNullable private constructor(name:String, val elementType: Type) : Type(name) {
    companion object {
        val allNullableTypes = mutableMapOf<Type, TypeNullable>()
        fun create(location:Location, elementType: Type) : Type {
            if (elementType is TypeError) return TypeError
            if (elementType is TypeNullable) return elementType
            if (elementType is TypeInt || elementType is TypeReal || elementType is TypeChar || elementType is TypeBool)
                return makeTypeError(location, "Primitive types cannot be nullable")
            return allNullableTypes.getOrPut(elementType) {
                val name = "$elementType?"
                TypeNullable(name, elementType)
            }
        }
    }
}

class TypeVararg private constructor(name:String, val elementType: Type) : Type(name) {
    companion object {
        val allVarargTypes = mutableMapOf<Type, TypeVararg>()
        fun create(elementType: Type) : Type {
            if (elementType is TypeError) return TypeError
            return allVarargTypes.getOrPut(elementType) {
                val name = "$elementType..."
                TypeVararg(name, elementType)
            }
        }
    }
}


class TypeFunction private constructor(name:String, val parameters: List<Type>, val returnType: Type) : Type(name) {
    companion object {
        val allFunctionTypes = mutableListOf<TypeFunction>()
        fun create(parameters: List<Type>, returnType: Type) : TypeFunction {
            val existing = allFunctionTypes.find { it.parameters == parameters && it.returnType == returnType }
            if (existing != null)
                return existing
            val name = "(${parameters.joinToString(",")})->$returnType"
            val new = TypeFunction(name, parameters, returnType)
            allFunctionTypes.add(new)
            return new
        }
    }
}

class TypeClass private constructor(name:String, val baseType: Type?) : Type(name) {
    val symbols = mutableMapOf<String, Symbol>()
    var sizeInBytes = 0
    lateinit var constructor : Function
    lateinit var constructorParameters : List<Type>

    fun addSymbol(symbol:Symbol) {
        val duplicate = symbols[symbol.name]
        if (duplicate!=null)
            Log.error(symbol.location, "Duplicate symbol '$symbol', first defined here: ${duplicate.location}")
        symbols[symbol.name] = symbol
        if (symbol is SymbolField) {
            val symSize = symbol.type.sizeInBytes()

            // Add padding if necessary
            when(symSize) {
                0, 1 -> {}
                2 -> sizeInBytes = (sizeInBytes + 1) and -2
                else -> sizeInBytes = (sizeInBytes + 3) and -4
            }
            symbol.offset = sizeInBytes
            sizeInBytes += symSize
        }
    }

    fun lookupSymbol(name:String) : Symbol? {
        return symbols[name]
    }

    companion object {
        fun create(name:String, baseType: Type?) : TypeClass {
            val ret = TypeClass(name, baseType)
            allClasses.add(ret)
            return ret
        }
    }
}
val allClasses = mutableListOf<TypeClass>()


fun Type.isAssignableFrom(other:Type) : Boolean {
    if (this == other || this is TypeError || other is TypeError || this is TypeAny || other is TypeNothing)
        return true

    if (this is TypeNullable && (other is TypeNull || this.elementType.isAssignableFrom(other)))
        return true

    // Todo - should we allow for covariance on arrays and functions. For now, no. but maybe consider later.

    return false
}

fun Type.checkCompatibleWith(expr:TstExpr) {
    if (!isAssignableFrom(expr.type))
        Log.error(expr.location, "Got type '${expr.type}' when expecting '$this'")
}

fun Type.defaultPromotions() : Type {
    if (this == TypeChar)
        return TypeInt
    return this
}

fun Type.sizeInBytes() : Int {
    // Gets the size of a type in bytes.
    return when(this) {
        TypeAny -> 4
        is TypeArray -> 4
        TypeBool -> 1
        TypeChar -> 1
        TypeError -> 0
        is TypeFunction -> 4
        TypeInt -> 4
        TypeNothing -> 4
        TypeNull -> 4
        is TypeNullable -> 4
        is TypeRange -> TODO()
        TypeReal -> 4
        TypeString -> 4
        TypeUnit -> 0
        is TypeVararg -> 4
        is TypeClass -> 4  // References to a class are pointers
    }

}