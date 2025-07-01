`timescale 1ns / 1ps

module blit_merge(
    input  logic clock,
    input  logic reset,

    input logic [25:0]  p5_addr,
    input logic [7:0]   p5_data,
    input logic         p5_write,
    input               p5_idle,

    output logic [25:0] p6_addr,
    output logic [31:0] p6_data,
    output logic [3:0]  p6_byte_enable,
    output logic        p6_write
); 

logic [25:0] next_p6_addr;
logic [31:0] next_p6_data;
logic [3:0]  next_p6_byte_enable;

always_comb begin
    next_p6_addr = p6_addr;
    next_p6_data = p6_data;
    next_p6_byte_enable = p6_byte_enable;
    p6_write = 1'b0;

    // flush the accumulator if we're idle, or if a write targets a different address
    if (p5_write && p5_addr[25:2] != p6_addr[25:2]) begin
        p6_write = p6_byte_enable != 0;
        next_p6_addr = {p5_addr[25:2], 2'b0};
        next_p6_data = 32'bx;
        next_p6_byte_enable = 4'b0;
    end else if (p5_idle) begin
        p6_write = p6_byte_enable != 0;
        next_p6_addr = 0;
        next_p6_data = 32'bx;
        next_p6_byte_enable = 4'b0;
    end

    if (p5_write) begin
        case (p5_addr[1:0])
            2'b00: begin
                next_p6_data[7:0] = p5_data;
                next_p6_byte_enable[0] = 1'b1;
            end
            2'b01: begin
                next_p6_data[15:8] = p5_data;
                next_p6_byte_enable[1] = 1'b1;
            end
            2'b10: begin
                next_p6_data[23:16] = p5_data;
                next_p6_byte_enable[2] = 1'b1;
            end
            2'b11: begin
                next_p6_data[31:24] = p5_data;
                next_p6_byte_enable[3] = 1'b1;
            end
        endcase
    end

    if (reset) begin
        next_p6_addr = 0;
        next_p6_data = 32'bx;
        next_p6_byte_enable = 0;
        p6_write = 0;
    end
end

always_ff @(posedge clock) begin
    p6_addr <= next_p6_addr;
    p6_data <= next_p6_data;
    p6_byte_enable <= next_p6_byte_enable;
end

endmodule