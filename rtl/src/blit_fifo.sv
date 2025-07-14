`timescale 1ns / 1ps

module blit_fifo(
    input  logic clock,
    input  logic reset,
    input  logic [127:0] cmd_in,
    input  logic        cmd_in_valid,
    output logic [127:0] cmd_out,
    output logic        cmd_out_valid,
    output logic [7:0]  fifo_slots_free,
    output logic        fifo_overflow,
    input  logic        next_cmd
);

logic [127:0] ram[0:255];
logic [7:0] read_ptr;
logic [7:0] write_ptr;

wire [7:0] next_read_ptr = read_ptr + {7'b0, next_cmd};
wire [7:0] next_write_ptr = write_ptr + {7'b0, cmd_in_valid};

always_ff @(posedge clock) begin
    if (cmd_in_valid) begin
        if (next_write_ptr!=read_ptr) begin
            ram[write_ptr] <= cmd_in;
            write_ptr <= next_write_ptr;
        end else begin
            fifo_overflow <= 1'b1;
        end
    end

    cmd_out <= ram[next_read_ptr];
    read_ptr <= next_read_ptr;
    cmd_out_valid <= next_read_ptr != write_ptr;
    fifo_slots_free <= read_ptr - write_ptr - 1'b1;

    if (reset) begin
        read_ptr <= 0;
        write_ptr <= 0;
        fifo_overflow <= 1'b0;
    end
end


endmodule