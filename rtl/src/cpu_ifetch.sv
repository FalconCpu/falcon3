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

    // Outputs to the exception unit
    output logic [31:0] p3_pc,
    output logic [31:0] p4_pc,
    output logic [31:0] p4_instr,

    input  logic [31:0] p4_jump_addr,     // Jump address
    input  logic        p4_jump_taken     // Jump is valid
);

logic [31:0] pc, next_pc;                   // PC of instruction being fetched
logic        in_progress, next_in_progress; // A fetch is in progress
logic [31:0] p1_pc, next_p1_pc;             // Address of instruction being fetched
logic [31:0] skid_instr, next_skid_instr;   // Skid buffer for instructions
logic [31:0] skid_addr, next_skid_addr;     // Skid buffer for addresses
logic        skid_valid, next_skid_valid;   // Skid buffer for instruction validity
logic [31:0] next_p2_instr;                 // Instruction being decoded
logic [31:0] next_p2_pc;                    // Address of instruction in P2
logic [31:0] p3_instr; 
logic        next_p2_instr_valid;           // Instruction is valid
logic        flushing, next_flushing;       // Pipeline is flushing

always_comb begin
    next_p2_instr = p2_instr;
    next_p2_instr_valid = p2_instr_valid;
    next_skid_instr = skid_instr;
    next_skid_valid = skid_valid;
    next_skid_addr = skid_addr;
    next_flushing = flushing;
    next_p1_pc = p1_pc;
    cpui_request = 1'b0;
    next_p2_pc = p2_pc;

    // Check to see if the request has completed
    next_in_progress = in_progress && !cpui_ack;

    // Calculate the address of the next instruction
    if (p4_jump_taken && !stall) begin
        next_pc = p4_jump_addr;
        next_flushing = next_in_progress;
    end else if (cpui_ack && !flushing)
        next_pc = p1_pc + 4;
    else    
        next_pc = pc;

    // Request a new instruction
    if (!stall && !p2_bubble && !next_in_progress) begin
        cpui_request = 1'b1;
        next_in_progress = 1'b1;
        next_p1_pc = next_pc;
    end
    cpui_addr = next_p1_pc;

    // Move instructions along the pipeline
    if (!stall && !p2_bubble) begin
        // P2 stage is consuming the instruction
        next_p2_instr_valid = 1'b0;
        next_p2_instr = 32'hx;
        next_p2_pc = p1_pc;  // Set the address of the next instruction even if we don't have the contents yet, in case an interrupt occurs.
    end
    if (skid_valid && next_p2_instr_valid==0) begin
        // Move the skid buffer into the P2 stage
        next_p2_instr = skid_instr;
        next_p2_instr_valid = 1'b1;
        next_p2_pc = skid_addr;
        next_skid_instr = 32'hx;
        next_skid_valid = 1'b0;
        next_skid_addr = 32'hx;
    end
    if (cpui_ack) begin
        // Receive the instruction
        if (flushing) begin
            // Discard the instruction
            next_flushing = 1'b0;
        end else if (next_p2_instr_valid) begin
            // P2 slot is already occupied - move it to the skid buffer
            next_skid_instr = cpui_rdata;
            next_skid_valid = 1'b1;
            next_skid_addr  = p1_pc;
        end else begin
            // P2 slot is empty - use it
            next_p2_instr       = cpui_rdata;
            next_p2_pc          = p1_pc;
            next_p2_instr_valid = 1'b1;
        end
    end
    if (p4_jump_taken && !stall) begin
        // Flush the pipeline
        next_p2_instr_valid = 1'b0;
        next_p2_instr = 32'hx;
        next_p2_pc = p4_jump_addr;
        next_skid_valid = 1'b0;
        next_skid_instr = 32'hx;
        next_skid_addr = 32'hx;
    end

    // Handle resets
    if (reset) begin
        next_pc = 32'hFFFF0000;
        next_in_progress = 1'b0;
        next_flushing = 1'b0;
    end
end

always_ff @(posedge clock) begin
    pc <= next_pc;     
    in_progress    <= next_in_progress;
    p2_instr       <= next_p2_instr;
    p2_pc          <= next_p2_pc;
    p2_instr_valid <= next_p2_instr_valid;
    skid_instr     <= next_skid_instr;
    skid_addr      <= next_skid_addr;
    skid_valid     <= next_skid_valid;
    p1_pc          <= next_p1_pc;
    flushing       <= next_flushing;

    if (!stall) begin
        p3_pc <= p2_pc;
        p4_pc <= p3_pc;
        p3_instr <= p2_instr;
        p4_instr <= p3_instr;
    end
end

// synthesis translate_off
always @(posedge clock) begin
    if (p4_jump_taken && !stall && p4_jump_addr==32'h0) begin
        @ (posedge clock);
        @ (posedge clock);
        @ (posedge clock);
        $display("Simulation finished");
        $finish;
    end
end
// synthesis translate_on


endmodule