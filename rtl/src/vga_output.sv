`timescale 1ns / 1ps

module vga_output(
    input logic clk,
    input logic reset,

    // Connections to the vga pixel fifo
    input logic [23:0] pixel_color,
    output logic       pixel_taken,
    output logic       vsync,

    input logic [9:0]  mouse_x,
    input logic [9:0]  mouse_y,

    // Connections to the vga output pins
    output logic       VGA_BLANK_N,
	output logic [7:0] VGA_B,
	output logic       VGA_CLK,
	output logic [7:0] VGA_G,
	output logic       VGA_HS,
	output logic [7:0] VGA_R,
	output logic       VGA_SYNC_N,
	output logic       VGA_VS,
    output logic [9:0] vga_row,

    input logic [2:0]  SW
);

// Pixel clock 25 Mhz => CPU clock divided by 4
// 
// Horizontal timing (line)
// Polarity of horizontal sync pulse is negative.
// Visible area	640	    0..639
// Front porch	 16	    640..655
// Sync pulse	 96	    656..751
// Back porch	 48	    752..799
// Whole line	800	
// 
// Vertical timing (frame)
// Visible area	480	    0..479
// Front porch	10	    480..489
// Sync pulse	2	    490..491
// Back porch	33	    492..524
// Whole frame	525	     

logic [1:0]  count_c;
logic [9:0]  count_x;
logic [9:0]  count_y;

logic [7:0] hs_shift;
logic [7:0] vs_shift;

wire in_visible_area = count_x < 640 && count_y < 480;
assign vsync = count_x==0 && count_y == 524 && count_c == 2'b11;  // Send the vsync pulse one line before the visible area 
assign VGA_CLK = count_c[1];
assign VGA_SYNC_N = 1'b1;
assign vga_row = count_y;

reg [15:0] mouse_image[15:0];
initial begin
    mouse_image[0]  = 16'b1000000000000000;
    mouse_image[1]  = 16'b1100000000000000;
    mouse_image[2]  = 16'b1110000000000000;
    mouse_image[3]  = 16'b1111000000000000;
    mouse_image[4]  = 16'b1111100000000000;
    mouse_image[5]  = 16'b1111110000000000;
    mouse_image[6]  = 16'b1111111000000000;
    mouse_image[7]  = 16'b1111111100000000;
    mouse_image[8]  = 16'b1111111110000000;
    mouse_image[9]  = 16'b1111111111000000;
    mouse_image[10] = 16'b1111110000000000;
    mouse_image[11] = 16'b1110011000000000;
    mouse_image[12] = 16'b1100011000000000;
    mouse_image[13] = 16'b1000001100000000;
    mouse_image[14] = 16'b0000001100000000;
    mouse_image[15] = 16'b0000000110000000;
end
reg [15:0] mouse_image_line;
reg        mouse_image_bit;

// Mouse cursor
wire [9:0] sprite_x = count_x - mouse_x;
wire [9:0] sprite_y = count_y - mouse_y;

always_ff @(posedge clk) begin
    pixel_taken <= 1'b0;

    count_c <= count_c + 1'b1;
    if (count_c == 2'b11) begin
        count_x <= count_x + 1'b1;
        if (count_x == 799) begin
            count_y <= count_y + 1'b1;
            count_x <= 10'b0;
            if (count_y == 524)
                count_y <= 10'b0;
        end

        if (in_visible_area) begin
            VGA_R <= pixel_color[23:16];
            VGA_G <= pixel_color[15:8];
            VGA_B <= pixel_color[7:0];
            pixel_taken <= 1'b1;
        end else begin
            VGA_R <= 8'b0;
            VGA_G <= 8'b0;
            VGA_B <= 8'b0;
            pixel_taken <= 1'b0;
        end

        // Overlay mouse cursor
        mouse_image_line <=  mouse_image[sprite_y[3:0]];
        mouse_image_bit <= mouse_image_line[15-sprite_x];
        if (sprite_x < 16 && sprite_y < 16 && mouse_image_bit && in_visible_area) begin
            VGA_R <= 8'hff;
            VGA_G <= 8'hff;
            VGA_B <= 8'hff;
        end

        VGA_BLANK_N <= 1'b1; // in_visible_area;
        hs_shift[0] <= count_x >=656 && count_x <=751;
        vs_shift[0] <= count_y >=490 && count_y <=491;



    end

    hs_shift[7:1] <= hs_shift[6:0];
    vs_shift[7:1] <= vs_shift[6:0];

    VGA_HS <= !hs_shift[SW];
    VGA_VS <= !vs_shift[SW];

    if (reset) begin
        count_c <= 2'b0;
        count_x <= 10'b0;
        count_y <= 10'd523;
    end
end


endmodule


