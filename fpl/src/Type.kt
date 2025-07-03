
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
        val allArrayTypes = mutableListOf<TypeArray>()
        fun create(elementType: Type) : TypeArray {
            val match = allArrayTypes.find {it.elementType==elementType }
            if (match!=null)
                return match
            val name = "Array<$elementType>"
            val new = TypeArray(name, elementType)
            allArrayTypes += new
            return new
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
                return elementType // (location, "Primitive types cannot be nullable")
            return allNullableTypes.getOrPut(elementType) {
                val name = "$elementType?"
                TypeNullable(name, elementType)
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

class TypeEnum(name:String) : Type(name) {
    val symbols = mutableMapOf<String, Symbol>()

    fun addSymbol(symbol:Symbol) {
        val duplicate = symbols[symbol.name]
        if (duplicate!=null)
            Log.error(symbol.location, "Duplicate symbol '$symbol', first defined here: ${duplicate.location}")
        symbols[symbol.name] = symbol
    }

    fun lookupSymbol(location:Location, name:String) : Symbol? {
        val ret = symbols[name]
        if (ret != null && ret.location.filename!="")
            ret.references += location
        return ret
    }

    companion object {
        val allEnumTypes = mutableListOf<TypeEnum>()
    }
}

class TypeParameter(name:String, val constraints:List<Type> = emptyList()) : Type(name)

// Represents a class, possibly with type parameters.
class TypeClassGeneric private constructor(name:String, val typeParameters:List<TypeParameter>, val baseType: Type?) : Type(name) {
    val symbols = mutableMapOf<String, Symbol>()
    var sizeInBytes = 0
    lateinit var constructor : Function

    fun addSymbol(symbol:Symbol) {
        val duplicate = symbols[symbol.name]
        if (duplicate!=null)
            Log.error(symbol.location, "Duplicate symbol '$symbol', first defined here: ${duplicate.location}")
        symbols[symbol.name] = symbol
        when (symbol) {
            is SymbolField -> {
                val symSize = symbol.type.sizeInBytes()
                val fieldAlignment = symbol.type.getAlignmentRequirement()      // Add padding if necessary
                sizeInBytes = (sizeInBytes + fieldAlignment - 1) and -(fieldAlignment)
                symbol.offset = sizeInBytes
                sizeInBytes += symSize
            }
            is SymbolInlineField -> {
                val symSize = symbol.type.sizeInBytes()
                val fieldAlignment = symbol.type.getAlignmentRequirement()
                sizeInBytes = (sizeInBytes + fieldAlignment - 1) and -(fieldAlignment)
                symbol.offset = sizeInBytes
                sizeInBytes += symSize
            }
            else -> {}
        }
    }


    companion object {
        fun create(name:String, typeParameters:List<TypeParameter>, baseType: Type?) : TypeClassGeneric {
            val ret = TypeClassGeneric(name, typeParameters, baseType)
            allClasses.add(ret)
            return ret
        }
    }
}
val allClasses = mutableListOf<TypeClassGeneric>()

// Represents a class with all type arguments instantiated.
class TypeClassInstance private constructor (name:String, val genericClass:TypeClassGeneric, val typeArgs:Map<TypeParameter, Type>) : Type(name) {

    val symbols by lazy {
        genericClass.symbols.mapValues { (_, symbol) -> symbol.mapType(typeArgs) }
    }
    val constructor by lazy {genericClass.constructor}

    fun lookupSymbol(location: Location, name:String) : Symbol? {
        val ret = symbols[name]
        if (ret != null && location.filename!="")
            ret.references += location
        return ret
    }

    companion object {
        val allClassInstances = mutableListOf<TypeClassInstance>()

        fun create(genericClass: TypeClassGeneric, typeArgs:Map<TypeParameter, Type>) : TypeClassInstance {
            val ret = allClassInstances.find { it.genericClass == genericClass && it.typeArgs == typeArgs }
            if (ret != null)
                return ret
            val name = if (typeArgs.isEmpty()) genericClass.name else genericClass.name + "<${typeArgs.map{ it.value.name }.joinToString(",")}>"
            val new = TypeClassInstance(name, genericClass, typeArgs)
            allClassInstances.add(new)
            return new
        }
    }
}

fun Type.substitute(typeArgs:Map<TypeParameter, Type>) : Type {
    return when (this) {
        is TypeArray -> TypeArray.create(elementType.substitute(typeArgs))
        is TypeClassGeneric -> this
        is TypeFunction -> TypeFunction.create(parameters.map { it.substitute(typeArgs) }, returnType.substitute(typeArgs))
        is TypeNullable -> TypeNullable.create(nullLocation, elementType.substitute(typeArgs))
        is TypeParameter -> typeArgs[this] ?: this
        is TypeRange -> TypeRange.create(elementType.substitute(typeArgs))
        is TypeClassInstance -> {
            val newArgs = typeArgs.mapValues { (_, type) -> type.substitute(typeArgs) }
            TypeClassInstance.create(genericClass , newArgs)
        }
        is TypeInlineArray -> TypeInlineArray.create(elementType.substitute(typeArgs), numElements)
        is TypeInlineInstance -> TypeInlineInstance.create(base.substitute(typeArgs) as TypeClassInstance)
        is TypeEnum,
        TypeError,
        TypeBool,
        TypeChar,
        TypeInt,
        TypeNothing,
        TypeNull,
        TypeReal,
        TypeString,
        TypeUnit,
        TypeAny -> this
    }
}

class TypeInlineArray private constructor (val elementType:Type, val numElements:Int) : Type("inline Array<$elementType>($numElements)") {
    companion object {
        val allInlineTypes = mutableListOf<TypeInlineArray>()
        fun create(elementType:Type, numElements:Int) : Type {
            val ret = allInlineTypes.find { it.elementType == elementType && it.numElements == numElements }
            if (ret != null)
                return ret
            val new = TypeInlineArray(elementType, numElements)
            allInlineTypes.add(new)
            return new
        }
    }
}


class TypeInlineInstance private constructor (val base:TypeClassInstance) : Type("inline $base") {
    companion object {
        val allInlineTypes = mutableListOf<TypeInlineInstance>()
        fun create(base:TypeClassInstance) : Type {
            val ret = allInlineTypes.find { it.base == base}
            if (ret != null)
                return ret
            val new = TypeInlineInstance(base)
            allInlineTypes.add(new)
            return new
        }
    }
}



fun Type.isAssignableFrom(other:Type) : Boolean {
    if (this == other || this is TypeError || other is TypeError || this is TypeAny || other is TypeNothing)
        return true

    if (this is TypeNullable && (other is TypeNull || this.elementType.isAssignableFrom(other)))
        return true

    if (this is TypeClassGeneric && other is TypeClassInstance && other.genericClass == this)
        return true

    if (this is TypeClassInstance && other is TypeClassGeneric && this.genericClass == other)
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

//fun Type.dropInline() : Type {
//    return when (this) {
//        is TypeInlineArray -> base.dropInline()
//        is TypeInlineInstance -> base.dropInline()
//        else -> this
//    }
//}

fun Type.isInline() = this is TypeInlineArray || this is TypeInlineInstance

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
        is TypeClassGeneric -> 4  // References to a class are pointers
        is TypeEnum -> 4  // References to an enum are integers
        is TypeParameter -> 4
        is TypeClassInstance -> 4
        is TypeInlineArray-> elementType.sizeInBytes() * numElements
        is TypeInlineInstance -> base.genericClass.sizeInBytes
    }
}

fun Type.getAlignmentRequirement() : Int {
    return when(this) {
        TypeBool -> 1
        TypeChar -> 1
        TypeError -> 1
        else -> 4
    }
}
