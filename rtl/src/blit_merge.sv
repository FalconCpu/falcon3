`timescale 1ns / 1ps

module blit_merge(
    input  logic clock,
    input  logic reset,

    input logic [25:0]  p4_addr,
    input logic [7:0]   p4_data,
    input logic         p4_write,
    input               p4_idle,
    input logic [8:0]   transparent_color,

    output logic [25:0] p5_addr,
    output logic [31:0] p5_data,
    output logic [3:0]  p5_byte_enable,
    output logic        p5_write
); 

logic [25:0] next_p4_addr;
logic [31:0] next_p4_data;
logic [3:0]  next_p4_byte_enable;
wire         write = p4_write && ({1'b0,p4_data} != transparent_color);


always_comb begin
    next_p4_addr = p5_addr;
    next_p4_data = p5_data;
    next_p4_byte_enable = p5_byte_enable;
    p5_write = 1'b0;

    // flush the accumulator if we're idle, or if a write targets a different address
    if (write && p4_addr[25:2] != p5_addr[25:2]) begin
        p5_write = p5_byte_enable != 0;
        next_p4_addr = {p4_addr[25:2], 2'b0};
        next_p4_data = 32'bx;
        next_p4_byte_enable = 4'b0;
    end else if (p4_idle) begin
        p5_write = p5_byte_enable != 0;
        next_p4_addr = 0;
        next_p4_data = 32'bx;
        next_p4_byte_enable = 4'b0;
    end

    if (write) begin
        case (p4_addr[1:0])
            2'b00: begin
                next_p4_data[7:0] = p4_data;
                next_p4_byte_enable[0] = 1'b1;
            end
            2'b01: begin
                next_p4_data[15:8] = p4_data;
                next_p4_byte_enable[1] = 1'b1;
            end
            2'b10: begin
                next_p4_data[23:16] = p4_data;
                next_p4_byte_enable[2] = 1'b1;
            end
            2'b11: begin
                next_p4_data[31:24] = p4_data;
                next_p4_byte_enable[3] = 1'b1;
            end
        endcase
    end

    if (reset) begin
        next_p4_addr = 0;
        next_p4_data = 32'bx;
        next_p4_byte_enable = 0;
        p5_write = 0;
    end
end

always_ff @(posedge clock) begin
    p5_addr <= next_p4_addr;
    p5_data <= next_p4_data;
    p5_byte_enable <= next_p4_byte_enable;
end

endmodule