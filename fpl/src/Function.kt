val allFunctions = mutableListOf<Function>()

class Function(val name:String, val parameters:List<SymbolVar>, val returnType:Type) {
    val code = mutableListOf<Instr>()
    val regs = allMachineRegs.toMutableList<Reg>()
    val labels = mutableListOf<Label>()

    val allTempRegs = mutableListOf<RegTemp>()
    val allVars = mutableMapOf<Symbol, RegVar>()
    val retLabel = newLabel()
    val regAssignComments = mutableListOf<String>()
    var maxRegister = 0
    var stackVarSize = 0

    override fun toString(): String = name

    fun addInstr(instr:Instr) {
        code.add(instr)
    }

    fun newTemp() : RegTemp {
        val new = RegTemp("T${allTempRegs.size}")
        allTempRegs.add(new)
        regs.add(new)
        return new
    }

    fun newLabel() : Label {
        val new = Label("L${labels.size}")
        labels.add(new)
        return new
    }

    fun dump(sb: StringBuilder) {
        sb.append("Function $name\n")
        for(instr in code)
            sb.append("$instr\n")
        sb.append("\n")
    }

    fun getVar(sym:Symbol) : RegVar {
        val ret = allVars[sym]
        if (ret != null)
            return ret
        val new = RegVar(sym.name)
        allVars[sym] = new
        regs.add(new)
        return new
    }

    // Some utility functions to add code
    fun addAlu(op:AluOp, lhs:Reg, rhs:Reg) : RegTemp {
        val dest = newTemp()
        addInstr(InstrAlu(op, dest, lhs, rhs))
        return dest
    }

    fun addAlu(op:AluOp, lhs:Reg, rhs:Int) : RegTemp {
        val dest = newTemp()
        addInstr(InstrAluImm(op, dest, lhs, rhs))
        return dest
    }

    fun addMov(dest:Reg, src:Reg) {
        addInstr(InstrMov(dest, src))
    }

    fun addMov(src:Reg) : Reg {
        val dest = newTemp()
        addInstr(InstrMov(dest, src))
        return dest
    }

    fun addMov(src:Int) : Reg {
        val dest = newTemp()
        addInstr(InstrMovImm(dest, src))
        return dest
    }

    fun addLea(value:Value) : Reg {
        val dest = newTemp()
        addInstr(InstrLea(dest, value))
        return dest
    }

    fun addLabel(label:Label) {
        addInstr(InstrLabel(label))
    }

    fun addJump(label:Label) {
        addInstr(InstrJump(label))
    }

    fun addBranch(op:AluOp, lhs:Reg, rhs:Reg, label:Label) {
        addInstr(InstrBranch(op, lhs, rhs, label))
    }

    fun addCall(func:Function) {
        addInstr(InstrCall(func))
    }
}

fun List<Function>.dump() : String {
    val sb = StringBuilder()
    for(func in allFunctions)
        func.dump(sb)
    return sb.toString()
}