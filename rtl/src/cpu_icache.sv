`timescale 1ns/1ps

module cpu_icache(
    input logic clock,
    input logic reset,

    // CPU Instruction Bus
    input  logic        cpui_request,     // CPU requests a bus transaction. Asserts for one cycle.
    input  logic [31:0] cpui_addr,        // Address of instruction to read
    output logic [31:0] cpui_rdata,       // Instruction read from memory
    output logic        cpui_ack          // Memory has responded to the request.
);


logic [31:0] mem [0:16383];               // 64KB of memory
logic [31:0] mem_q;

initial
    $readmemh("asm.hex", mem);

assign cpui_rdata = cpui_ack ? mem_q : 32'bx;

always_ff @(posedge clock) begin
    mem_q    <= mem[cpui_addr[15:2]];
    cpui_ack <= cpui_request;
end

endmodule
