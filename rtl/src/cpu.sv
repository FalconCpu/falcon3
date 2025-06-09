`timescale 1ns / 1ps


module cpu(
    input logic   clock,
    input logic   reset,

    // CPU Data Bus
    output logic        cpud_request,     // CPU requests a bus transaction. Asserts for one cycle.
    output logic [31:0] cpud_addr,        // Address of data to read/write
    output logic        cpud_write,       // 1 = write, 0 = read
    output logic [3:0]  cpud_byte_enable, // For a write, which bytes to write.
    output logic [31:0] cpud_wdata,       // Data to write
    input  logic [31:0] cpud_rdata,       // Data read from memory
    input  logic        cpud_ack,         // Memory has responded to the request.

    // CPU Instruction Bus
    output logic        cpui_request,     // CPU requests a bus transaction. Asserts for one cycle.
    output logic [31:0] cpui_addr,        // Address of instruction to read
    input  logic [31:0] cpui_rdata,       // Instruction read from memory
    input  logic        cpui_ack          // Memory has responded to the request.
);


// Signals from the ifetch unit
logic [31:0] p2_instr;
logic [31:0] p2_pc;
logic        p2_instr_valid;

// Signals from the datamux unit
logic [31:0] p3_data_a;
logic [31:0] p3_data_b;

// Signals from the decoder
logic        p2_bubble;
logic [4:0]  p2_reg_a;
logic [4:0]  p2_reg_b;
logic        p2_bypass_3_a;
logic        p2_bypass_3_b;
logic        p2_bypass_4_a;
logic        p2_bypass_4_b;
logic        p2_literal_b;
logic [4:0]  p4_reg_d;
logic        p4_write_en;
logic [31:0] p2_literal_value;
logic [5:0]  p3_op;
logic [7:0]  p3_opx;
logic [31:0] p4_op;

// Signals from the Execute stage
logic [31:0] p3_data_out;
logic        p3_jump_taken;
logic [31:0] p3_jump_addr;
logic [31:0] p4_data_out;

// dummy for now
logic        stall = 0;

cpu_ifetch  cpu_ifetch_inst (
    .clock(clock),
    .reset(reset),
    .stall(stall),
    .cpui_request(cpui_request),
    .cpui_addr(cpui_addr),
    .cpui_rdata(cpui_rdata),
    .cpui_ack(cpui_ack),
    .p2_instr(p2_instr),
    .p2_pc(p2_pc),
    .p2_instr_valid(p2_instr_valid),
    .p2_bubble(p2_bubble),
    .p3_jump_addr(p3_jump_addr),
    .p3_jump_taken(p3_jump_taken)
  );

cpu_datamux  cpu_datamux_inst (
    .clock(clock),
    .p2_reg_a(p2_reg_a),
    .p2_reg_b(p2_reg_b),
    .p2_bypass_3_a(p2_bypass_3_a),
    .p2_bypass_3_b(p2_bypass_3_b),
    .p2_bypass_4_a(p2_bypass_4_a),
    .p2_bypass_4_b(p2_bypass_4_b),
    .p2_literal_b(p2_literal_b),
    .p4_reg_d(p4_reg_d),
    .p4_write_en(p4_write_en),
    .p3_data_a(p3_data_a),
    .p3_data_b(p3_data_b),
    .p2_literal_value(p2_literal_value),
    .p3_data_out(p3_data_out),
    .p4_data_out(p4_data_out)
  );

cpu_decode  cpu_decode_inst (
    .clock(clock),
    .reset(reset),
    .stall(stall),
    .p2_instr(p2_instr),
    .p2_pc(p2_pc),
    .p2_instr_valid(p2_instr_valid),
    .p2_bubble(p2_bubble),
    .p2_reg_a(p2_reg_a),
    .p2_reg_b(p2_reg_b),
    .p2_bypass_3_a(p2_bypass_3_a),
    .p2_bypass_3_b(p2_bypass_3_b),
    .p2_bypass_4_a(p2_bypass_4_a),
    .p2_bypass_4_b(p2_bypass_4_b),
    .p2_literal_b(p2_literal_b),
    .p4_reg_d(p4_reg_d),
    .p4_write_en(p4_write_en),
    .p2_literal_value(p2_literal_value),
    .p3_op(p3_op),
    .p3_opx(p3_opx),
    .p3_jump_taken(p3_jump_taken),
    .p4_op(p4_op)
  );
  
cpu_execute  cpu_execute_inst (
    .clock(clock),
    .reset(reset),
    .stall(stall),
    .p3_op(p3_op),
    .p3_opx(p3_opx),
    .p3_data_a(p3_data_a),
    .p3_data_b(p3_data_b),
    .p3_data_out(p3_data_out),
    .p3_jump_taken(p3_jump_taken),
    .p3_jump_addr(p3_jump_addr),
    .p4_data_out(p4_data_out)
  );


endmodule