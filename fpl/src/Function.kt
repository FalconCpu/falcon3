val allFunctions = mutableListOf<Function>()

class Function(val name:String, val parameters:List<SymbolVar>, val thisSymbol : SymbolVar?, val returnType:Type) {
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

    fun newVar() : RegVar {
        val new = RegVar("V${regs.size}")
        regs.add(new)
        return new
    }

    fun newLabel() : Label {
        val new = Label("L${labels.size}")
        labels.add(new)
        return new
    }

    fun stackAlloc(size:Int) : Int {
        val sizeRounded = (size + 3) and -4
        val ret = stackVarSize
        stackVarSize += sizeRounded
        return ret
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

    fun addCall(func:Function) : Reg {
        addInstr(InstrCall(func))
        return if (func.returnType!=TypeUnit)
            addMov(regResult)
        else
            allMachineRegs[0]
    }

    fun addCall(func:Function, args:List<Reg>) : Reg {
        for((index,reg) in args.withIndex())
            addInstr(InstrMov(allMachineRegs[index+1], reg))
        addInstr(InstrCall(func))
        return if (func.returnType!=TypeUnit)
            addMov(regResult)
        else
            allMachineRegs[0]
    }


    fun addLoadMem(size:Int, addr:Reg, offset:Int): RegTemp {
        val dest = newTemp()
        addInstr(InstrLoadMem(size, dest, addr, offset))
        return dest
    }

    fun addStoreMem(size:Int, src:Reg, addr:Reg, offset:Int) {
        addInstr(InstrStoreMem(size, src, addr, offset))
    }

    fun addLoadMem(addr:Reg, offset:SymbolField): RegTemp {
        val dest = newTemp()
        addInstr(InstrLoadField(offset.type.sizeInBytes(), dest, addr, offset))
        return dest
    }

    fun addStoreMem(src:Reg, addr:Reg, offset:SymbolField) {
        addInstr(InstrStoreField(offset.type.sizeInBytes(), src, addr, offset))
    }


    fun addIndexOp(scale:Int, src:Reg, limit:Reg) : Reg {
        val op = when(scale) {
            1 -> 0
            2 -> 1
            4 -> 2
            else -> error("Invalid scale $scale")
        }
        require(scale==1 || scale==2 || scale==4)
        val dest = newTemp()
        addInstr(InstrIndex(op, dest, src, limit))
        return dest
    }

}

fun List<Function>.dump() : String {
    val sb = StringBuilder()
    for(func in allFunctions)
        func.dump(sb)
    return sb.toString()
}