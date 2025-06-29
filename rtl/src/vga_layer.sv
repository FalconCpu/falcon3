`timescale 1ns/1ps

module vga_layer(
    input logic clk,
    input logic reset,
    input logic stall,

    // connections to hwregs bus (write only)
    input logic        hwregs_layer_select, 
    input logic [2:0]  hwregs_layer_addr,
    input logic [25:0] hwregs_wdata,

    // connections to the vga pixel pipeline
    input               p1_valid,
    input logic [10:0]  p1_x,
    input logic [10:0]  p1_y,
    input logic         p1_end_of_frame,
    input logic         p1_end_of_line,
    output logic [25:0] p2_addr,
    output logic        p2_valid
);
// Registers for the layer
logic [25:0] reg_addr;      // Address of the layer
logic [10:0] reg_x1;        // X coordinate of the top left corner
logic [10:0] reg_y1;        // Y coordinate of the top left corner
logic [10:0] reg_x2;        // X coordinate of the bottom right corner (exclusive)
logic [10:0] reg_y2;        // Y coordinate of the bottom right corner (exclusive)
logic [10:0] reg_bpl;       // Number of bytes per line
logic [1:0]  reg_bpp;       // Bytes per pixel

// Register for the pixel generator
logic [25:0] pixel_next_line;
logic [25:0] pixel_next;

wire in_layer_x = p1_x >= reg_x1 && p1_x < reg_x2;
wire in_layer_y = p1_y >= reg_y1 && p1_y < reg_y2;

always_ff @(posedge clk) begin

    // Write to registers
    if (hwregs_layer_select) begin
        case (hwregs_layer_addr)
            3'h0: reg_addr <= hwregs_wdata[25:0];
            3'h1: reg_x1 <= hwregs_wdata[10:0];
            3'h2: reg_y1 <= hwregs_wdata[10:0];
            3'h3: reg_x2 <= hwregs_wdata[10:0];
            3'h4: reg_y2 <= hwregs_wdata[10:0];
            3'h5: reg_bpl <= hwregs_wdata[10:0];
            3'h6: reg_bpp <= hwregs_wdata[1:0];
            default: ;
        endcase
    end

    // Pixel address logic
    if (p1_end_of_frame) begin
        pixel_next_line <= reg_addr + {15'b0, reg_bpl};
        pixel_next <= reg_addr;

    end else if (stall) begin
        // Do nothing
    
    end else if (!p1_valid) begin
        p2_valid <= 0;
        p2_addr <= 26'hx;

    end else begin
        p2_valid <= 0;
        p2_addr <= pixel_next;
        if (in_layer_x && in_layer_y) begin
            p2_valid <= 1;
            pixel_next <= pixel_next + {24'b0, reg_bpp};
        end 
        if (p1_end_of_line && in_layer_y) begin
            pixel_next <= pixel_next_line;
            pixel_next_line <= pixel_next_line + {15'b0, reg_bpl};
        end
    end

    // Reset
    if (reset) begin
        reg_addr <= 0;
        reg_x1 <= 0;
        reg_y1 <= 0;
        reg_x2 <= 0;
        reg_y2 <= 0;
    end
end

endmodule