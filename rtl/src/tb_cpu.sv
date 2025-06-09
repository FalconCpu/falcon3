`timescale 1ns/1ps

module cpu_tb;

logic clock;
logic reset;
logic cpud_request;
logic [31:0] cpud_addr;
logic cpud_write;
logic [3:0] cpud_byte_enable;
logic [31:0] cpud_wdata;
logic [31:0] cpud_rdata;
logic cpud_ack;
logic cpui_request;
logic [31:0] cpui_addr;
logic [31:0] cpui_rdata;
logic cpui_ack;

cpu  cpu_inst (
    .clock(clock),
    .reset(reset),
    .cpud_request(cpud_request),
    .cpud_addr(cpud_addr),
    .cpud_write(cpud_write),
    .cpud_byte_enable(cpud_byte_enable),
    .cpud_wdata(cpud_wdata),
    .cpud_rdata(cpud_rdata),
    .cpud_ack(cpud_ack),
    .cpui_request(cpui_request),
    .cpui_addr(cpui_addr),
    .cpui_rdata(cpui_rdata),
    .cpui_ack(cpui_ack)
);

cpu_icache  cpu_icache_inst (
    .clock(clock),
    .reset(reset),
    .cpui_request(cpui_request),
    .cpui_addr(cpui_addr),
    .cpui_rdata(cpui_rdata),
    .cpui_ack(cpui_ack)
  );

  
cpu_dcache  cpu_dcache_inst (
    .clock(clock),
    .reset(reset),
    .cpud_request(cpud_request),
    .cpud_addr(cpud_addr),
    .cpud_write(cpud_write),
    .cpud_byte_enable(cpud_byte_enable),
    .cpud_wdata(cpud_wdata),
    .cpud_rdata(cpud_rdata),
    .cpud_ack(cpud_ack)
  );

always begin
    clock = 1'b0;
    #5;
    clock = 1'b1;
    #5;
end

initial begin
    $dumpvars();
    reset = 1'b1;
    #10;
    reset = 1'b0;
    #1000;
    $finish;
end


endmodule