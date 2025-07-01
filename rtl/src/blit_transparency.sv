`timescale 1ns/1ns

module blit_transparency(
    input  logic clock,

    input logic [25:0]  p4_addr,
    input logic [7:0]   p4_data,
    input logic         p4_write,
    input               p4_idle,
    input logic [8:0]   transparent_color,

    output logic [25:0]  p5_addr,
    output logic [7:0]   p5_data,
    output logic         p5_write,
    output logic         p5_idle
);

always_ff @(posedge clock) begin
    p5_addr <= p4_addr;
    p5_data <= p4_data;
    p5_write <= p4_write && ({1'b0,p4_data} != transparent_color);
    p5_idle <= p4_idle;
end

endmodule