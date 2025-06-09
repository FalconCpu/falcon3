`timescale 1ns / 1ps
`include "cpu.vh"

module cpu_execute(
    input logic clock,
    input logic reset,
    input logic stall,

    input logic [5:0]  p3_op,           // ALU operation to perform
    input logic [7:0]  p3_opx,          // ALU operation specific parameters
    input logic [31:0] p3_data_a,       // ALU input A
    input logic [31:0] p3_data_b,       // ALU input B

    output logic [31:0] p3_data_out,    // ALU output
    output logic        p3_jump_taken,  // ALU has taken a jump
    output logic [31:0] p3_jump_addr,   // ALU jump address
    output logic [31:0] p4_data_out
);

wire [4:0] shift_amount = p3_data_b[4:0];


always_comb begin
    // default assignments
    p3_data_out = 32'bx;
    p3_jump_addr = 32'bx;
    p3_jump_taken = 0;


    case(p3_op) 
        `OP_AND:    p3_data_out = p3_data_a & p3_data_b;
        `OP_OR:     p3_data_out = p3_data_a | p3_data_b;
        `OP_XOR:    p3_data_out = p3_data_a ^ p3_data_b;
        `OP_SHIFT:  p3_data_out = p3_data_a << shift_amount;   // Add other shift operations here
        `OP_ADD:    p3_data_out = p3_data_a + p3_data_b;
        `OP_SUB:    p3_data_out = p3_data_a - p3_data_b;
        `OP_CLT:    p3_data_out = $signed(p3_data_a) < $signed(p3_data_b);
        `OP_CLTU:   p3_data_out = $unsigned(p3_data_a) < $unsigned(p3_data_b);
        `OP_BEQ:    begin end
        `OP_BNE:    begin end
        `OP_BLT:    begin end
        `OP_BGE:    begin end
        `OP_BLTU:    begin end
        `OP_BGEU:    begin end
        `OP_JMP:    begin end
        `OP_LDB:    begin end
        `OP_LDH:    begin end
        `OP_LDW:    begin end
        `OP_LDBU:    begin end
        `OP_LDHU:    begin end
        `OP_STB:    begin end
        `OP_STH:    begin end
        `OP_STW:    begin end
        `OP_MUL:    begin end
        `OP_DIVU:    begin end
        `OP_DIVS:    begin end
        `OP_MODU:    begin end
        `OP_MODS:    begin end
        `OP_CFGR:    begin end
        `OP_CFGW:    begin end
        `OP_RTE:    begin end
        `OP_SYS:    begin end
        default:    begin end
    endcase
end


always_ff @(posedge clock) begin
    if(!stall) begin
        p4_data_out <= p3_data_out;
    end
end

endmodule