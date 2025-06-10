`timescale 1ns/1ps

module sdram_controller(
    input  logic clock,
    input  logic reset,

    // Arbiter interface
    input  logic [2:0]  sdram_req,          // Which bus master requests SDRAM access
    input  logic [25:0] sdram_addr,         // Address of data to read/write
    input  logic        sdram_write,        // 1 = write, 0 = read
    input  logic [3:0]  sdram_byte_enable,  // For a write, which bytes to write.
    input  logic [31:0] sdram_wdata,        // Data to write
    output logic        sdram_ack,          // SDRAM has responded to the request.
    output logic [31:0] sdram_rdata,        // Data read from SDRAM
    output logic [2:0]  sdram_rdvalid,      // Which bus master to send data to
  
    // SDRAM interface
    output logic [12:0] DRAM_ADDR,
	output logic  [1:0] DRAM_BA,
	output logic        DRAM_CAS_N,
	output logic        DRAM_CKE,
	output logic        DRAM_CS_N,
	inout  logic [15:0] DRAM_DQ,
	output logic        DRAM_LDQM,
	output logic        DRAM_RAS_N,
	output logic        DRAM_UDQM,
	output logic        DRAM_WE_N
);

// SDRAM COMMANDS
`define CMD_NOP    3'b011


endmodule