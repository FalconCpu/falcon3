`timescale 1ns/1ps

module cpu_ifetch(
    input logic clock,
    input logic reset,
    input logic stall,                    // Pipeline stalled. 

    // CPU Instruction Bus
    output logic        cpui_request,     // CPU requests a bus transaction. Asserts for one cycle.
    output logic [31:0] cpui_addr,        // Address of instruction to read
    input  logic [31:0] cpui_rdata,       // Instruction read from memory
    input  logic        cpui_ack,         // Memory has responded to the request.

    // Connections to the CPU Decoder
    output logic [31:0] p2_instr,         // instruction to be decoded
    output logic [31:0] p2_pc,            // PC of instruction
    output logic        p2_instr_valid,   // Instruction is valid
    input  logic        p2_bubble,        // Decoder is not ready to accept an instruction

    input  logic [31:0] p3_jump_addr,     // Jump address
    input  logic        p3_jump_taken     // Jump is valid
);

logic [31:0] pc, pc_q;                     // PC
logic        in_progress, in_progress_q;   // Do we have a transaction in progress?
logic [31:0] cpui_addr_q;                  // address of transaction in progress
logic        prev_stall;                   // Was pipeline stalled last cycle?
logic [31:0] p2_instr_q;                   // Instruction we outputted last cycle
logic        p2_instr_valid_q;             // Was the instruction we outputted last cycle valid?
logic [31:0] p2_pc_q;                      // PC of instruction in decoder
logic        invalidate, invalidate_q;     // The instruction being fetched has been invalidated

logic        skid_valid, skid_valid_q;     // Are we skidding an instruction?
logic [31:0] skid_instr, skid_instr_q;     // Instruction we are skidding
logic [31:0] skid_pc, skid_pc_q;           // PC of instruction we are skidding

logic        error;


always_comb begin
    error = 0;

    // Check for jumps
    if (p3_jump_taken && !stall) begin
        pc = p3_jump_addr;                 // Update the PC to the jump address
        invalidate = in_progress;          // Kill the instruction we were about to fetch
    end else begin
        pc = pc_q;                         // The increment happens later
        invalidate = invalidate_q;
    end


    // receive an instruction from the memory
    if (cpui_ack)
        in_progress = 0;
    else
        in_progress = in_progress_q;

    if (cpui_ack && !invalidate) begin
        if (prev_stall) begin                 
            // Pipeline is not ready to accept an instruction - so we need to skid the rx'd instruction
            error          = skid_valid;
            p2_instr       = p2_instr_q;
            p2_pc          = p2_pc_q;
            p2_instr_valid = p2_instr_valid_q;
            skid_instr     = cpui_rdata;
            skid_pc        = cpui_addr_q;
            skid_valid     = 1;
        end else if (skid_valid) begin        
            // We have a skid instruction - so we need to output it
            p2_instr       = skid_instr;
            p2_pc          = skid_pc;
            p2_instr_valid = 1;
            skid_instr     = cpui_rdata;
            skid_pc        = cpui_addr_q;
            skid_valid     = 1;
        end else begin
            p2_instr       = cpui_rdata;
            p2_pc          = cpui_addr_q;
            p2_instr_valid = 1;
            skid_instr     = 32'hx;
            skid_pc        = 32'hx;
            skid_valid     = 0;
        end
    end else begin
        if (prev_stall) begin
            // Pipeline is not ready to accept an instruction - so hold the current instruction
            p2_instr       = p2_instr_q;
            p2_pc          = p2_pc_q;
            p2_instr_valid = p2_instr_valid_q;
            skid_instr     = skid_instr_q;
            skid_pc        = skid_pc_q;
            skid_valid     = skid_valid_q;
        end else if (skid_valid) begin
            // We have a skid instruction - so we need to output it
            p2_instr       = skid_instr;
            p2_pc          = skid_pc;
            p2_instr_valid = 1;
            skid_instr     = 32'hx;
            skid_pc        = 32'hx;
            skid_valid     = 0;
        end else begin
            p2_instr       = 32'hx;
            p2_pc          = 32'hx;
            p2_instr_valid = 0;
            skid_instr     = 32'hx;
            skid_pc        = 32'hx;
            skid_valid     = 0;
        end
    end

    // Request the next instruction
    if (in_progress) begin                               // Transaction still in progress
        cpui_request    = 0;
        cpui_addr       = cpui_addr_q;
    end else if (stall || p2_bubble || skid_valid) begin // Wait for stall to clear
        cpui_request    = 0;
        cpui_addr       = 32'hx;
    end else begin                                       // Request the next instruction
        cpui_request    = 1;
        cpui_addr       = pc;
        pc              = pc + 4;
        invalidate      = 0;
        in_progress     = 1;
    end


    // reset
    if (reset) begin
        pc           = 32'hffff0000;
        in_progress  = 0;
        skid_valid   = 0;
        cpui_request = 0;
    end
end

always_ff @(posedge clock) begin
    pc_q             <= pc;
    in_progress_q    <= in_progress;
    cpui_addr_q      <= cpui_addr;
    p2_instr_q       <= p2_instr;
    p2_instr_valid_q <= p2_instr_valid;
    p2_pc_q          <= p2_pc;
    skid_instr_q     <= skid_instr;
    skid_pc_q        <= skid_pc;
    skid_valid_q     <= skid_valid;
    prev_stall       <= stall ||  p2_bubble;
    invalidate_q     <= invalidate;
end

always @(posedge clock) begin
    if (error)
        $display("ERROR %t: skid buffer overflow", $time);
end


endmodule