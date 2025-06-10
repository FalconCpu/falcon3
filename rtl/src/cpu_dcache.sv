`timescale 1ns/1ps

module cpu_dcache(
    input logic clock,
    input logic reset,

    // CPU Data Bus
    input  logic        cpud_request,     // CPU requests a bus transaction. Asserts for one cycle.
    input  logic [31:0] cpud_addr,        // Address of data to read/write
    input  logic        cpud_write,       // 1 = write, 0 = read
    input  logic [3:0]  cpud_byte_enable, // For a write, which bytes to write.
    input  logic [31:0] cpud_wdata,       // Data to write
    output logic [31:0] cpud_rdata,       // Data read from memory
    output logic        cpud_ack          // Memory has responded to the request.
);

reg [7:0] mem0 [0:16383];               // 64KB of memory
reg [7:0] mem1 [0:16383];               
reg [7:0] mem2 [0:16383];               
reg [7:0] mem3 [0:16383];               
reg [31:0] mem_q;

assign cpud_rdata = cpud_ack ? mem_q : 32'hx;

always @(posedge clock) begin
    if (cpud_request && cpud_write) begin
        if (cpud_byte_enable[0])
            mem0[cpud_addr[15:2]] = cpud_wdata[7:0];
        if (cpud_byte_enable[1])
            mem1[cpud_addr[15:2]] = cpud_wdata[15:8];
        if (cpud_byte_enable[2])
            mem2[cpud_addr[15:2]]= cpud_wdata[23:16];
        if (cpud_byte_enable[3])
            mem3[cpud_addr[15:2]] = cpud_wdata[31:24];
        $display("[%08x] = %08x", cpud_addr, cpud_wdata);
    end
    mem_q <= {mem3[cpud_addr[15:2]],mem2[cpud_addr[15:2]],mem1[cpud_addr[15:2]],mem0[cpud_addr[15:2]]};
    cpud_ack <= cpud_request;
end



endmodule