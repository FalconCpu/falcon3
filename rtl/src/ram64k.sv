`timescale 1ns/1ps

module ram64k (
	input logic          clock,
	input logic  [13:0]  address_a,
	input logic  [13:0]  address_b,
	input logic  [3:0]   byteena_a,
	input logic  [3:0]   byteena_b,
	input logic  [31:0]  data_a,
	input logic  [31:0]  data_b,
	input logic          wren_a,
	input logic          wren_b,
	output logic  [31:0] q_a,
	output logic  [31:0] q_b
);

logic [3:0][7:0] mem [0:16383];           // 64KB of memory
initial
    $readmemh("font.hex", mem);

always_ff @(posedge clock) begin
    if (wren_a) begin
        if (byteena_a[0])     mem[address_a][0] <= data_a[7:0];
        if (byteena_a[1])     mem[address_a][1] <= data_a[15:8];
        if (byteena_a[2])     mem[address_a][2] <= data_a[23:16];
        if (byteena_a[3])     mem[address_a][3] <= data_a[31:24];
    end
    q_a  <= mem[address_a];

    if (wren_b) begin
        if (byteena_b[0])     mem[address_b][0] <= data_b[7:0];
        if (byteena_b[1])     mem[address_b][1] <= data_b[15:8];
        if (byteena_b[2])     mem[address_b][2] <= data_b[23:16];
        if (byteena_b[3])     mem[address_b][3] <= data_b[31:24];
    end
    q_b  <= mem[address_b];
end

endmodule