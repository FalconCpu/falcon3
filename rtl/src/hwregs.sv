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
// E0000018  GPIO0          W    32 bits of GPIO0
// E000001C  GPIO1          W    32 bits of GPIO1
// E0000020  GPIO1A         W    4  bits of GPIO1A
// E0000024  VGA_ROW        R    Current row of the VGA display
// E0000028  MOUSE_X        R    Current X position of the mouse
// E000002C  MOUSE_Y        R    Current Y position of the mouse
// E0000030  MOUSE_BTN      R    Current button state of the mouse
// E0000034
// E0000038
// E000003C
// E0000040
// E0000044  SIM            R    Reads as 1 in a simulation, 0 on hardware
// E0000048  TIMER          RW   Free-running counter, increments every cycle.
// E0001XXX  VGA            W    256 words of VGA registers (write only)
// E0002000  BLIT_CMD       W    Command to the blitter, read=number of slots free in fifo
// E0002004  BLIT_ARG1      W    First arg for blitter
// E0002008  BLIT_ARG2      W    Second aeg for blitter
// E000200C  BLIT_COLOR     W    Color for operation
// E0002010  BLIT_STATUS    R    Blitter Status register

// verilator lint_off PINCONNECTEMPTY


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

    // connections to the vga controller
    output logic [8:0]  hwregs_vga_addr,
    output logic [25:0] hwregs_vga_wdata,
    output logic        hwregs_vga_select,
    output logic [9:0]  mouse_x,
    output logic [9:0]  mouse_y,

    output logic [127:0] blit_cmd,
    output logic        blit_cmd_valid,
    input  logic [7:0]  blit_fifo_slots_free,
    input  logic [31:0] blit_status,

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
    input  logic        UART_RX,
    output logic [31:0] GPIO_0,
    output logic [35:0] GPIO_1,
    inout               PS2_CLK,
    inout               PS2_DAT,

    input  logic [9:0]  vga_row
);

logic [23:0] seven_seg;
logic [7:0]  fifo_tx_data;
logic        fifo_tx_complete;
logic        fifo_tx_not_empty;
logic [9:0]  fifo_tx_slots_free;
logic [7:0]  fifo_rx_data;
logic        fifo_rx_not_empty;
logic [7:0]  uart_rx_data;
logic        uart_rx_complete;
logic [2:0]  mouse_buttons;
logic [31:0] timer;


// synthesis translate_off
integer fh;
initial 
   fh =  $fopen("rtl_uart.log", "w");
// synthesis translate_on

