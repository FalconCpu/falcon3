`timescale 1ns / 1ps

module cpu_regfile(
    input  logic clock,

    input  logic  [4:0] p2_reg_a,
    input  logic  [4:0] p2_reg_b,
    output logic [31:0] p2_reg_data_a,
    output logic [31:0] p2_reg_data_b,

    input logic [4:0]   p4_reg_d,
    input logic         p4_write_en,
    input logic [31:0]  p4_reg_data_d
);    

reg [31:0] regfile [0:31];

initial begin
    regfile[0] = 32'b0;
end

assign p2_reg_data_a = regfile[p2_reg_a];
assign p2_reg_data_b = regfile[p2_reg_b];

always @(posedge clock) begin
    if (p4_write_en && p4_reg_d != 0) begin
        regfile[p4_reg_d] <= p4_reg_data_d;
        $display("$%2d = %08x", p4_reg_d, p4_reg_data_d);
    end
end

endmodule
