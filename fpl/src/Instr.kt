// =========================================================
// The Instr class models the instructions in the code
// =========================================================

sealed class Instr {
    var index = 0

    override fun toString(): String = when(this) {
        is InstrAlu -> "$op $dest, $lhs, $rhs"
        is InstrAluImm -> "$op $dest, $lhs, $rhs"
        is InstrBranch -> "${op.getBranchOp()} $lhs, $rhs, $label"
        is InstrJump -> "jmp $label"
        is InstrLabel -> "$label:"
        is InstrLoadMem -> "${size.loadOp()} $dest, $addr[$offset]"
        is InstrStoreMem -> "${size.storeOp()} $src, $addr[$offset]"
        is InstrMov -> "ld $dest, $src"
        is InstrMovImm -> "ld $dest, $src"
        is InstrNop -> "nop"
        is InstrRet -> "ret"
        is InstrStart -> "start"
        is InstrCall -> "jsr $func"
        is InstrCallIndirect -> "jsr $func"
        is InstrLea -> "ld $dest, $value"
    }

    fun getDef() : Reg? = when(this) {
        is InstrAlu -> dest
        is InstrAluImm -> dest
        is InstrBranch -> null
        is InstrJump -> null
        is InstrLabel -> null
        is InstrLoadMem -> dest
        is InstrStoreMem -> null
        is InstrMov -> dest
        is InstrMovImm -> dest
        is InstrNop -> null
        is InstrRet -> null
        is InstrStart -> null
        is InstrCall -> if (func.returnType==TypeUnit) null else regResult
        is InstrCallIndirect -> if (signature.returnType==TypeUnit) null else regResult
        is InstrLea -> dest
    }

    fun getUse() : List<Reg> = when(this) {
        is InstrAlu -> listOf(lhs, rhs)
        is InstrAluImm -> listOf(lhs)
        is InstrBranch -> listOf(lhs, rhs)
        is InstrJump -> emptyList()
        is InstrLabel -> emptyList()
        is InstrLoadMem -> listOf(addr)
        is InstrStoreMem -> listOf(src, addr)
        is InstrMov -> listOf(src)
        is InstrMovImm -> emptyList()
        is InstrNop -> emptyList()
        is InstrRet -> emptyList()
        is InstrStart -> emptyList()
        is InstrCall -> allMachineRegs.subList(1, 1+func.parameters.size)
        is InstrCallIndirect -> allMachineRegs.subList(1, 1+signature.parameters.size) + func
        is InstrLea -> emptyList()
    }
}

class InstrNop() : Instr()
class InstrAlu(val op:AluOp, val dest:Reg, val lhs:Reg, val rhs:Reg) : Instr()
class InstrAluImm(val op:AluOp, val dest:Reg, val lhs:Reg, val rhs:Int) : Instr()
class InstrMov(val dest:Reg, val src:Reg) : Instr()
class InstrMovImm(val dest:Reg, val src:Int) : Instr()
class InstrLabel(val label:Label) : Instr()
class InstrJump(val label:Label) : Instr()
class InstrBranch(val op:AluOp, val lhs:Reg, val rhs:Reg, val label:Label) : Instr()
class InstrCall(val func:Function) : Instr()
class InstrCallIndirect(val func:Reg, val signature:TypeFunction) : Instr()
class InstrRet() : Instr()
class InstrStart(): Instr()
class InstrLoadMem(val size:Int, val dest:Reg, val addr:Reg, val offset:Int) : Instr()
class InstrStoreMem(val size:Int, val src:Reg, val addr:Reg, val offset:Int) : Instr()
class InstrLea(val dest:Reg, val value:Value) : Instr()

fun Int.loadOp() = when(this) {
    1 -> "ldb"
    2 -> "ldh"
    4 -> "ldw"
    else -> error("Invalid size for load")
}

fun Int.storeOp() = when(this) {
    1 -> "stb"
    2 -> "sth"
    4 -> "stw"
    else -> error("Invalid size for store")
}

fun AluOp.getBranchOp() = when(this) {
    AluOp.EQ_I  -> "beq"
    AluOp.NEQ_I -> "bne"
    AluOp.LT_I  -> "blt"
    AluOp.LTE_I -> "ble"
    AluOp.GT_I  -> "bgt"
    AluOp.GTE_I -> "bge"
    else -> error("Invalid branch op")
}



// =========================================================
//                   Reg
// =========================================================

sealed class Reg(val name:String) {
    var index:Int = -1
    var useCount = 0
    val def = mutableListOf<Instr>()
    override fun toString(): String = name
}

class RegVar(name:String) : Reg(name)           // Represents a user visible symbol
class RegTemp(name:String) : Reg(name)          // Temporary register
class RegMachine(name:String) : Reg(name)       // A machine register

val allMachineRegs = (0..31).map {
    when(it) {
        0 -> RegMachine("0")
        31 -> RegMachine("SP")
        else -> RegMachine("R$it")
    }
}

val regSP = allMachineRegs[31]
val regZero = allMachineRegs[0]
val regResult = allMachineRegs[8]


// =========================================================
//                    Labels
// =========================================================

class Label(val name:String) {
    var index = 0
    var useCount = 0
    override fun toString(): String = name
}

// =========================================================
//                    Alu Operations
// =========================================================
// This is a list of all the alu type operations. It also includes some operations that are not actually performed
// by stdlib functions, but can be treated as if they were cpu operations in the early stages of compilation.

enum class AluOp {
    // Integer Operations
    ADD_I,
    SUB_I,
    MUL_I,
    DIV_I,
    MOD_I,
    AND_I,
    OR_I,
    XOR_I,
    SHL_I,
    SHR_I,
    EQ_I,
    NEQ_I,
    LT_I,
    GT_I,
    LTE_I,
    GTE_I,

    ADD_R,
    SUB_R,
    MUL_R,
    DIV_R,
    MOD_R,
    EQ_R,
    NEQ_R,
    LT_R,
    GT_R,
    LTE_R,
    GTE_R,

    EQ_S,
    NEQ_S,
    LT_S,
    GT_S,
    LTE_S,
    GTE_S
}

fun AluOp.isCommutative () = when(this) {
    AluOp.ADD_I, AluOp.MUL_I,
    AluOp.AND_I, AluOp.OR_I, AluOp.XOR_I,
    AluOp.EQ_I, AluOp.NEQ_I -> true
    else -> false
}

fun AluOp.evaluate(lhs:Int, rhs:Int) : Int {
    return when (this) {
        AluOp.ADD_I -> lhs + rhs
        AluOp.SUB_I -> lhs - rhs
        AluOp.MUL_I -> lhs * rhs
        AluOp.DIV_I -> lhs / rhs
        AluOp.MOD_I -> lhs % rhs
        AluOp.AND_I -> lhs and rhs
        AluOp.OR_I -> lhs or rhs
        AluOp.XOR_I -> lhs xor rhs
        AluOp.SHL_I -> lhs shl rhs
        AluOp.SHR_I -> lhs shr rhs
        AluOp.EQ_I -> if (lhs == rhs) 1 else 0
        AluOp.NEQ_I -> if (lhs != rhs) 1 else 0
        AluOp.LT_I -> if (lhs < rhs) 1 else 0
        AluOp.GT_I -> if (lhs > rhs) 1 else 0
        AluOp.LTE_I -> if (lhs <= rhs) 1 else 0
        AluOp.GTE_I -> if (lhs >= rhs) 1 else 0
        else -> error("Invalid alu op")
    }
}
