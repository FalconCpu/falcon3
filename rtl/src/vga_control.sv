`timescale 1ns/1ns

module vga_control(
    input logic clk,
    input logic reset,

    // Connections to the hwregs bus (write only)
    input logic        hwregs_vga_select,
    input logic [8:0]  hwregs_addr,
    input logic [25:0] hwregs_wdata,
    input logic [9:0]  mouse_x,
    input logic [9:0]  mouse_y,

    // Connections to the sdram arbiter
    output logic        vga_sdram_req,
    output logic [25:0] vga_sdram_addr,
    input  logic        vga_sdram_ack,
    input  logic [31:0] vga_sdram_rdata,
    input  logic        vga_sdram_rdvalid,
    input  logic        vga_sdram_complete,

    // Connections to the VGA pins
    output logic       VGA_BLANK_N,
	output logic [7:0] VGA_B,
	output logic       VGA_CLK,
	output logic [7:0] VGA_G,
	output logic       VGA_HS,
	output logic [7:0] VGA_R,
	output logic       VGA_SYNC_N,
	output logic       VGA_VS,
    output logic [9:0] vga_row,
    input  logic [2:0] SW
);

genvar g;

// Registers to step through the display
logic [10:0] p1_x;                  // Current pixel x coordinate
logic [10:0] p1_y;
logic        p1_valid;
logic        p1_running;
`define SCREEN_WIDTH 11'd640
`define SCREEN_HEIGHT 11'd480

logic [25:0] p2_addr[0:7];          // Address of current pixel on each layer
logic [7:0]  p2_valid;

logic [25:0] p3_addr;               // Address of current pixel on topmost layer
logic        p3_valid;

logic [7:0]  p4_data;               // Color index of current pixel
logic        p4_valid;

logic [23:0] p5_color;              // Color of current pixel
logic        p5_valid;

logic [23:0] pixel_color;              // Color of current pixel
logic        pixel_taken;


logic fifo_full;

logic vsync;
logic stall;

integer i; 

wire p1_end_of_line = p1_x == `SCREEN_WIDTH - 1;
wire p1_end_of_frame = vsync;

// Instantiate the pixel generator
always_ff @(posedge clk) begin
    // Increment the pixel counter
    if (reset) begin
        p1_x <= 11'h0;
        p1_y <= 11'h0;
        p1_valid <= 1'b0;
        p1_running <= 1'b0;
    end else if (vsync) begin
        p1_x <= 11'h0;
        p1_y <= 11'h0;
        p1_valid <= 1'b1;
        p1_running <= 1'b1;
    end else if (stall) begin
        // Keep everything unchanged
    end else if (!p1_running) begin
        p1_valid <= 1'b0;
        p1_x <= 11'hx;
        p1_y <= 11'hx;
    end else if (fifo_full) begin
        // Stall the pixel generator
        p1_valid <= 1'b0;
    end else if (p1_end_of_frame) begin
        p1_x <= 11'h0;
        p1_y <= 11'h0;
        p1_valid <= 1'b0;
        p1_running <= 1'b0;
    end else if (p1_end_of_line) begin
        p1_x <= 11'h0;
        p1_y <= p1_y + 11'h1;
        p1_valid <= 1'b1;
    end else begin
        p1_x <= p1_x + 11'h1;
        p1_valid <= 1'b1;
    end
end

// Instantiate the layers
generate for (g = 0; g < 8; g++) begin : layer_inst
    vga_layer  vga_layer_inst (
    .clk(clk),
    .reset(reset),
    .stall(stall),
    .hwregs_layer_select(hwregs_vga_select && hwregs_addr[5:3] == g),
    .hwregs_layer_addr(hwregs_addr[2:0]),
    .hwregs_wdata(hwregs_wdata[25:0]),
    .p1_valid(p1_valid),
    .p1_x(p1_x),
    .p1_y(p1_y),
    .p1_end_of_frame(p1_end_of_frame),
    .p1_end_of_line(p1_end_of_line),
    .p2_addr(p2_addr[g]),
    .p2_valid(p2_valid[g])
  );
end
endgenerate

// find the top layer
logic p2_on_screen;
always_ff @(posedge clk) begin
    // Update the top layer
    if (stall) begin
        // Do nothing
    end else begin
        p2_on_screen <= p1_valid && (p1_x<640) && (p1_y<480);
        p3_addr <= 26'h0;
        p3_valid <= p2_on_screen;
        for (i = 0; i < 8; i++) begin
            if (p2_valid[i]) 
                p3_addr <= p2_addr[i];
        end
    end
end

vga_sdram_interface  vga_sdram_interface_inst (
    .clk(clk),
    .reset(reset),
    .pixel_addr(p3_addr),
    .pixel_addr_valid(p3_valid),
    .pixel_data(p4_data),
    .pixel_data_valid(p4_valid),
    .stall(stall),
    .vga_sdram_req(vga_sdram_req),
    .vga_sdram_addr(vga_sdram_addr),
    .vga_sdram_ack(vga_sdram_ack),
    .vga_sdram_rdata(vga_sdram_rdata),
    .vga_sdram_rdvalid(vga_sdram_rdvalid),
    .vga_sdram_complete(vga_sdram_complete)
  );

vga_palette  vga_palette_inst (
    .clk(clk),
    .in_code(p4_data),
    .in_valid(p4_valid),
    .out_color(p5_color),
    .out_valid(p5_valid)
  );

vga_fifo  vga_fifo_inst (
    .clk(clk),
    .reset(reset || vsync),
    .in_data(p5_color),
    .in_valid(p5_valid),
    .fifo_full(fifo_full),
    .out_data(pixel_color),
    .out_taken(pixel_taken)
  );

vga_output  vga_output_inst (
    .clk(clk),
    .reset(reset),
    .mouse_x(mouse_x),
    .mouse_y(mouse_y),
    .pixel_color(pixel_color),
    .pixel_taken(pixel_taken),
    .vsync(vsync),
    .VGA_BLANK_N(VGA_BLANK_N),
    .VGA_B(VGA_B),
    .VGA_CLK(VGA_CLK),
    .VGA_G(VGA_G),
    .VGA_HS(VGA_HS),
    .VGA_R(VGA_R),
    .VGA_SYNC_N(VGA_SYNC_N),
    .VGA_VS(VGA_VS),
    .vga_row(vga_row),
    .SW(SW)
  );


wire _unused_ok = &{hwregs_addr[8:6]};

endmodule