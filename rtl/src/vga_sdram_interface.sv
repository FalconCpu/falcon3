`timescale 1ns / 1ps    

module vga_sdram_interface(
    input logic clk,
    input logic reset,

    // Connections to the vga pixel pipeline
    input logic [25:0]  pixel_addr,
    input logic         pixel_addr_valid,
    output logic [7:0]  pixel_data,
    output logic        pixel_data_valid,
    output logic        stall,

    // Connections to the SDRAM controller
    output logic        vga_sdram_req,
    output logic [25:0] vga_sdram_addr,
    input  logic        vga_sdram_ack,
    input  logic [31:0] vga_sdram_rdata,
    input  logic        vga_sdram_rdvalid,
    input  logic        vga_sdram_complete
);

reg [25:6] cache_addr;            // Address of the SDRAM cache line
reg        cache_valid;           // Cache line is valid
reg [3:0]  cache_wptr;            // Write pointer for cache
reg [31:0] cache_data[0:15];      // 64 bytes of data
reg [31:0] cache_rdata;           // Data read from cache 
reg [1:0]  cache_addr_lsb;        // Cache address in bytes
reg        prev_stall;            // Previous stall signal

wire cache_hit = cache_valid && cache_addr == pixel_addr[25:6];
assign stall = pixel_addr_valid && !cache_hit;
assign pixel_data = cache_rdata[cache_addr_lsb*8 +: 8];

always_ff @(posedge clk) begin
    if (reset || vga_sdram_ack) begin
        vga_sdram_req <= 1'b0;
        vga_sdram_addr <= 26'hx;
    end else if (pixel_addr_valid && !cache_hit && !prev_stall) begin
        vga_sdram_req <= 1'b1;
        vga_sdram_addr <= {pixel_addr[25:6], 6'b0};  // Align to 64 bytes
        cache_addr <= pixel_addr[25:6];
        cache_valid <= 1'b0;
        cache_wptr <= 4'h0;
    end

    if (vga_sdram_rdvalid) begin
        cache_data[cache_wptr] <= vga_sdram_rdata;
        cache_wptr <= cache_wptr + 4'h1;
    end
    
    if (vga_sdram_complete) begin
        cache_valid <= 1'b1;
    end 

    cache_rdata <= cache_data[pixel_addr[5:2]];
    cache_addr_lsb <= pixel_addr[1:0];
    pixel_data_valid <= pixel_addr_valid && cache_hit;
    prev_stall <= stall;

    if (reset) begin
        cache_valid <= 1'b0;
        cache_wptr <= 4'h0;
    end
end


endmodule
        