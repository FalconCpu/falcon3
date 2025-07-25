`timescale 1ns / 1ps
`include "cpu.vh"

module cpu_completion(
    input logic clock,
    input logic reset,

    input [5:0]        p4_op,
    input logic [31:0] p4_alu_out,
    input logic        p4_write_pending,
    input logic        p4_read_pending, 
    input logic [31:0] p4_mem_rdata,
    input logic [31:0] p4_quotient,
    input logic [31:0] p4_remainder,
    input logic        p4_divider_done,
    input logic [31:0] p4_csr_out,

    output logic [31:0] p4_data_out,
    input  logic [31:0] p4_mult,
    output logic stall
);

always_comb begin
    stall = 0;
    p4_data_out = 32'bx;

    case (p4_op) 
        `OP_AND,
        `OP_OR,
        `OP_XOR,
        `OP_SHIFT,
        `OP_ADD,
        `OP_SUB,
        `OP_CLT,
        `OP_CLTU,
        `OP_BEQ,
        `OP_BNE,
        `OP_BLT,
        `OP_BGE,
        `OP_BLTU,
        `OP_BGEU,
        `OP_JMP,
        `OP_JMPR,
        `OP_LD,
        `OP_LDPC,
        `OP_IDX1,
        `OP_IDX2,
        `OP_IDX4:
            p4_data_out = p4_alu_out;

        `OP_LDB,
        `OP_LDH,
        `OP_LDW,
        `OP_LDBU,
        `OP_LDHU: begin
            p4_data_out = p4_mem_rdata;
            stall = p4_read_pending;
        end
    
        `OP_STB,
        `OP_STH,
        `OP_STW:
            stall = p4_write_pending;
        
        `OP_MUL: p4_data_out = p4_mult;
        `OP_DIVU,
        `OP_DIVS: begin
            p4_data_out = p4_quotient;
            stall = !p4_divider_done;
        end
        `OP_MODU,
        `OP_MODS: begin
            p4_data_out = p4_remainder;
            stall = !p4_divider_done;
        end
        `OP_CSRR,
        `OP_CSRW: p4_data_out = p4_csr_out;

        `OP_RTE,
        `OP_SYS: begin 
            p4_data_out = 32'bx;
        end

        default: begin
            p4_data_out = 32'bx;
        end
    endcase

end

endmodule

