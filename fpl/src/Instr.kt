class Instr {
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

