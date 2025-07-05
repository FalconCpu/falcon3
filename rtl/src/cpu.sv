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
logic [31:0] p3_pc;
logic [31:0] p4_pc;
logic [31:0] p4_instr;

// Signals from the datamux unit
logic [31:0] p3_data_a;
logic [31:0] p3_data_b;
logic [31:0] p3_literal;

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
logic [5:0]  p4_op;
logic        p4_illegal_ins;

// Signals from the Execute stage
logic [31:0] p3_alu_out;
logic        p3_jump_taken;
logic [31:0] p3_jump_addr;
logic [31:0] p4_alu_out;
logic [31:0] p4_mult;
logic        p3_misaligned_address;
logic        p3_overflow;
logic        p4_misaligned_address;
logic        p3_request;
logic        p3_write;
logic [31:0] p3_addr;
logic [31:0] p3_wdata;
logic  [3:0] p3_byte_enable;
logic  [1:0] p3_size;

// Signals from the memory unit
logic        p4_read_pending;
logic        p4_write_pending;
logic [31:0] p4_mem_rdata;
logic [31:0] p4_mem_addr;
logic        p4_load_access_fault;
logic        p4_store_access_fault;

// Signals from the exception unit
logic [31:0] p4_csr_out;
logic        p4_jump_taken;
logic [31:0] p4_jump_addr;

// Signals from the completion unit
logic [31:0] p4_data_out;
logic stall;

// Signals from Memory Protection Unit
logic [31:0] csr_dmpu0;
logic [31:0] csr_dmpu1;
logic [31:0] csr_dmpu2;
logic [31:0] csr_dmpu3;
logic [31:0] csr_dmpu4;
logic [31:0] csr_dmpu5;
logic [31:0] csr_dmpu6;
logic [31:0] csr_dmpu7;
logic        supervisor;
logic        p3_access_deny;


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
    .p4_jump_addr(p4_jump_addr),
    .p4_jump_taken(p4_jump_taken),
    .p3_pc(p3_pc),
    .p4_pc(p4_pc),
    .p4_instr(p4_instr)
  );

cpu_datamux  cpu_datamux_inst (
    .clock(clock),
    .stall(stall),
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
    .p3_alu_out(p3_alu_out),
    .p3_literal(p3_literal),
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
    .p4_jump_taken(p4_jump_taken),
    .p4_op(p4_op),
    .p4_illegal_ins(p4_illegal_ins)
  );
  
cpu_execute  cpu_execute_inst (
    .clock(clock),
    .reset(reset),
    .stall(stall),
    .p3_op(p3_op),
    .p3_opx(p3_opx),
    .p3_data_a(p3_data_a),
    .p3_data_b(p3_data_b),
    .p2_pc(p2_pc),
    .p3_literal(p3_literal),
    .p4_jump_taken(p4_jump_taken),
    .p3_request(p3_request),
    .p3_addr(p3_addr),
    .p3_write(p3_write),
    .p3_byte_enable(p3_byte_enable),
    .p3_wdata(p3_wdata),
    .p3_size(p3_size),
    .p3_alu_out(p3_alu_out),
    .p3_jump_taken(p3_jump_taken),
    .p3_jump_addr(p3_jump_addr),
    .p4_alu_out(p4_alu_out),
    .p3_misaligned_address(p3_misaligned_address),
    .p3_overflow(p3_overflow),
    .p4_mult(p4_mult)
  );

