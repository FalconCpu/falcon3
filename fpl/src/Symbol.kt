sealed class Symbol(val location: Location, val name:String, val type:Type, val mutable: Boolean) {
    override fun toString() = name
}

class SymbolVar(location: Location, name:String, type:Type, mutable:Boolean) : Symbol(location, name, type, mutable)
class SymbolGlobal(location: Location, name:String, type:Type, mutable:Boolean) : Symbol(location, name, type, mutable)
class SymbolFunction(location: Location, name:String, type:Type) : Symbol(location, name, type, false)
