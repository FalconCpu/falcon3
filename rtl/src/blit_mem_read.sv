`timescale 1ns / 1ps    

module blit_mem_read(
    input logic clk,
    input logic reset,

    // Connections to the vga pixel pipeline
    input logic [31:0]  p3_src_addr,
    input logic [1:0]   p3_op,
    input logic         p3_write,
    output logic [7:0]  p4_src_data,
    output logic        stall,
    output logic        p4_write,

    // For conversion of monochrome to color
    input logic [2:0]   p3_src_bit_index,
    input logic [7:0]   fg_color,
    input logic [7:0]   bg_color,

    // Connections to the SDRAM controller
    output logic        blitr_sdram_req,
    output logic [25:0] blitr_sdram_addr,
    input  logic        blitr_sdram_ack,
    input  logic [31:0] blitr_sdram_rdata,
    input  logic        blitr_sdram_rdvalid,
    input  logic        blitr_sdram_complete,

    // Connections to the pattern ram
    output logic        blitr_patram_req,
    output logic [15:0] blitr_patram_addr,
    input  logic [31:0] blitr_patram_rdata,
    input  logic        blitr_patram_rdvalid
);

localparam OP_PEN   = 2'h0;
localparam OP_SRC   = 2'h1;
localparam OP_MONO  = 2'h2;

reg [25:6] cache_addr;            // Address of the SDRAM cache line
reg        cache_valid;           // Cache line is valid
reg [3:0]  cache_wptr;            // Write pointer for cache
reg [31:0] cache_data[0:15];      // 64 bytes of data
reg [31:0] cache_rdata;           // Data read from cache 
reg [1:0]  cache_addr_lsb;        // Cache address in bytes
reg        prev_stall;            // Previous stall signal
logic [1:0]p4_op;                
logic [2:0]p4_src_bit_index;

wire do_read = p3_write && p3_op != 2'b00;
wire cache_hit = cache_valid && cache_addr == p3_src_addr[25:6];
wire patram = p3_src_addr[31:24] == 8'hF0;

assign stall = do_read && !cache_hit && !patram && !reset;
assign blitr_patram_req = do_read && patram;
assign blitr_patram_addr = blitr_patram_req ? p3_src_addr[15:0] : 16'bx;
wire [31:0] read_data = blitr_patram_rdvalid ? blitr_patram_rdata : cache_rdata;
wire [7:0]  byte_data = read_data[cache_addr_lsb*8 +: 8];
wire        bit_data = byte_data[p4_src_bit_index];
assign p4_src_data =  (p4_op==OP_PEN)  ? fg_color
                    : (p4_op==OP_SRC)  ? byte_data
                    : (p4_op==OP_MONO) ? (bit_data ? fg_color : bg_color)
                    : 8'hx;

always_ff @(posedge clk) begin

    if (reset || blitr_sdram_ack) begin
        blitr_sdram_req <= 1'b0;
        blitr_sdram_addr <= 26'hx;
    end else if (do_read && !cache_hit && !patram && !prev_stall) begin
        blitr_sdram_req <= 1'b1;
        blitr_sdram_addr <= {p3_src_addr[25:6], 6'b0};  // Align to 64 bytes
        cache_addr <= p3_src_addr[25:6];
        cache_valid <= 1'b0;
        cache_wptr <= 4'h0;
    end

    if (blitr_sdram_rdvalid) begin
        cache_data[cache_wptr] <= blitr_sdram_rdata;
        cache_wptr <= cache_wptr + 4'h1;
    end
    
    if (blitr_sdram_complete) begin
        cache_valid <= 1'b1;
    end 

    cache_rdata <= cache_data[p3_src_addr[5:2]];
    cache_addr_lsb <= p3_src_addr[1:0];
    prev_stall <= stall;

    if (!stall) begin
        p4_write <= p3_write;
        p4_op    <= p3_op;
        p4_src_bit_index <= p3_src_bit_index;
    end

    if (reset) begin
        cache_valid <= 1'b0;
        cache_wptr <= 4'h0;
    end
end


endmodule
        