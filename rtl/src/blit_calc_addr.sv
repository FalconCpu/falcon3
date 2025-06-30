`timescale 1ns/1ns

module blit_calc_addr(
    input logic clock,
    input logic stall,
    input logic reset,

    // Signals from the command processor
    input  logic [25:0] dest_addr,
    input  logic [15:0] dest_bpl,
    input  logic [31:0] src_addr,
    input  logic [15:0] src_bpl,
    input  logic [15:0] clip_x1,
    input  logic [15:0] clip_y1,
    input  logic [15:0] clip_x2,
    input  logic [15:0] clip_y2,
    input logic [15:0]  p2_dest_x,
    input logic [15:0]  p2_dest_y,
    input logic [15:0]  p2_src_x,
    input logic [15:0]  p2_src_y,
    input logic [15:0]  p2_color,
    input logic         p2_write,

    // Outputs to the next stage
    output logic [25:0]  p5_addr,
    output logic [15:0]  p4_data,
    output logic         p3_write,

    output logic [31:0]  p3_src_addr
);

logic [25:0] p3_addr, p4_addr;
logic [15:0] p3_data;

always_ff @(posedge clock) begin
    if (!stall) begin
        p3_addr <= (dest_addr + {10'b0,p2_dest_x}) + (p2_dest_y * dest_bpl);
        p3_src_addr <= (src_addr + {16'b0, p2_src_x}) + (p2_src_y * src_bpl);
        if (p2_dest_x < clip_x1 || p2_dest_x >= clip_x2 || p2_dest_y < clip_y1 || p2_dest_y >= clip_y2)
            p3_write <= 1'b0;
        else
            p3_write <= p2_write;
        p3_data <= p2_color;
        p4_data <= p3_data;
        p4_addr <= p3_addr;
        p5_addr <= p4_addr;
    end

    if (reset) begin
        p3_write <= 1'b0;
    end
end



endmodule
