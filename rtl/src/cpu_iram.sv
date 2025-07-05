`timescale 1ns/1ps

module cpu_iram(
    input logic clock,

    // CPU Instruction Bus
    input  logic        cpui_request,     // CPU requests a bus transaction. Asserts for one cycle.
    input  logic [15:0] cpui_addr,        // Address of instruction to read
    output logic [31:0] cpui_rdata,       // Instruction read from memory
    output logic        cpui_ack,         // Memory has responded to the request.

    // CPU Data Bus
    input  logic        cpud_request,     // CPU requests a bus transaction. Asserts for one cycle.
    input  logic [15:0] cpud_addr,        // Address of data to read/write
    input  logic        cpud_write,       // 1 = write, 0 = read
    input  logic [3:0]  cpud_byte_enable, // For a write, which bytes to write.
    input  logic [31:0] cpud_wdata,       // Data to write
    output logic [31:0] cpud_rdata,       // Data read from memory
    output logic        cpud_ack          // Memory has responded to the request.
);


logic [31:0] mem [0:16383];               // 64KB of memory
logic [31:0] mem_q;

logic cpud_read;
logic [31:0] mem_data_q;
assign cpud_rdata = cpud_read ? mem_data_q : 32'b0;

initial
    $readmemh("asm.hex", mem);

assign cpui_rdata = cpui_ack ? mem_q : 32'bx;

always_ff @(posedge clock) begin
    if (cpud_request && cpud_write) begin
        mem[cpud_addr[15:2]] = cpud_wdata;
        if (cpud_byte_enable!=4'hf)
            $display("Only word access support for instruction ram");
        $display("IRAM: [%x] = %x", cpud_addr, cpud_wdata);
    end
    mem_data_q <= mem[cpud_addr[15:2]];
    cpud_read  <= cpud_request && !cpud_write;
    cpud_ack   <= cpud_request;

    mem_q    <= mem[cpui_addr[15:2]];
    cpui_ack <= cpui_request;
end

endmodule
