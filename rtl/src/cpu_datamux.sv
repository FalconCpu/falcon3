`timescale 1ns/1ps

module cpu_datamux(
    input logic clock,

    input logic [4:0]  p2_reg_a,
    input logic [4:0]  p2_reg_b,
    input logic        p2_bypass_3_a,
    input logic        p2_bypass_3_b,
    input logic        p2_bypass_4_a,
    input logic        p2_bypass_4_b,
    input logic        p2_literal_b,
    input logic [4:0]  p4_reg_d,
    input logic        p4_write_en,
    output logic [31:0] p3_data_a,
    output logic [31:0] p3_data_b,

    input logic [31:0] p2_literal_value,
    input logic [31:0] p3_data_out,
    input logic [31:0] p4_data_out
);

logic [31:0] p2_reg_data_a;
logic [31:0] p2_reg_data_b;

cpu_regfile  cpu_regfile_inst (
    .clock(clock),
    .p2_reg_a(p2_reg_a),
    .p2_reg_b(p2_reg_b),
    .p2_reg_data_a(p2_reg_data_a),
    .p2_reg_data_b(p2_reg_data_b),
    .p4_reg_d(p4_reg_d),
    .p4_write_en(p4_write_en),
    .p4_reg_data_d(p4_data_out)
  );

always_ff @(posedge clock) begin
    if (p2_bypass_4_a)
        p3_data_a <= p4_data_out;
    else if (p2_bypass_3_a)
        p3_data_a <= p3_data_out;
    else 
        p3_data_a <= p2_reg_data_a;

    if (p2_literal_b)
        p3_data_b <= p2_literal_value;
    else if (p2_bypass_4_b)
        p3_data_b <= p4_data_out;
    else if (p2_bypass_3_b)
        p3_data_b <= p3_data_out;   
    else
        p3_data_b <= p2_reg_data_b;
end
endmodule