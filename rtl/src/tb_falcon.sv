`timescale 1ns / 1ps

module tb_falcon;

  // Parameters

  //Ports
  reg AUD_ADCDAT;
  wire AUD_ADCLRCK;
  wire AUD_BCLK;
  wire AUD_DACDAT;
  wire AUD_DACLRCK;
  wire AUD_XCK;
  reg CLOCK2_50;
  reg CLOCK3_50;
  reg CLOCK4_50;
  reg CLOCK_50;
  wire [12:0] DRAM_ADDR;
  wire [1:0] DRAM_BA;
  wire DRAM_CAS_N;
  wire DRAM_CKE;
  wire DRAM_CLK;
  wire DRAM_CS_N;
  wire [15:0] DRAM_DQ;
  wire DRAM_LDQM;
  wire DRAM_RAS_N;
  wire DRAM_UDQM;
  wire DRAM_WE_N;
  wire FPGA_I2C_SCLK;
  wire FPGA_I2C_SDAT;
  wire [6:0] HEX0;
  wire [6:0] HEX1;
  wire [6:0] HEX2;
  wire [6:0] HEX3;
  wire [6:0] HEX4;
  wire [6:0] HEX5;
  reg [3:0] KEY;
  wire [9:0] LEDR;
  wire PS2_CLK;
  wire PS2_CLK2;
  wire PS2_DAT;
  wire PS2_DAT2;
  reg [9:0] SW;
  wire VGA_BLANK_N;
  wire [7:0] VGA_B;
  wire VGA_CLK;
  wire [7:0] VGA_G;
  wire VGA_HS;
  wire [7:0] VGA_R;
  wire VGA_SYNC_N;
  wire VGA_VS;
  wire [35:0] GPIO_0;
  wire [35:0] GPIO_1;

  Falcon3  Falcon3_inst (
    .AUD_ADCDAT(AUD_ADCDAT),
    .AUD_ADCLRCK(AUD_ADCLRCK),
    .AUD_BCLK(AUD_BCLK),
    .AUD_DACDAT(AUD_DACDAT),
    .AUD_DACLRCK(AUD_DACLRCK),
    .AUD_XCK(AUD_XCK),
    .CLOCK2_50(CLOCK2_50),
    .CLOCK3_50(CLOCK3_50),
    .CLOCK4_50(CLOCK4_50),
    .CLOCK_50(CLOCK_50),
    .DRAM_ADDR(DRAM_ADDR),
    .DRAM_BA(DRAM_BA),
    .DRAM_CAS_N(DRAM_CAS_N),
    .DRAM_CKE(DRAM_CKE),
    .DRAM_CLK(DRAM_CLK),
    .DRAM_CS_N(DRAM_CS_N),
    .DRAM_DQ(DRAM_DQ),
    .DRAM_LDQM(DRAM_LDQM),
    .DRAM_RAS_N(DRAM_RAS_N),
    .DRAM_UDQM(DRAM_UDQM),
    .DRAM_WE_N(DRAM_WE_N),
    .FPGA_I2C_SCLK(FPGA_I2C_SCLK),
    .FPGA_I2C_SDAT(FPGA_I2C_SDAT),
    .HEX0(HEX0),
    .HEX1(HEX1),
    .HEX2(HEX2),
    .HEX3(HEX3),
    .HEX4(HEX4),
    .HEX5(HEX5),
    .KEY(KEY),
    .LEDR(LEDR),
    .PS2_CLK(PS2_CLK),
    .PS2_CLK2(PS2_CLK2),
    .PS2_DAT(PS2_DAT),
    .PS2_DAT2(PS2_DAT2),
    .SW(SW),
    .VGA_BLANK_N(VGA_BLANK_N),
    .VGA_B(VGA_B),
    .VGA_CLK(VGA_CLK),
    .VGA_G(VGA_G),
    .VGA_HS(VGA_HS),
    .VGA_R(VGA_R),
    .VGA_SYNC_N(VGA_SYNC_N),
    .VGA_VS(VGA_VS),
    .GPIO_0(GPIO_0),
    .GPIO_1(GPIO_1)
  );

micron_sdram  micron_sdram_inst (
    .Clk(DRAM_CLK),
    .Cke(DRAM_CKE),
    .Cs_n(DRAM_CS_N),
    .Ras_n(DRAM_RAS_N),
    .Cas_n(DRAM_CAS_N),
    .We_n(DRAM_WE_N),
    .Addr(DRAM_ADDR),
    .Ba(DRAM_BA),
    .Dq(DRAM_DQ),
    .Dqm({DRAM_UDQM, DRAM_LDQM})
  );

initial begin
    $dumpfile("dump.vcd");
    $dumpvars(0, tb_falcon);
    # 300000;
    $finish;
end

logic       uart_clock;
logic       uart_reset;
logic       uart_rx_complete;
logic [7:0] uart_rx_data;
logic       uart_tx_valid;
logic [7:0] uart_tx_data;
logic       uart_tx_complete;

always begin
   uart_clock = 0;
   # 5;
   uart_clock = 1;
   # 5;
end

initial begin
  uart_reset = 1;
  uart_tx_data = 8'h15;
  uart_tx_valid = 0;
  @ (posedge uart_clock);
  @ (posedge uart_clock);
  uart_reset = 0;
  @ (posedge uart_clock);
  @ (posedge uart_clock);
  @ (posedge uart_clock);
  uart_tx_valid = 1;
  @ (posedge uart_clock);
  uart_tx_valid = 0;
end

uart  uart_inst (
    .clock(uart_clock),
    .reset(uart_reset),
    .UART_RX(GPIO_0[0]),
    .UART_TX(GPIO_0[1]),
    .rx_complete(uart_rx_complete),
    .rx_data(uart_rx_data),
    .tx_valid(uart_tx_valid),
    .tx_data(uart_tx_data),
    .tx_complete(uart_tx_complete)
  );

//always #5  clk = ! clk ;

endmodule