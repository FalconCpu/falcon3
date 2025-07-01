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
    input logic         p2_write,
    input logic [1:0]   p2_op,

    // Outputs to the next stage
    output logic [25:0]  p4_addr,
    output logic         p3_write,

    output logic [31:0]  p3_src_addr,
    output logic [2:0]   p3_src_bit_index
);

localparam OP_PEN  = 2'h0;
localparam OP_SRC  = 2'h1;
localparam OP_MONO = 2'b10;

logic [25:0] p3_addr;

always_ff @(posedge clock) begin
    if (!stall) begin
        p3_addr <= (dest_addr + {10'b0,p2_dest_x}) + (p2_dest_y * dest_bpl);
        
        case(p2_op)
            OP_PEN: p3_src_addr <= 32'hx;
            OP_SRC: p3_src_addr <= (src_addr + {16'b0, p2_src_x}) + (p2_src_y * src_bpl);
            OP_MONO: p3_src_addr <=(src_addr + {19'b0, p2_src_x[15:3]}) + (p2_src_y * src_bpl);
            default: p3_src_addr <= 32'hx;
        endcase
        p3_src_bit_index <= p2_src_x[2:0];

        if (p2_dest_x < clip_x1 || p2_dest_x >= clip_x2 || p2_dest_y < clip_y1 || p2_dest_y >= clip_y2)
            p3_write <= 1'b0;
        else
            p3_write <= p2_write;
        p4_addr <= p3_addr;
    end

    if (reset) begin
        p3_write <= 1'b0;
    end
end



endmodule
