`timescale 1ns / 1ps

module vga(
    input  logic clock,
    input  logic reset,

    // VGA interface
    output logic       VGA_BLANK_N,
	output logic [7:0] VGA_B,
	output logic       VGA_CLK,
	output logic [7:0] VGA_G,
	output logic       VGA_HS,
	output logic [7:0] VGA_R,
	output logic       VGA_SYNC_N,
	output logic       VGA_VS,

    // Connection to the SDRAM arbiter
    output logic        vga_sdram_request,
    output logic [25:0] vga_sdram_addr,
    input  logic        vga_sdram_ack,
    input  logic [31:0] vga_sdram_rdata,
    input  logic        vga_sdram_rdvalid,
    input  logic        vga_sdram_complete
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

logic [1:0]  count_c, next_count_c;
logic [9:0]  count_x, next_count_x;
logic [9:0]  count_y, next_count_y;
logic        in_visible_area;
logic        next_vga_sdram_request;
logic [25:0] next_addr;
logic [7:0]  next_vga_r;
logic [7:0]  next_vga_g;
logic [7:0]  next_vga_b;
logic        next_vga_hs;  
logic        next_vga_vs;

logic [31:0] fifo_ram[0:255];
logic [7:0]  fifo_read_pointer, next_fifo_read_pointer; 
logic [7:0]  fifo_write_pointer, next_fifo_write_pointer;
logic [7:0]  fifo_free_count;
logic [31:0] fifo_read_data;
logic [7:0]  pixel_value;

assign VGA_BLANK_N = (count_x<640) && (count_y<480);
assign VGA_SYNC_N = 0;
assign VGA_CLK = count_c[1];

always_comb begin
    next_vga_sdram_request = vga_sdram_request;
    next_addr              = vga_sdram_addr;
    next_count_x           = count_x;
    next_count_y           = count_y;
    next_vga_r             = VGA_R;
    next_vga_g             = VGA_G;
    next_vga_b             = VGA_B;
    next_vga_hs            =  VGA_HS;
    next_vga_vs            =  VGA_VS;
    next_fifo_read_pointer = fifo_read_pointer; 
    next_fifo_write_pointer = fifo_write_pointer;

    // Derive screen coordinates
    next_count_c = count_c + 1'b1;
    if (count_c==3) begin
        next_count_x = count_x +1'b 1;
        if (count_x==799) begin
            next_count_y = count_y + 1'b1;
            next_count_x = 0;
            if (count_y==524)
                next_count_y = 0;
        end
    end

    // Generate the vertical and horizontal sync pulses
    in_visible_area = (count_x<640) && (count_y<480);
    next_vga_hs = !(count_x >=656 && count_x <=751);
    next_vga_vs = !(count_y >=490 && count_y < 492);

    // Read data from the FIFO
    pixel_value = count_x[1:0]== 2'b00 ? fifo_read_data[7:0] : 
                  count_x[1:0]== 2'b01 ? fifo_read_data[15:8] :
                  count_x[1:0]== 2'b10 ? fifo_read_data[23:16] :
                                         fifo_read_data[31:24];

    if (count_x[1:0]==2'b11 && count_c==2'b11 && in_visible_area)
        next_fifo_read_pointer = fifo_read_pointer + 1;

    // Determine the RGB values
    if (count_x<640 && count_y<480) begin
        next_vga_r = {pixel_value[2:0], 5'b0};
        next_vga_g = {pixel_value[5:3], 5'b0};
        next_vga_b = {pixel_value[7:6], 6'b0};
    end else begin
        next_vga_r = 0;
        next_vga_g = 0;
        next_vga_b = 0;
    end



    // Each burst from the memory gets us 64 bytes. So make a request every 64 pixels.
    // We start filling the fifo one scan line before the visible area starts - so that
    // ensures that the fifo nearly full when the visible area starts.

    fifo_free_count = 8'hff - (fifo_write_pointer - fifo_read_pointer);

    if (count_y>=480 && count_y<524) begin
        // Finished one frame. Reset the fifo pointers.
        next_fifo_read_pointer = 0;
        next_fifo_write_pointer = 0;
        next_addr = 26'h3f80000;
    end else if (count_x[5:0]==0 && count_c==0 && fifo_free_count>32)
        next_vga_sdram_request = 1;

    if (vga_sdram_ack)
        next_vga_sdram_request = 0;

    // Write the data to the fifo
    if (vga_sdram_rdvalid) begin
        next_addr = vga_sdram_addr + 1'b1;
        next_fifo_write_pointer = fifo_write_pointer + 1'b1;
    end

    if (reset) begin
        next_fifo_read_pointer = 0;
        next_fifo_write_pointer = 0;
        next_vga_sdram_request = 0;
        next_count_y = 523;
        next_count_x = 0;
        next_count_c = 0;
        next_addr = 26'h3f80000;  // Address of the start of the framebuffer
    end
end


always_ff @(posedge clock) begin
    vga_sdram_request <= next_vga_sdram_request;
    vga_sdram_addr    <= next_addr;
    count_c           <= next_count_c;
    count_x           <= next_count_x;
    count_y           <= next_count_y;
    VGA_R             <= next_vga_r;
    VGA_G             <= next_vga_g;
    VGA_B             <= next_vga_b;
    VGA_HS            <= next_vga_hs;
    VGA_VS            <= next_vga_vs;
    fifo_read_pointer <= next_fifo_read_pointer;
    fifo_write_pointer <= next_fifo_write_pointer;
    if (vga_sdram_rdvalid) 
        fifo_ram[fifo_write_pointer] <= vga_sdram_rdata;
    fifo_read_data <= fifo_ram[next_fifo_read_pointer];
end

endmodule