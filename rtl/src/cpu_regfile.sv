`timescale 1ns / 1ps

module cpu_regfile(
    input  logic clock,
    input  logic stall,

    input  logic  [4:0] p2_reg_a,
    input  logic  [4:0] p2_reg_b,
    output logic [31:0] p2_reg_data_a,
    output logic [31:0] p2_reg_data_b,

    input logic [4:0]   p4_reg_d,
    input logic         p4_write_en,
    input logic [31:0]  p4_reg_data_d
);    

wire wren = !stall && p4_write_en && p4_reg_d!=0;

regfile_ram  regfile_ram_A (
    .clock(clock),
    .data(p4_reg_data_d),
    .rdaddress(p2_reg_a),
    .wraddress(p4_reg_d),
    .wren(wren),
    .q(p2_reg_data_a)
  );

regfile_ram  regfile_ram_B (
    .clock(clock),
    .data(p4_reg_data_d),
    .rdaddress(p2_reg_b),
    .wraddress(p4_reg_d),
    .wren(wren),
    .q(p2_reg_data_b)
  );


// synthesis translate_off
integer file;

initial begin
    file = $fopen("rtl_reg.log", "w");
end

always @(posedge clock) begin
    if (wren) begin
        $fwrite(file, "$%2d = %08x\n", p4_reg_d, p4_reg_data_d);
    end
end
// synthesis translate_on

endmodule
