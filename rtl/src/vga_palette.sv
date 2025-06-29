`timescale 1ns / 1ps

module vga_palette(
    input logic clk,

    input logic [7:0] in_code, 
    input             in_valid,
    output logic [23:0] out_color,
    output logic        out_valid
);

logic [23:0] palette[0:255];

integer i;
initial begin
    for(i=0; i<256; i=i+1) begin
        palette[i] = {i[7:5], i[7:5], i[7:6], i[4:2], i[4:2], i[4:3], i[1:0], i[1:0], i[1:0], i[1:0]};
    end
end

always_ff @(posedge clk) begin
    if (in_valid) begin
        out_color <= palette[in_code];
        out_valid <= 1'b1;
    end else begin
        out_valid <= 1'b0;
        out_color <= 24'h0;
    end
end

endmodule
