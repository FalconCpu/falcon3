
// OP values for EXECUTE and MEM stages
`define  OP_AND    6'b000_000
`define  OP_OR     6'b000_001
`define  OP_XOR    6'b000_010
`define  OP_SHIFT  6'b000_011
`define  OP_ADD    6'b000_100
`define  OP_SUB    6'b000_101
`define  OP_CLT    6'b000_110
`define  OP_CLTU   6'b000_111
`define  OP_BEQ    6'b001_000
`define  OP_BNE    6'b001_001
`define  OP_BLT    6'b001_010
`define  OP_BGE    6'b001_011
`define  OP_BLTU   6'b001_100
`define  OP_BGEU   6'b001_101
`define  OP_JMP    6'b001_110
`define  OP_JMPR   6'b001_111
`define  OP_LDB    6'b010_000
`define  OP_LDH    6'b010_001
`define  OP_LDW    6'b010_010
`define  OP_LDBU   6'b010_100
`define  OP_LDHU   6'b010_101
`define  OP_STB    6'b011_000
`define  OP_STH    6'b011_001
`define  OP_STW    6'b011_010
`define  OP_MUL    6'b100_000
`define  OP_DIVU   6'b100_100
`define  OP_DIVS   6'b100_101
`define  OP_MODU   6'b100_110
`define  OP_MODS   6'b100_111
`define  OP_CFGR   6'b101_000
`define  OP_CFGW   6'b101_001
`define  OP_RTE    6'b101_010
`define  OP_SYS    6'b101_011
`define  OP_LD     6'b110_000


// KIND values for decode
`define KIND_ALU   6'b010000
`define KIND_ALU_I 6'b010001
`define KIND_LOAD  6'b010010
`define KIND_STORE 6'b010011
`define KIND_BRA   6'b010100
`define KIND_JMP   6'b010101
`define KIND_JMPR  6'b010110
`define KIND_LDI   6'b010111
`define KIND_LDPC  6'b011000
`define KIND_MUL   6'b011001
`define KIND_MUL_I 6'b011010
`define KIND_CFG   6'b011011
`define KIND_IDX   6'b011100








