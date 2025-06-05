
data class PathContext(
    val uninitialized:Set<Symbol>,
    val maybeUninitialized:Set<Symbol>,
    val refinedTypes: Map<Symbol,Type>,
    val unreachable: Boolean) {

    fun setUnreachable() = PathContext(uninitialized, maybeUninitialized, refinedTypes, true)

    fun addUninitialized(sym:Symbol) = PathContext(uninitialized + sym, maybeUninitialized + sym, refinedTypes, unreachable)

    fun initialize(sym:Symbol) = if (sym !in maybeUninitialized) this else
            PathContext(uninitialized-sym, maybeUninitialized-sym, refinedTypes, unreachable)

    fun refineType(sym:Symbol, type:Type) = PathContext(uninitialized-sym, maybeUninitialized-sym, refinedTypes + (sym to type), unreachable)

    fun getType(sym:Symbol) = refinedTypes.getOrDefault(sym, sym.type)
}

val emptyPathContext = PathContext(emptySet(), emptySet(), emptyMap(), false)

fun List<PathContext>.merge() : PathContext {
    // Only consider paths which are reachable
    val reachable = this.filter { !it.unreachable }

    // If no path is reachable then return empty
    if (reachable.isEmpty())
        return PathContext(emptySet(), emptySet(), emptyMap(), true)

    // maybeUninitialized contains symbols which are uninitialized along any reachable path
    val maybeUninitialized = reachable.flatMap { it.maybeUninitialized }.toSet()

    // uninitialised is the symbols which are uninitialized along all reachable paths
    val uninitialised = reachable.first().uninitialized.filter{ sym-> reachable.all {it.uninitialized.contains(sym)}}.toSet()

    // For refined types, only keep types that are consistent across all reachable paths
    val refinedTypes = reachable.first().refinedTypes.filter { (symbol, type) ->
        reachable.all { it.refinedTypes[symbol] == type }
    }

    return PathContext(uninitialised, maybeUninitialized, refinedTypes, false)
}