sealed class Symbol(val location: Location, val name:String, val type:Type, val mutable: Boolean) {
    override fun toString() = name

    fun details() = when(this) {
        is SymbolFunction -> "FUN:$name:$type"
        is SymbolGlobal -> "GLOBAL:$name:$type"
        is SymbolVar -> "VAR:$name:$type"
    }
}

class SymbolVar(location: Location, name:String, type:Type, mutable:Boolean) : Symbol(location, name, type, mutable)
class SymbolGlobal(location: Location, name:String, type:Type, mutable:Boolean) : Symbol(location, name, type, mutable)
class SymbolFunction(location: Location, name:String, type:Type) : Symbol(location, name, type, false)

fun AstBlock.addSymbol(symbol:Symbol) {
    val dupliate = symbolTable[symbol.name]
    if (dupliate != null)
        Log.error(symbol.location, "Duplicate symbol '$symbol', first defined at ${dupliate.location}")
    symbolTable[symbol.name] = symbol
}

fun AstBlock.lookupSymbol(name:String) : Symbol? {
    return symbolTable[name] ?: parent?.lookupSymbol(name)
}

fun AstBlock.lookupOrDefault(location:Location, name:String) : Symbol {
    val symbol = lookupSymbol(name)
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