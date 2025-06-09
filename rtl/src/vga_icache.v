`timescale 1ns / 1ps

module vga_icache(
    input             clock,
    input             reset,

    // connections to the dlp
    input     [31:0]  dlp_addr,                 // address to access. Always word aligned.
    input             dlp_req,                  // request a memory access    
    output reg [31:0] dlp_instr,                // instruction data
    output            dlp_valid,                // instruction is valid

    // connections to the sdram controller
    output reg        dlp_sdram_req,       // request a memory access
    output reg [31:0] dlp_sdram_addr,      // address to access
    input             dlp_sdram_ack,       // the memory has acknowledged the request
    input      [31:0] dlp_sdram_data,      // data from the memory
    input             dlp_sdram_valid,     // data is valid
    input             dlp_sdram_complete); // the burst is complete

// Implement a direct mapped cache. 
// cache line size is 32 bytes (to match the sdram burst size)
// 32 cache lines * 32 bytes = 1KB   (=1 BRAM on FPGA)

reg [31:0] cache_ram[0:255];
reg [31:0] cache_tag[0:31];
reg [31:0] cache_valid[0:31];

reg [31:0] this_data;
reg [31:0] this_tag;
reg [31:0] this_req;

reg [31:0] this_addr, next_addr; 

always @(posedge clock) begin
    // Determine the address to access
    


    if (reset) begin


end


endmodule