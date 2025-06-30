`timescale 1ns / 1ps

module blit_write_fifo(
    input  logic        clock,
    input  logic        reset,

    input  logic        in_write,
    input  logic [25:0] in_addr,
    input  logic [31:0] in_data,
    input  logic [3:0]  in_byte_enable,

    output logic        out_req,
    output logic [25:0] out_addr,
    output logic [31:0] out_data,
    output logic [3:0]  out_byte_enable,
    input  logic        out_ack,
    output logic        fifo_full
);

logic [25:0] addr_fifo   [255:0];
logic [31:0] data_fifo   [255:0];
logic [3:0]  be_fifo     [255:0];

logic [7:0] rd_ptr, wr_ptr;
wire [7:0] next_rd_ptr = rd_ptr + {7'b0, out_ack};
wire [7:0] slots_free = rd_ptr - wr_ptr - 1;
assign fifo_full = slots_free < 8'd8;


always_ff @(posedge clock) begin
    // Push
    if (in_write) begin
        addr_fifo[wr_ptr] <= in_addr;
        data_fifo[wr_ptr] <= in_data;
        be_fifo[wr_ptr]   <= in_byte_enable;
        wr_ptr <= wr_ptr + 1;
    end

    // Pop
    out_req <= next_rd_ptr != wr_ptr;
    out_addr <= addr_fifo[next_rd_ptr];
    out_data <= data_fifo[next_rd_ptr];
    out_byte_enable <= be_fifo[next_rd_ptr];
    rd_ptr <= next_rd_ptr;

    if (reset) begin
        rd_ptr <= 0;
        wr_ptr <= 0;
    end
end

endmodule
