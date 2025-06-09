`timescale 1ns / 1ps
`include "cpu.vh"

module cpu_decode(
    input logic clock,
    input logic reset,
    input logic stall,

    // Signals from the IFetch stage
    input  logic [31:0] p2_instr,
    input  logic [31:0] p2_pc,
    input  logic        p2_instr_valid,
    output logic        p2_bubble,

    // Signals to the datamux
    output logic [4:0]  p2_reg_a,
    output logic [4:0]  p2_reg_b,
    output logic        p2_bypass_3_a,
    output logic        p2_bypass_3_b,
    output logic        p2_bypass_4_a,
    output logic        p2_bypass_4_b,
    output logic        p2_literal_b,
    output logic [4:0]  p4_reg_d,
    output logic        p4_write_en,
    output logic [31:0] p2_literal_value,

    // Signals to the ALU
    output logic [5:0]  p3_op,
    output logic [7:0]  p3_opx,
    input  logic        p3_jump_taken,

    // Signals to the MemAccess Stage
    output logic [31:0] p4_op
); 

// Pipeline registers
reg [4:0] p2_reg_d,    p3_reg_d;
reg       p2_write_en, p3_write_en;
reg [5:0] p2_op;

reg       p2_use_a, p2_use_b;

// Instruction Formats:-
// 109876 543 21098 76543 21098765 43210   
// 010000 III DDDDD AAAAA ........ BBBBB   alu3 $d,$a,$b        ALU ops: and/or/xor/shift/add/sub/clt/cltu
// 010001 III DDDDD AAAAA ######## #####   alu3 $d,$a,#s13      ALU literal ops. III has same meaning as ALU
// 010010 ZZZ DDDDD AAAAA ######## #####   ldz  $d,$a[#s13]     Memory loads: ldb/ldh/ldw
// 010011 ZZZ ##### AAAAA ######## BBBBB   stz  $b,$a[#s13d]    Memory stores: stb/sth/stw
// 010100 CCC ##### AAAAA ######## BBBBB   bcc  $a,$b,#s13d     Branch beq/bne/blt/bge/bltu/bgeu   dest=next_pc+4*s13d
// 010101 ### DDDDD ##### ######## #####   jmp  $d, #s21        dest=next_pc+4*s21
// 010110 ... DDDDD AAAAA ######## #####   jmp  $d, $a[#s13]    Jump to address from register + 4*s13
// 010111 ### DDDDD ##### ######## #####   ld   $d, #21<<11     Load upper bits of register with literal value
// 011000 ### DDDDD ##### ######## #####   add  $d, $pc, #21    Calculate pc relative address
// 011001 III DDDDD AAAAA ........ BBBBB   mul  $d, $a, $b      Multiply ops: mul/../../../divs/mods/divu/modu
// 011010 III DDDDD AAAAA ######## #####   mul  $d, $a, #s13    Multiply ops: mul/../../../divs/mods/divu/modu
// 011011 III DDDDD AAAAA ######## #####   cfg  $d, $a, #reg    load config regs
// 011100 III DDDDD AAAAA ........ BBBBB   idx  $d, $a, $b      Index calculation

// Break the instruction into fields
wire  [5:0] ins_kind = p2_instr[31:26];
wire  [2:0] ins_op   = p2_instr[25:23];
wire  [4:0] ins_d    = p2_instr[22:18];
wire        ins_sign = p2_instr[17];
wire  [4:0] ins_a    = p2_instr[17:13];
wire  [7:0] ins_c    = p2_instr[12:5];
wire  [4:0] ins_b    = p2_instr[4:0];


always_comb begin
    // default assignments
    p2_bubble        = 1'b0;
    p2_reg_a         = ins_a;
    p2_reg_b         = ins_b;
    p2_bypass_3_a    = 1'b0;
    p2_bypass_3_b    = 1'b0;
    p2_bypass_4_a    = 1'b0;
    p2_bypass_4_b    = 1'b0;
    p2_literal_b     = 1'b0;
    p2_reg_d         = 1'b0;
    p2_write_en      = 1'b0;
    p2_literal_value = 32'bx;
    p2_use_a         = 1'b0;
    p2_use_b         = 1'b0;
    p2_op            = 6'b0;

    if (reset || p3_jump_taken || !p2_instr_valid) begin
        // Do nothing this cycle  (default assignments)

    end case(ins_kind) 
        `KIND_ALU: begin
            p2_use_a    = 1'b1;
            p2_use_b    = 1'b1;
            p2_op       = {3'b000,  ins_op};
            p2_reg_d    = ins_d;
            p2_write_en = 1'b1;
        end
        
        `KIND_ALU_I: begin
            p2_use_a         = 1'b1;
            p2_literal_b     = 1'b1;
            p2_literal_value = {{19{ins_sign}}, ins_c, ins_b};
            p2_op            = {3'b000, ins_op};
            p2_reg_d         = ins_d;
            p2_write_en      = 1'b1;
        end
    
        default: begin
            // Not yet implemented
        end
    endcase

    // Register bypassing
    if (p2_use_a && p2_reg_a!=5'h0) begin
        if (p2_reg_a == p4_reg_d && p4_write_en)
            p2_bypass_4_a = 1'b1;
        else if (p2_reg_a == p3_reg_d && p3_write_en)
            p2_bypass_3_a = 1'b1;
    end

    if (p2_use_b && p2_reg_b!=5'h0) begin
        if (p2_reg_b == p4_reg_d && p4_write_en)
            p2_bypass_4_b = 1'b1;
        else if (p2_reg_b == p3_reg_d && p3_write_en)
            p2_bypass_3_b = 1'b1;
    end

    // Hazard detection
    // TODO

end

always_ff @(posedge clock) begin
    if (!stall) begin
        p3_reg_d    <= p2_reg_d;
        p3_write_en <= p2_write_en;
        p3_op       <= p2_op;

        p4_reg_d    <= p3_reg_d;
        p4_write_en <= p3_write_en;
        p4_op       <= p3_op;
    end
end



endmodule