always_ff @(posedge clock) begin
    cpud_ack <= cpud_request;
    cpud_rdata <= 32'b0;
    hwregs_vga_addr <= cpud_addr[10:2];
    hwregs_vga_wdata <= cpud_wdata[25:0];
    hwregs_vga_select <= cpud_request && cpud_write && cpud_addr[15:12] == 4'h1;
    blit_cmd_valid <= 1'b0;
    timer <= timer + 1;

    if (cpud_request && cpud_write) begin
        // Write to hardware registers
        case(cpud_addr)
            16'h0000: begin
                if (cpud_wdata[23:0] != seven_seg)
                    $display("[%t] 7SEG = %06X", $time, cpud_wdata[23:0]);
                if (cpud_byte_enable[0]) seven_seg[7:0] <= cpud_wdata[7:0];
                if (cpud_byte_enable[1]) seven_seg[15:8] <= cpud_wdata[15:8];
                if (cpud_byte_enable[2]) seven_seg[23:16] <= cpud_wdata[23:16];
            end
            16'h0004: begin
                if (cpud_byte_enable[0])  LEDR[7:0] <= cpud_wdata[7:0];
                if (cpud_byte_enable[1])  LEDR[9:8] <= cpud_wdata[9:8];
                $display("[%t] LED = %03X", $time, cpud_wdata[10:0]);
            end
            16'h0010: begin 
                // Writes to the UART TX are handled by the FIFO
                // synthesis translate_off
                $write("%c", cpud_wdata[7:0]);
                $fwrite(fh, "%c", cpud_wdata[7:0]);
                // synthesis translate_on
            end 
            16'h0014: begin end // Writes to the UART RX are ignored
            16'h0018: begin
                if (cpud_byte_enable[0])  GPIO_0[7:0] <= cpud_wdata[7:0];
                if (cpud_byte_enable[1])  GPIO_0[15:8] <= cpud_wdata[15:8];
                if (cpud_byte_enable[2])  GPIO_0[23:16] <= cpud_wdata[23:16];
                if (cpud_byte_enable[3])  GPIO_0[31:24] <= cpud_wdata[31:24];
            end
            16'h001C: begin
                if (cpud_byte_enable[0])  GPIO_1[7:0] <= cpud_wdata[7:0];
                if (cpud_byte_enable[1])  GPIO_1[15:8] <= cpud_wdata[15:8];
                if (cpud_byte_enable[2])  GPIO_1[23:16] <= cpud_wdata[23:16];
                if (cpud_byte_enable[3])  GPIO_1[31:24] <= cpud_wdata[31:24];
            end

            16'h0020: begin
                if (cpud_byte_enable[0])  GPIO_1[35:32] <= cpud_wdata[3:0];
            end

            16'h2000: begin
                if (cpud_byte_enable[0])  blit_cmd[7:0] <= cpud_wdata[7:0];
                if (cpud_byte_enable[1])  blit_cmd[15:8] <= cpud_wdata[15:8];
                if (cpud_byte_enable[2])  blit_cmd[23:16] <= cpud_wdata[23:16];
                if (cpud_byte_enable[3])  blit_cmd[31:24] <= cpud_wdata[31:24];
                blit_cmd_valid <= 1;
            end

            16'h2004: begin
                if (cpud_byte_enable[0])  blit_cmd[39:32] <= cpud_wdata[7:0];
                if (cpud_byte_enable[1])  blit_cmd[47:40] <= cpud_wdata[15:8];
                if (cpud_byte_enable[2])  blit_cmd[55:48] <= cpud_wdata[23:16];
                if (cpud_byte_enable[3])  blit_cmd[63:56] <= cpud_wdata[31:24];
            end

            16'h2008: begin
               if (cpud_byte_enable[0])  blit_cmd[71:64] <= cpud_wdata[7:0];
               if (cpud_byte_enable[1])  blit_cmd[79:72] <= cpud_wdata[15:8];
               if (cpud_byte_enable[2])  blit_cmd[87:80] <= cpud_wdata[23:16];
               if (cpud_byte_enable[3])  blit_cmd[95:88] <= cpud_wdata[31:24];
            end

            16'h200C: begin
               if (cpud_byte_enable[0])  blit_cmd[103:96] <= cpud_wdata[7:0];
               if (cpud_byte_enable[1])  blit_cmd[111:104] <= cpud_wdata[15:8];
               if (cpud_byte_enable[2])  blit_cmd[119:112] <= cpud_wdata[23:16];
               if (cpud_byte_enable[3])  blit_cmd[127:120] <= cpud_wdata[31:24];
            end

            16'h0048: timer <= cpud_wdata;

            default: begin end
        endcase

    end else if (cpud_request && !cpud_write) begin
        // Read from hardware registers
        case(cpud_addr)
            16'h0000: cpud_rdata <= {8'h00, seven_seg}; 
            16'h0004: cpud_rdata <= {22'b0, LEDR};
            16'h0008: cpud_rdata <= {22'b0, SW};
            16'h000C: cpud_rdata <= {28'b0, KEY};
            16'h0010: cpud_rdata <= {22'b0, fifo_tx_slots_free};
            16'h0014: cpud_rdata <= fifo_rx_not_empty ? {24'b0, fifo_rx_data} : 32'hffffffff;
            16'h0024: cpud_rdata <= {22'b0, vga_row};
            16'h0028: cpud_rdata <= {22'b0, mouse_x};
            16'h002C: cpud_rdata <= {22'b0, mouse_y};
            16'h0030: cpud_rdata <= {29'b0, mouse_buttons};
            16'h0044: begin 
                cpud_rdata <= 0;
                // synthesis translate_off
                cpud_rdata <= 1;
                // synthesis translate_on
            end
            16'h0048: cpud_rdata <= timer;
            16'h2000: cpud_rdata <= {24'b0, blit_fifo_slots_free};
            16'h2004: cpud_rdata <= {blit_cmd[63:32]};
            16'h2008: cpud_rdata <= {blit_cmd[95:64]};
            16'h200C: cpud_rdata <= {blit_cmd[127:96]};
            16'h2010: cpud_rdata <= blit_status;
            default:  cpud_rdata <= 32'bx;
        endcase
    end

    if (reset) begin
        seven_seg <= 24'h000000;
        LEDR <= 10'b0;
        timer <= 0;
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

uart  uart_inst (
    .clock(clock),
    .reset(reset),
    .UART_RX(UART_RX),
    .UART_TX(UART_TX),
    .rx_complete(uart_rx_complete),
    .rx_data(uart_rx_data),
    .tx_valid(fifo_tx_not_empty),
    .tx_data(fifo_tx_data),
    .tx_complete(fifo_tx_complete)
  );

wire tx_strobe = cpud_request && cpud_write && cpud_addr[15:0] == 16'h0010;

byte_fifo  uart_tx_fifo (
    .clk(clock),
    .reset(reset),
    .write_enable(tx_strobe),
    .write_data(cpud_wdata[7:0]),
    .read_enable(fifo_tx_complete),
    .read_data(fifo_tx_data),
    .slots_free(fifo_tx_slots_free),
    .not_empty(fifo_tx_not_empty)
  );

wire rx_strobe = cpud_request && !cpud_write && cpud_addr[15:0] == 16'h0014;

byte_fifo  uart_rx_fifo (
    .clk(clock),
    .reset(reset),
    .write_enable(uart_rx_complete),
    .write_data(uart_rx_data),
    .read_enable(rx_strobe),
    .read_data(fifo_rx_data),
    .slots_free(),
    .not_empty(fifo_rx_not_empty)
  );

mouse_interface  mouse_interface_inst (
    .clock(clock),
    .reset(reset),
    .PS2_CLK(PS2_CLK),
    .PS2_DAT(PS2_DAT),
    .mouse_x(mouse_x),
    .mouse_y(mouse_y),
    .mouse_buttons(mouse_buttons)
  );  

endmodule