`timescale 1ns / 1ps

// Module for the hardware registers
//
// This creates a 64kb block sitting at address 0xE0000000 in the CPU address space.
//
// ADDRESS   REGISTER       R/W  DESCRIPTION
// E0000000  SEVEN_SEG      R/W  6 digit hexadecimal seven segment display
// E0000004  LEDR           R/W  10 LEDs
// E0000008  SW             R    10 Switches
// E000000C  KEY            R    4 Push buttons
// E0000010  UART_TX        R/W  Write = byte of data to transmit, read = number of slots free in fifo
// E0000014  UART_RX        R    1 byte of data from the uart, -1 if no data

module hwregs (
    input  logic clock,
    input  logic reset,

    // Connection to the CPU bus
    input  logic        cpud_request,     // CPU requests a bus transaction. Asserts for one cycle.
    input  logic [15:0] cpud_addr,        // Address of data to read/write
    input  logic        cpud_write,       // 1 = write, 0 = read
    input  logic [3:0]  cpud_byte_enable, // For a write, which bytes to write.
    input  logic [31:0] cpud_wdata,       // Data to write
    output logic [31:0] cpud_rdata,       // Data read from memory
    output logic        cpud_ack,         // Memory has responded to the request.

    // Connections to the chip pins
    output logic [6:0]	HEX0,
	output logic [6:0]	HEX1,
	output logic [6:0]	HEX2,
	output logic [6:0]	HEX3,
	output logic [6:0]	HEX4,
	output logic [6:0]	HEX5,
	input  logic [3:0]	KEY,
	output logic [9:0]	LEDR,
    input  logic [9:0]  SW,
    output logic        UART_TX,
    input  logic        UART_RX
);

logic [23:0] seven_seg;

always_ff @(posedge clock) begin
    cpud_ack <= cpud_request;
    cpud_rdata <= 32'b0;

    if (cpud_request && cpud_write) begin
        // Write to hardware registers
        case(cpud_addr)
            16'h0000: seven_seg <= cpud_wdata[23:0];
            16'h0004: LEDR <= cpud_wdata[9:0];
            16'h0010: begin end // UART not yet implemented
        endcase

    end else begin
        // Read from hardware registers
        case(cpud_addr)
            16'h0000: cpud_rdata <= {8'h00, seven_seg}; 
            16'h0004: cpud_rdata <= {22'b0, LEDR};
            16'h0008: cpud_rdata <= {22'b0, SW};
            16'h000C: cpud_rdata <= {28'b0, KEY};
            16'h0010: begin end // UART not yet implemented
            16'h0014: begin end // UART not yet implemented
        endcase
    end

    if (reset) begin
        seven_seg <= 24'h000000;
        LEDR <= 10'b0;
    end
end


seven_seg  seven_seg_inst (
    .seven_seg_data(seven_seg),
    .HEX0(HEX0),
    .HEX1(HEX1),
    .HEX2(HEX2),
    .HEX3(HEX3),
    .HEX4(HEX4),
    .HEX5(HEX5)
  );

endmodule