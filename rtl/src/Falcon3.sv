`timescale 1ns / 1ps

// verilator lint_off PINCONNECTEMPTY

module Falcon3(

	//////////// Audio //////////
	input 		          		AUD_ADCDAT,
	inout 		          		AUD_ADCLRCK,
	inout 		          		AUD_BCLK,
	output		          		AUD_DACDAT,
	inout 		          		AUD_DACLRCK,
	output		          		AUD_XCK,

	//////////// CLOCK //////////
	input 		          		CLOCK2_50,
	input 		          		CLOCK3_50,
	input 		          		CLOCK4_50,
	input 		          		CLOCK_50,

	//////////// SDRAM //////////
	output		    [12:0]		DRAM_ADDR,
	output		     [1:0]		DRAM_BA,
	output		          		DRAM_CAS_N,
	output		          		DRAM_CKE,
	output		          		DRAM_CLK,
	output		          		DRAM_CS_N,
	inout 		    [15:0]		DRAM_DQ,
	output		          		DRAM_LDQM,
	output		          		DRAM_RAS_N,
	output		          		DRAM_UDQM,
	output		          		DRAM_WE_N,

	//////////// I2C for Audio and Video-In //////////
	output		          		FPGA_I2C_SCLK,
	inout 		          		FPGA_I2C_SDAT,

	//////////// SEG7 //////////
	output		     [6:0]		HEX0,
	output		     [6:0]		HEX1,
	output		     [6:0]		HEX2,
	output		     [6:0]		HEX3,
	output		     [6:0]		HEX4,
	output		     [6:0]		HEX5,

	//////////// KEY //////////
	input 		     [3:0]		KEY,

	//////////// LED //////////
	output		     [9:0]		LEDR,

	//////////// PS2 //////////
	inout 		          		PS2_CLK,
	inout 		          		PS2_CLK2,
	inout 		          		PS2_DAT,
	inout 		          		PS2_DAT2,

	//////////// SW //////////
	input 		     [9:0]		SW,

	//////////// VGA //////////
	output		          		VGA_BLANK_N,
	output		     [7:0]		VGA_B,
	output		          		VGA_CLK,
	output		     [7:0]		VGA_G,
	output		          		VGA_HS,
	output		     [7:0]		VGA_R,
	output		          		VGA_SYNC_N,
	output		          		VGA_VS,

	//////////// GPIO_0, GPIO_0 connect to GPIO Default //////////
	inout 		    [35:0]		GPIO_0,

	//////////// GPIO_1, GPIO_1 connect to GPIO Default //////////
	inout 		    [35:0]		GPIO_1
);



//=======================================================
//  REG/WIRE declarations
//=======================================================

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
logic        cpu_dcache_req;
logic        cpu_dcache_ack;
logic [31:0] cpu_dcache_rdata;
logic        cpu_hwregs_req;
logic        cpu_hwregs_ack;
logic [31:0] cpu_hwregs_rdata;
logic        UART_RX, UART_TX;
logic locked;

logic        cpu_iram_req;
logic        cpu_iram_ack;
logic [31:0] cpu_iram_rdata;

logic [8:0]  hwregs_vga_addr;
logic [25:0] hwregs_vga_wdata;
logic        hwregs_vga_select;
logic [9:0]  mouse_x;
logic [9:0]  mouse_y;
logic [95:0] blit_cmd;
logic        blit_cmd_valid;
logic [7:0]  blit_fifo_slots_free;
logic [31:0] blit_status;


logic [2:0]  sdram_req;
logic [25:0] sdram_addr;
logic        sdram_write;
logic        sdram_burst;
logic [3:0]  sdram_byte_enable;
logic [31:0] sdram_wdata;
logic        sdram_ack;
logic [31:0] sdram_rdata;
logic [2:0]  sdram_rdvalid;
logic        sdram_complete;

logic        dcache_sdram_request;
logic [25:0] dcache_sdram_addr;
logic        dcache_sdram_write;
logic [3:0]  dcache_sdram_byte_enable;
logic [31:0] dcache_sdram_wdata;
logic        dcache_sdram_ack;
logic [31:0] dcache_sdram_rdata;
logic        dcache_sdram_rdvalid;

logic        vga_sdram_request;
logic [25:0] vga_sdram_addr;
logic        vga_sdram_ack;
logic [31:0] vga_sdram_rdata;
logic        vga_sdram_rdvalid;
logic        vga_sdram_complete;
logic [9:0]  vga_row;

logic        blitw_sdram_req;
logic [25:0] blitw_sdram_addr;
logic [31:0] blitw_sdram_wdata;
logic [3:0]  blitw_sdram_byte_enable;
logic        blitw_sdram_ack;

logic        blitr_sdram_req;
logic [25:0] blitr_sdram_addr;
logic        blitr_sdram_ack;
logic [31:0] blitr_sdram_rdata;
logic        blitr_sdram_rdvalid;
logic        blitr_sdram_complete;


assign GPIO_0[3:1] = 3'bzzz;
assign GPIO_0[0] = UART_TX;
assign UART_RX = GPIO_0[1];

pll  pll_inst (
    .refclk(CLOCK_50),
    .rst(1'b0),
    .outclk_0(clock),
    .outclk_1(DRAM_CLK),
    .locked(locked)
  );
assign reset = ~locked | ~KEY[0];

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

address_decoder  address_decoder_inst (
    .clock(clock),
    .cpud_request(cpud_request),
    .cpud_addr(cpud_addr),
    .cpud_rdata(cpud_rdata),
    .cpud_ack(cpud_ack),
    .cpu_dcache_req(cpu_dcache_req),
    .cpu_dcache_ack(cpu_dcache_ack),
    .cpu_dcache_rdata(cpu_dcache_rdata),
    .cpu_iram_req(cpu_iram_req),
    .cpu_iram_ack(cpu_iram_ack),
    .cpu_iram_rdata(cpu_iram_rdata),
    .cpu_hwregs_req(cpu_hwregs_req),
    .cpu_hwregs_ack(cpu_hwregs_ack),
    .cpu_hwregs_rdata(cpu_hwregs_rdata)
  );

cpu_icache  cpu_icache_inst (
    .clock(clock),
    .reset(reset),
    .cpui_request(cpui_request),
    .cpui_addr(cpui_addr),
    .cpui_rdata(cpui_rdata),
    .cpui_ack(cpui_ack),
    .cpud_request(cpu_iram_req),
    .cpud_addr(cpud_addr[15:0]),
    .cpud_write(cpud_write),
    .cpud_byte_enable(cpud_byte_enable),
    .cpud_wdata(cpud_wdata),
    .cpud_rdata(cpu_iram_rdata),
    .cpud_ack(cpu_iram_ack)
  );
  
cpu_dcache  cpu_dcache_inst (
    .clock(clock),
    .reset(reset),
    .cpud_request(cpu_dcache_req),
    .cpud_addr(cpud_addr),
    .cpud_write(cpud_write),
    .cpud_byte_enable(cpud_byte_enable),
    .cpud_wdata(cpud_wdata),
    .cpud_rdata(cpu_dcache_rdata),
    .cpud_ack(cpu_dcache_ack),

    .dcache_sdram_request(dcache_sdram_request),
    .dcache_sdram_addr(dcache_sdram_addr),
    .dcache_sdram_write(dcache_sdram_write),
    .dcache_sdram_byte_enable(dcache_sdram_byte_enable),
    .dcache_sdram_wdata(dcache_sdram_wdata),
    .dcache_sdram_ack(dcache_sdram_ack),
    .dcache_sdram_rdata(dcache_sdram_rdata),
    .dcache_sdram_rdvalid(dcache_sdram_rdvalid)
  );

  hwregs  hwregs_inst (
    .clock(clock),
    .reset(reset),
    .cpud_request(cpu_hwregs_req),
    .cpud_addr(cpud_addr[15:0]),
    .cpud_write(cpud_write),
    .cpud_byte_enable(cpud_byte_enable),
    .cpud_wdata(cpud_wdata),
    .cpud_rdata(cpu_hwregs_rdata),
    .cpud_ack(cpu_hwregs_ack),
    .hwregs_vga_addr(hwregs_vga_addr),
    .hwregs_vga_wdata(hwregs_vga_wdata),
    .hwregs_vga_select(hwregs_vga_select),
    .blit_cmd(blit_cmd),
    .blit_cmd_valid(blit_cmd_valid),
    .blit_fifo_slots_free(blit_fifo_slots_free),
    .blit_status(blit_status),
    .HEX0(HEX0),
    .HEX1(HEX1),
    .HEX2(HEX2),
    .HEX3(HEX3),
    .HEX4(HEX4),
    .HEX5(HEX5),
    .KEY(KEY),
    .LEDR(LEDR),
    .SW(SW),
    .UART_TX(UART_TX),
    .UART_RX(UART_RX),
    .GPIO_0(GPIO_0[35:4]),
    .GPIO_1(GPIO_1),
    .PS2_CLK(PS2_CLK),
    .PS2_DAT(PS2_DAT),
    .vga_row(vga_row),
    .mouse_x(mouse_x),
    .mouse_y(mouse_y)
);


sdram_arbiter  sdram_arbiter_inst (
    .clock(clock),
    .reset(reset),
    .sdram_req(sdram_req),
    .sdram_addr(sdram_addr),
    .sdram_write(sdram_write),
    .sdram_burst(sdram_burst),
    .sdram_byte_enable(sdram_byte_enable),
    .sdram_wdata(sdram_wdata),
    .sdram_ack(sdram_ack),
    .sdram_rdata(sdram_rdata),
    .sdram_rdvalid(sdram_rdvalid),
    .sdram_complete(sdram_complete),
    .bus1_request(vga_sdram_request),     // Master1 = VGA display
    .bus1_addr(vga_sdram_addr),
    .bus1_write(1'b0),
    .bus1_burst(1'b1),
    .bus1_byte_enable(4'bx),
    .bus1_wdata(32'bx),
    .bus1_ack(vga_sdram_ack),
    .bus1_rdata(vga_sdram_rdata),
    .bus1_rdvalid(vga_sdram_rdvalid),
    .bus1_complete(vga_sdram_complete),
    .bus2_request(dcache_sdram_request),  // Master2 = CPU data cache
    .bus2_addr(dcache_sdram_addr),
    .bus2_write(dcache_sdram_write),
    .bus2_burst(1'b0),
    .bus2_byte_enable(dcache_sdram_byte_enable),
    .bus2_wdata(dcache_sdram_wdata),
    .bus2_ack(dcache_sdram_ack),
    .bus2_rdata(dcache_sdram_rdata),
    .bus2_rdvalid(dcache_sdram_rdvalid),
    .bus2_complete(),
    .bus3_request(blitr_sdram_req),     // Master3 = Blitter read port
    .bus3_addr(blitr_sdram_addr),
    .bus3_write(1'b0),
    .bus3_burst(1'b1),
    .bus3_byte_enable(4'bx),
    .bus3_wdata(32'bx),
    .bus3_ack(blitr_sdram_ack),
    .bus3_rdata(blitr_sdram_rdata),
    .bus3_rdvalid(blitr_sdram_rdvalid),
    .bus3_complete(blitr_sdram_complete),
    .bus4_request(blitw_sdram_req),     // Master4 = Blitter write port
    .bus4_addr(blitw_sdram_addr),
    .bus4_write(1'b1),
    .bus4_burst(1'b0),
    .bus4_byte_enable(blitw_sdram_byte_enable),
    .bus4_wdata(blitw_sdram_wdata),
    .bus4_ack(blitw_sdram_ack),
    .bus4_rdata(),
    .bus4_rdvalid(),
    .bus4_complete()
  );

sdram_controller  sdram_controller_inst (
  .clock(clock),
  .reset(reset),
  .sdram_req(sdram_req),
  .sdram_addr(sdram_addr),
  .sdram_write(sdram_write),
  .sdram_burst(sdram_burst),
  .sdram_byte_enable(sdram_byte_enable),
  .sdram_wdata(sdram_wdata),
  .sdram_ack(sdram_ack),
  .sdram_rdata(sdram_rdata),
  .sdram_rdvalid(sdram_rdvalid),
  .sdram_complete(sdram_complete),
  .DRAM_ADDR(DRAM_ADDR),
  .DRAM_BA(DRAM_BA),
  .DRAM_CAS_N(DRAM_CAS_N),
  .DRAM_CKE(DRAM_CKE),
  .DRAM_CS_N(DRAM_CS_N),
  .DRAM_DQ(DRAM_DQ),
  .DRAM_LDQM(DRAM_LDQM),
  .DRAM_RAS_N(DRAM_RAS_N),
  .DRAM_UDQM(DRAM_UDQM),
  .DRAM_WE_N(DRAM_WE_N)
);

vga_control  vga_control_inst (
    .clk(clock),
    .reset(reset),
    .hwregs_vga_select(hwregs_vga_select),
    .hwregs_addr(hwregs_vga_addr),
    .hwregs_wdata(hwregs_vga_wdata),
    .mouse_x(mouse_x),
    .mouse_y(mouse_y),
    .vga_sdram_req(vga_sdram_request),
    .vga_sdram_addr(vga_sdram_addr),
    .vga_sdram_ack(vga_sdram_ack),
    .vga_sdram_rdata(vga_sdram_rdata),
    .vga_sdram_rdvalid(vga_sdram_rdvalid),
    .vga_sdram_complete(vga_sdram_complete),
    .VGA_BLANK_N(VGA_BLANK_N),
    .VGA_B(VGA_B),
    .VGA_CLK(VGA_CLK),
    .VGA_G(VGA_G),
    .VGA_HS(VGA_HS),
    .VGA_R(VGA_R),
    .VGA_SYNC_N(VGA_SYNC_N),
    .VGA_VS(VGA_VS),
    .vga_row(vga_row),
    .SW(SW[2:0])
  );

blit_top  blit_top_inst (
  .clock(clock),
  .reset(reset),
  .blit_cmd(blit_cmd),
  .blit_cmd_valid(blit_cmd_valid),
  .blit_fifo_slots_free(blit_fifo_slots_free),
  .blit_status(blit_status),
  .blitw_sdram_req(blitw_sdram_req),
  .blitw_sdram_addr(blitw_sdram_addr),
  .blitw_sdram_wdata(blitw_sdram_wdata),
  .blitw_sdram_byte_enable(blitw_sdram_byte_enable),
  .blitw_sdram_ack(blitw_sdram_ack),
  .blitr_sdram_req(blitr_sdram_req),
  .blitr_sdram_addr(blitr_sdram_addr),
  .blitr_sdram_ack(blitr_sdram_ack),
  .blitr_sdram_rdata(blitr_sdram_rdata),
  .blitr_sdram_rdvalid(blitr_sdram_rdvalid),
  .blitr_sdram_complete(blitr_sdram_complete)
);

endmodule
