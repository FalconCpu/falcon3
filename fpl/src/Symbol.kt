import java.io.File

sealed class Symbol(val location: Location, val name:String, val type:Type, val mutable: Boolean) {
    override fun toString() = name

    fun details() = when(this) {
        is SymbolFunction -> "FUN:$name:$type"
        is SymbolGlobal -> "GLOBAL:$name:$type"
        is SymbolVar -> "VAR:$name:$type"
        is SymbolTypeName -> "TYPE:$name:$type"
        is SymbolField -> "FIELD:$name:$type"
        is SymbolConstant -> "CONST:$name:$type"
        is SymbolInlineField -> "EMBED:$name:$type"
        is SymbolInlineVar -> "INLINE:$name:$type"
    }

    val references = mutableSetOf<Location>()
}

class SymbolVar(location: Location, name:String, type:Type, mutable:Boolean) : Symbol(location, name, type, mutable)
class SymbolGlobal(location: Location, name:String, type:Type, mutable:Boolean) : Symbol(location, name, type, mutable) {
    var offset = -1
}
class SymbolFunction(location: Location, name:String, type:Type, val functions:MutableList<FunctionInstance>) : Symbol(location, name, type, false)
class SymbolTypeName(location: Location, name:String, type:Type) : Symbol(location, name, type, false)
class SymbolField(location: Location, name:String, type:Type, mutable: Boolean) : Symbol(location, name, type, mutable) {
    var offset = -1
}
class SymbolConstant(location: Location, name:String, type:Type, val value:Value) : Symbol(location, name, type, false)

class SymbolInlineVar(location: Location, name:String, type:Type, mutable:Boolean) : Symbol(location, name, type, mutable) {
    var offset = -1
}
class SymbolInlineField(location: Location, name:String, type:Type, mutable: Boolean) : Symbol(location, name, type, mutable) {
    var offset = -1
}


fun AstBlock.addSymbol(symbol:Symbol) {
    // Promote non-private symbols at file level to global scope
    if (this is AstFile) {
        parent!!.addSymbol(symbol)
        return
    }

    val duplicate = symbolTable[symbol.name]
    if (duplicate != null)
        Log.error(symbol.location, "Duplicate symbol '$symbol', first defined at ${duplicate.location}")
    symbolTable[symbol.name] = symbol
}

fun AstBlock.lookupSymbol(location:Location, name:String) : Symbol? {
    val ret = predefinedSymbols[name] ?: symbolTable[name] ?: parent?.lookupSymbol(location, name)
    if (ret != null && location.filename!="")
        ret.references += location
    return ret
}

fun AstBlock.lookupOrDefault(location:Location, name:String) : Symbol {
    val symbol = lookupSymbol(location, name)
    if (symbol != null)
        return symbol
    Log.error(location, "Undeclared identifier '$name'")
    val new = SymbolVar(location, name, TypeError, true)
    addSymbol(new)
    return new
}

fun AstBlock.setParent(parent:AstBlock) {
    this.parent = parent
    for(blk in body.filterIsInstance<AstBlock>())
        blk.setParent(this)
}

// ============================================================
//                    Type Substitution
// ============================================================
// Generate a new symbol to remap a type for generics

 fun Symbol.mapType(map:Map<TypeParameter,Type>) : Symbol {
    return when (this) {
        is SymbolField -> {
            val newType = type.substitute(map)
            val newSymbol = SymbolField(location, name, newType, mutable)
            newSymbol.offset = offset
            newSymbol
        }
        is SymbolInlineField -> {
            val newType = type.substitute(map)
            val newSymbol = SymbolInlineField(location, name, newType, mutable)
            newSymbol.offset = offset
            newSymbol
        }
        is SymbolFunction -> {
            val newType = type.substitute(map)
            val newFunctions = functions.map { it.mapType(map) }.toMutableList()
            SymbolFunction(location, name, newType, newFunctions)
        }

        is SymbolConstant,
        is SymbolGlobal -> this
        is SymbolTypeName -> TODO()
        is SymbolVar -> SymbolVar(location, name, type.substitute(map), mutable)
        is SymbolInlineVar -> SymbolInlineVar(location, name, type.substitute(map), mutable)
    }
}



// ============================================================
//                    Predefined symbols
// ============================================================

private val predefinedSymbolList = listOf(
    SymbolTypeName(nullLocation, "Bool", TypeBool),
    SymbolTypeName(nullLocation, "Char", TypeChar),
    SymbolTypeName(nullLocation, "Int", TypeInt),
    SymbolTypeName(nullLocation, "Real", TypeReal),
    SymbolTypeName(nullLocation, "String", TypeString),
    SymbolTypeName(nullLocation, "Unit", TypeUnit),
    SymbolTypeName(nullLocation, "Any", TypeAny),
    SymbolTypeName(nullLocation, "Nothing", TypeNothing),
    SymbolConstant(nullLocation, "true", TypeBool, ValueInt(1, TypeBool)),
    SymbolConstant(nullLocation, "false", TypeBool, ValueInt(0, TypeBool)),
    SymbolConstant(nullLocation, "null", TypeNull, ValueInt(0, TypeNull)),
    SymbolFunction(nullLocation, "memcpy",TypeFunction.create(listOf(TypeInt,TypeInt,TypeInt),TypeInt),
        mutableListOf(FunctionInstance.create(Stdlib.memcpy))),
)

val predefinedSymbols = predefinedSymbolList.associateBy { it.name }

val sizeField = SymbolField(nullLocation, "size", TypeInt, false).also { it.offset = -4 }


// ============================================================
//                    SymbolMap
// ============================================================
// Output a JSON file containing a list of all symbols with their
// types and locations.

private fun Symbol.toJson():String {
    val kind = when (this) {
        is SymbolField -> "field"
        is SymbolConstant -> "constant"
        is SymbolFunction -> "function"
        is SymbolTypeName -> when (type) {
            is TypeClassGeneric -> "class"
            is TypeClassInstance -> "class"
            is TypeEnum -> "enum"
            else -> "type"
        }
        is SymbolVar -> "var"
        is SymbolInlineVar -> "inline var"
        is SymbolInlineField -> "inline field"
        is SymbolGlobal -> "global"
    }

    return """{"name":"$name", "kind":"$kind", "type":"$type", "definition":${location.toJson()}, "references":[${references.joinToString{ it.toJson() }}], "mutable":$mutable}"""
}

private fun AstBlock.getAllSymbols(out:MutableSet<Symbol>) {
    for (symbol in symbolTable.values)
        out += symbol
    for (blk in body.filterIsInstance<AstBlock>())
        blk.getAllSymbols(out)

    for (cls in TypeClassInstance.Companion.allClassInstances)
        out += cls.symbols.values

    for (enum in TypeEnum.allEnumTypes)
        out += enum.symbols.values
}

fun AstTop.writeSymbolMap() {
    val fh = File("symbol-map.json").bufferedWriter()
    val symbols = mutableSetOf<Symbol>()
    getAllSymbols(symbols)
    val json = """{"symbols":[
        |${symbols.joinToString(",\n") { it.toJson() }}
        |]}""".trimMargin()
    fh.write(json)
    fh.close()
}