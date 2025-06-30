`timescale 1ns / 1ps

module blit_data_select(
    input logic clock,
    input logic stall,

    input logic [7:0]  p4_data,
    input logic [7:0]  p4_src_data,
    input logic [1:0]  p4_op,
    input logic        p4_write,

    output logic [7:0] p5_data,
    output logic       p5_write
);

always_ff @(posedge clock) begin
    if (!stall) begin
        if (p4_op == 2'b01)
            p5_data <= p4_src_data;
        else
            p5_data <= p4_data[7:0];
        p5_write <= p4_write;
    end
end

endmodule