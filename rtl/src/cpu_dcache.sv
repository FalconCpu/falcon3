`timescale 1ns/1ps

// Eventually this will be a cache - but for now its just a pass-through to the SDRAM controller

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
    output logic        cpud_ack,          // Memory has responded to the request.

    // SDRAM interface
    output logic        dcache_sdram_request, // CPU requests SDRAM access. Asserts until SDRAM responds with ack
    output logic [25:0] dcache_sdram_addr,    // Address of data to read/write
    output logic        dcache_sdram_write,   // 1 = write, 0 = read
    output logic [3:0]  dcache_sdram_byte_enable, // which bytes to write
    output logic [31:0] dcache_sdram_wdata,   // Data to write
    input  logic        dcache_sdram_ack,     // Acknowledge from SDRAM
    input  logic [31:0] dcache_sdram_rdata,   // Data read from SDRAM
    input  logic        dcache_sdram_rdvalid  // Data read complete
);


// We send the ack to the CPU immediately for a write, 
assign cpud_ack = (dcache_sdram_ack && dcache_sdram_write) || dcache_sdram_rdvalid;
assign cpud_rdata = dcache_sdram_rdvalid ? dcache_sdram_rdata : 32'b0;

always_ff @(posedge clock) begin
    if (dcache_sdram_ack) begin
        if (!dcache_sdram_request)
            $display("[%t] CPU: CPU D-Cache: Error: CPU D-Cache received an ack from SDRAM but CPU did not request SDRAM.", $time);
        dcache_sdram_request = 0;
    end

    if (cpud_request) begin
        if (dcache_sdram_request) 
            $display("[%t] CPU: CPU D-Cache: Error: CPU tried to request SDRAM while SDRAM was already requested.", $time);
        dcache_sdram_request = 1;
        dcache_sdram_addr <= cpud_addr[25:0];
        dcache_sdram_write <= cpud_write;
        dcache_sdram_byte_enable <= cpud_byte_enable;
        dcache_sdram_wdata <= cpud_wdata;
    end


    if (reset) begin
        dcache_sdram_request = 0;
    end
end
endmodule