wire divider_start = (p3_op[5:2]==4'b1001) && !stall && !p4_jump_taken;
wire [31:0] p4_quotient; 
wire [31:0] p4_remainder;
wire        p4_divider_done;
cpu_divider  cpu_divider_inst (
    .clock(clock),
    .start(divider_start),
    .data_a(p3_data_a),
    .data_b(p3_data_b),
    .signed_div(p3_op[0]),
    .quotient(p4_quotient),
    .remainder(p4_remainder),
    .done(p4_divider_done)
  );

cpu_memif  cpu_memif_inst (
    .clock(clock),
    .reset(reset),
    .stall(stall),
    .cpud_request(cpud_request),
    .cpud_addr(cpud_addr),
    .cpud_write(cpud_write),
    .cpud_byte_enable(cpud_byte_enable),
    .cpud_wdata(cpud_wdata),
    .cpud_rdata(cpud_rdata),
    .cpud_ack(cpud_ack),
    .p3_request(p3_request),
    .p3_addr(p3_addr),
    .p3_write(p3_write),
    .p3_byte_enable(p3_byte_enable),
    .p3_wdata(p3_wdata),
    .p3_size(p3_size),
    .p3_misaligned_address(p3_misaligned_address),
    .p3_access_deny(p3_access_deny),
    .p4_mem_addr(p4_mem_addr),
    .p4_write_pending(p4_write_pending),
    .p4_read_pending(p4_read_pending),
    .p4_mem_rdata(p4_mem_rdata),
    .p4_misaligned_address(p4_misaligned_address),
    .p4_load_access_fault(p4_load_access_fault),
    .p4_store_access_fault(p4_store_access_fault)
  );

cpu_exception  cpu_exception_inst (
    .clock(clock),
    .reset(reset),
    .stall(stall),
    .p3_op(p3_op),
    .p4_op(p4_op),
    .p3_literal(p3_literal[12:0]),
    .p3_data_a(p3_data_a),
    .p4_csr_out(p4_csr_out),
    .p4_illegal_ins(p4_illegal_ins),
    .p3_jump_taken(p3_jump_taken),
    .p3_jump_addr(p3_jump_addr),
    .p4_jump_taken(p4_jump_taken),
    .p4_jump_addr(p4_jump_addr),
    .p4_misaligned_address(p4_misaligned_address),
    .p4_load_access_fault(p4_load_access_fault),
    .p4_store_access_fault(p4_store_access_fault),
    .p3_overflow(p3_overflow),
    .supervisor(supervisor),
    .csr_dmpu0(csr_dmpu0),
    .csr_dmpu1(csr_dmpu1),
    .csr_dmpu2(csr_dmpu2),
    .csr_dmpu3(csr_dmpu3),
    .csr_dmpu4(csr_dmpu4),
    .csr_dmpu5(csr_dmpu5),
    .csr_dmpu6(csr_dmpu6),
    .csr_dmpu7(csr_dmpu7),
    .p4_mem_addr(p4_mem_addr),
    .p3_pc(p3_pc),
    .p4_pc(p4_pc),
    .p4_instr(p4_instr)

  );

cpu_completion  cpu_completion_inst (
    .clock(clock),
    .reset(reset),
    .p4_op(p4_op),
    .p4_alu_out(p4_alu_out),
    .p4_write_pending(p4_write_pending),
    .p4_read_pending(p4_read_pending),
    .p4_mem_rdata(p4_mem_rdata),
    .p4_data_out(p4_data_out),
    .p4_mult(p4_mult),
    .p4_quotient(p4_quotient),
    .p4_remainder(p4_remainder),
    .p4_csr_out(p4_csr_out),
    .p4_divider_done(p4_divider_done),
    .stall(stall)
  );  

  cpu_mpu  cpu_mpu_inst (
    .clock(clock),
    .reset(reset),
    .supervisor(supervisor),
    .cpud_request(p3_request),
    .cpud_write(p3_write),
    .cpud_addr(p3_addr),
    .csr_dmpu0(csr_dmpu0),
    .csr_dmpu1(csr_dmpu1),
    .csr_dmpu2(csr_dmpu2),
    .csr_dmpu3(csr_dmpu3),
    .csr_dmpu4(csr_dmpu4),
    .csr_dmpu5(csr_dmpu5),
    .csr_dmpu6(csr_dmpu6),
    .csr_dmpu7(csr_dmpu7),
    .access_deny(p3_access_deny)
  );

endmodule