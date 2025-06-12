`timescale 1ns/1ns
`include "cpu.vh"

module cpu_exception (
    input  logic clock,
    input  logic reset,
    input  logic stall,

    input  logic [5:0]  p3_op,           // ALU operation to perform
    input  logic [12:0]  p3_literal,      // ALU literal value = CSR register number
    input  logic [31:0] p3_data_a,       // ALU input A
    output logic [31:0] p4_csr_out,
    input  logic [5:0]  p4_op,

    // exception sources
    input logic [31:0]  p3_pc,
    input logic [31:0]  p4_pc,
    input logic [31:0]  cpud_addr,          // Address of data read/write (P3)
    input logic [31:0]  p4_instr,
    input logic         p4_illegal_ins,
    input logic         p4_misaligned_addr,

    input  logic        p3_jump_taken,
    input  logic [31:0] p3_jump_addr,
    output logic        p4_jump_taken,
    output logic [31:0] p4_jump_addr
);

logic [31:0] csr_epc,      next_csr_epc;
logic  [7:0] csr_ecause,   next_csr_ecause;
logic [31:0] csr_edata,    next_csr_edata;
logic  [7:0] csr_estatus,  next_csr_estatus;
logic [31:0] csr_escratch, next_csr_escratch;
logic  [7:0] csr_status,   next_csr_status;
logic [31:0] csr_ipc,      next_csr_ipc;
logic  [7:0] csr_icause,   next_csr_icause;
logic  [7:0] csr_istatus,  next_csr_istatus;
logic [31:0] csr_intvec,   next_csr_intvec;
logic [31:0] csr_timer,    next_csr_timer;
logic        interupt_pending, next_interupt_pending;

logic [31:0] p3_csr_out;
logic [31:0] p4_jump_taken_q;
logic [31:0] p4_jump_addr_q;
logic [31:0] p4_cpud_addr;
logic [12:0] p4_literal;
logic        raise_exception;
logic        raise_interupt;
logic [31:0] next_instruction;

