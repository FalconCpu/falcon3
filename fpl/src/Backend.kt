const val UNDEFINED = -1

fun Function.rebuildIndex() {
    // Clear all indices

    for (label in labels) {
        label.index = UNDEFINED
        label.useCount = 0
    }

    for ((index,v)  in regs.withIndex()) {
        v.index = index
        v.useCount = 0
        v.def.clear()
    }

    // And rebuild the indexes
    code.removeIf { it is InstrNop }
    for ((index, instr) in code.withIndex()) {
        instr.index = index
        when (instr) {
            is InstrLabel -> instr.label.index = index
            is InstrJump -> instr.label.useCount++
            is InstrBranch -> instr.label.useCount++
            else -> {}
        }

        // Check for definitions of temps
        val def = instr.getDef()
        if (def is RegTemp && def.def.isNotEmpty())
            error("Reg $def is not SSA")
        def?.def += instr

        // Check for uses
        instr.getUse().forEach { it.useCount++ }
    }
}

private fun Reg.getSmallInt(depth:Int = 0) : Int? {
    if (depth > 10)
        return null     // Too deep
    if (this.def.size != 1)
        return null  // Only track through SSA definitions
    val def = def.first()

    if (def is InstrMovImm && def.src in -0xfff..0xfff)
        return def.src
    else if (def is InstrMov)
        return def.src.getSmallInt(depth+1)
    return null
}

private fun Function.peephole() : Boolean {

    // returns true if any changes were made
    var changed = false

    fun remove(index:Int) {
        if (debug)
            println("Removing instruction $index ${code[index]}")
        code[index] = InstrNop()
        changed = true
    }

    fun replace(index:Int, instr:Instr) {
        if (debug)
            println("Replacing instruction $index with $instr")
        code[index] = instr
        changed = true
    }

    for (instr in code) {
        when (instr) {
            is InstrMov -> {
                if (instr.dest.useCount==0 && instr.dest !is RegMachine)
                    remove(instr.index)
                else if (instr.src==instr.dest)
                    remove(instr.index)
            }

            is InstrMovImm -> {
                if (instr.dest.useCount==0 && instr.dest !is RegMachine)
                    remove(instr.index)
            }

            is InstrAlu -> {
                val lhsImm = instr.lhs.getSmallInt()
                val rhsImm = instr.rhs.getSmallInt()
                if (instr.dest.useCount==0 && instr.dest !is RegMachine)
                    remove(instr.index)
                else if (rhsImm!=null)
                    replace(instr.index, InstrAluImm(instr.op, instr.dest, instr.lhs, rhsImm))
                else if (lhsImm!=null && instr.op.isCommutative())
                    replace(instr.index, InstrAluImm(instr.op, instr.dest, instr.rhs, lhsImm))
            }

            is InstrAluImm -> {
                val lhsImm = instr.lhs.getSmallInt()
                if (instr.dest.useCount==0 && instr.dest !is RegMachine)
                    remove(instr.index)
                else if (lhsImm!=null)
                    replace(instr.index, InstrMovImm(instr.dest, instr.op.evaluate(lhsImm,instr.rhs)))
            }

            is InstrJump -> {
                if (instr.label.index == instr.index + 1)
                    remove(instr.index)
            }

            is InstrLabel -> {
                if (instr.label.useCount == 0)
                    remove(instr.index)
            }

            else -> {}
        }
    }

    return changed

}

fun Function.runBackend() {
    // Run the peephole optimizer until it stops changing anything
    do {
        rebuildIndex()
    } while (peephole())


    val livemap = Livemap(this)
    RegisterAllocator(this, livemap).run()

    // Run the peephole optimizer again
    do {
        rebuildIndex()
    } while (peephole())

}

fun List<Function>.runBackend() {
    for (func in this)
        func.runBackend()
}