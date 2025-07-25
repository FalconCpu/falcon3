val allFunctions = mutableListOf<Function>()

class Function(val name:String, val parameters:List<SymbolVar>, val thisSymbol : SymbolVar?, val isVararg:Boolean, val returnType:Type, val extern:Boolean=false) {
    val code = mutableListOf<Instr>()
    val regs = allMachineRegs.toMutableList<Reg>()
    val labels = mutableListOf<Label>()

    val allTempRegs = mutableListOf<RegTemp>()
    val allVars = mutableMapOf<Symbol, Reg>()
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

    fun newUnion() : RegUnion {
        return RegUnion("£UNION", newTemp(), newTemp())
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

    fun getVar(sym:Symbol) : Reg {
        val ret = allVars[sym]
        if (ret != null)
            return ret
        val new : Reg
        if (sym.type is TypeErrable) {
            val valReg = RegVar("${sym.name}.value")
            val typeReg = RegVar("${sym.name}.type")
            regs.add(valReg)
            regs.add(typeReg)
            new = RegUnion(sym.name, typeReg, valReg)
        } else {
            new = RegVar(sym.name)
            regs.add(new)
        }
        allVars[sym] = new
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
        if (src is RegUnion && dest is RegUnion) {
            addInstr(InstrMov(dest.typeIndex, src.typeIndex))
            addInstr(InstrMov(dest.value, src.value))
        } else if (src !is RegUnion && dest !is RegUnion)
            addInstr(InstrMov(dest, src))
        else
            error("Internal error: Mixing Union type to non-union")
    }

    fun addMov(src:Reg) : Reg {
        if (src is RegUnion) {
            val dest = newUnion()
            addInstr(InstrMov(dest.typeIndex, src.typeIndex))
            addInstr(InstrMov(dest.value, src.value))
            return dest
        } else {
            val dest = newTemp()
            addInstr(InstrMov(dest, src))
            return dest
        }
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

    fun addCall(func:FunctionInstance) : Reg = addCall(func.function)

    fun addCall(func:FunctionInstance, args: List<Reg>) = addCall(func.function, args)


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

    fun addLoadGlobal(global:SymbolGlobal): RegTemp {
        val dest = newTemp()
        addInstr(InstrLoadGlobal(dest, global))
        return dest
    }

    fun addStoreGlobal(src:Reg, global:SymbolGlobal) {
        addInstr(InstrStoreGlobal(src, global))
    }

    fun addSystemCall(syscall:Int) {
        addInstr(InstrSyscall(syscall))
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