always_comb begin
    // Default assignments
    p3_csr_out = 32'hx;
    next_csr_ecause = csr_ecause;
    next_csr_edata = csr_edata;
    next_csr_estatus = csr_estatus;
    next_csr_epc = csr_epc;
    next_csr_escratch = csr_escratch;
    next_csr_status = csr_status;
    next_csr_ipc = csr_ipc;
    next_csr_icause = csr_icause;
    next_csr_istatus = csr_istatus;
    next_csr_intvec  = csr_intvec;
    next_interupt_pending = interupt_pending;

    next_csr_timer = csr_timer - 1;     // countdown

    p4_jump_taken = p4_jump_taken_q;
    p4_jump_addr = p4_jump_addr_q;

    // Handle writing to CSR regs
    if (p3_op == `OP_CSRW) begin
        case (p3_literal)
            `CSR_EPC:      next_csr_epc      = p3_data_a;
            `CSR_ECAUSE:   next_csr_ecause   = p3_data_a[7:0];
            `CSR_EDATA:    next_csr_edata    = p3_data_a;
            `CSR_ESTATUS:  next_csr_estatus  = p3_data_a[7:0];
            `CSR_ESCRATCH: next_csr_escratch = p3_data_a;
            `CSR_STATUS:   next_csr_status   = p3_data_a[7:0];
            `CSR_IPC:      next_csr_ipc      = p3_data_a;
            `CSR_ICAUSE:   next_csr_icause   = p3_data_a[7:0];
            `CSR_ISTATUS:  next_csr_istatus  = p3_data_a[7:0];
            `CSR_INTVEC:   next_csr_intvec   = p3_data_a;
            `CSR_TIMER:    if (!stall) next_csr_timer    = p3_data_a;
        endcase
    end

    if (p3_op==`OP_CSRW || p3_op==`OP_CSRR) begin
        case (p3_literal)
            `CSR_EPC:      p3_csr_out = csr_epc;
            `CSR_ECAUSE:   p3_csr_out = {24'h0, csr_ecause};
            `CSR_EDATA:    p3_csr_out = csr_edata;
            `CSR_ESTATUS:  p3_csr_out = {24'h0, csr_estatus};
            `CSR_ESCRATCH: p3_csr_out = csr_escratch;
            `CSR_STATUS:   p3_csr_out = {24'h0, csr_status};
            `CSR_IPC:      p3_csr_out = csr_ipc;
            `CSR_ICAUSE:   p3_csr_out = {24'h0, csr_icause};
            `CSR_ISTATUS:  p3_csr_out = {24'h0, csr_istatus};
            `CSR_INTVEC:   p3_csr_out = csr_intvec;
            `CSR_TIMER:    p3_csr_out = csr_timer;
        endcase
    end

    // Handle exceptions 
    // Exceptions are always thrown at the P4 stage in the pipeline
    raise_exception = 1'b0;
    raise_interupt = 1'b0;

    if (p4_illegal_ins) begin
        next_csr_ecause  = `CAUSE_ILLEGAL_INSTRUCTION;
        next_csr_edata   = p4_instr;
        raise_exception  = 1'b1;

    end else if (p4_misaligned_addr) begin
        next_csr_ecause  = p4_op[3] ? `CAUSE_STORE_ADDRESS_MISALIGNED : `CAUSE_LOAD_ADDRESS_MISALIGNED;
        next_csr_edata   = p4_cpud_addr;
        raise_exception  = 1'b1;

    end else if (p4_op==`OP_RTE) begin
        // p4_literal[0] = 0 for RTE, 1 for RTI
        p4_jump_taken    = 1'b1;
        p4_jump_addr     = p4_literal[0] ? csr_ipc     : csr_epc;
        next_csr_status  = p4_literal[0] ? csr_istatus : csr_estatus;
    
    end else if (p4_op==`OP_SYS) begin
        next_csr_ecause  = `CAUSE_SYSTEM_CALL;
        next_csr_edata   = p4_literal;
        raise_exception  = 1'b1;
    end


    if (interupt_pending) begin
        raise_interupt = 1'b1;
        if (!stall)
            next_interupt_pending = 1'b0;
    end



    if (raise_exception) begin
        next_csr_epc     = p4_pc;
        p4_jump_taken    = 1'b1;
        p4_jump_addr     = `EXCEPTION_VECTOR;
        next_csr_estatus = csr_status;
        next_csr_status  = csr_status | `FLAG_SUPERVISOR;
        next_instruction = `EXCEPTION_VECTOR;
    end
    if (raise_interupt) begin
        next_csr_ipc     = raise_exception ? `EXCEPTION_VECTOR : 
                           p4_jump_taken   ? p4_jump_addr 
                                           : p3_pc;
        p4_jump_taken    = 1'b1;
        p4_jump_addr     = csr_intvec;
        next_csr_istatus = next_csr_status;
        next_csr_status  = csr_status | `FLAG_SUPERVISOR | `FLAG_INTERRUPT;
    end


    // Interupt sources
    if (csr_timer==0)
        interupt_pending = 1'b1;


    if (reset) begin
        next_csr_epc      = 32'h0;
        next_csr_ecause   = 8'h0;
        next_csr_edata    = 32'h0;
        next_csr_estatus  = 8'h0;
        next_csr_escratch = 32'h0;
        next_csr_status   = 8'h0;
        next_csr_ipc      = 32'h0;
        next_csr_intvec   = 32'hffff0008;
        next_csr_timer    = 32'hffffffff;
        next_interupt_pending = 1'b0;
    end
end

always_ff @(posedge clock) begin
    interupt_pending <= next_interupt_pending;
    csr_timer <= next_csr_timer;
    if (!stall) begin
        csr_epc      <= next_csr_epc;
        csr_ecause   <= next_csr_ecause;
        csr_edata    <= next_csr_edata;
        csr_estatus  <= next_csr_estatus;
        csr_escratch <= next_csr_escratch;
        csr_status   <= next_csr_status;
        csr_ipc      <= next_csr_ipc;
        csr_icause   <= next_csr_icause;
        csr_istatus  <= next_csr_istatus;
        csr_intvec   <= next_csr_intvec;
        p4_csr_out   <= p3_csr_out;
        p4_jump_taken_q<= p3_jump_taken;
        p4_jump_addr_q <= p3_jump_addr;
        p4_literal   <= p3_literal;
        p4_cpud_addr <= cpud_addr;
    end
end

endmodule