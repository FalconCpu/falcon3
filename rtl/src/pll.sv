`timescale 1 ps / 1 ps

module pll (
		input  logic  refclk,   //  refclk.clk
		input  logic  rst,      //   reset.reset
		output logic  outclk_0, // outclk0.clk
		output logic  outclk_1, // outclk1.clk
		output logic  locked    //  locked.export
	);

always begin
    outclk_0 = 0;
    outclk_1 = 0;
    #3750;
    outclk_0 = 1;
    outclk_1 = 1;
    #3750;
end

initial begin
    locked = 0;
    #8000;
    locked = 1;
end

endmodule