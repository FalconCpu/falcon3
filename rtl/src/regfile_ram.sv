`timescale 1ns / 1ps

module regfile_ram (
	input	  clock,
	input	[31:0]  data,
	input	[4:0]  rdaddress,
	input	[4:0]  wraddress,
	input	  wren,
	output	[31:0]  q
);

reg [31:0] mem[0:31];
integer i;
assign q = mem[rdaddress];

always @(posedge clock) begin
    if (wren) begin
        mem[wraddress] <= data;
    end
end

initial begin
    mem[0] = 0;
	mem[30] = 0;
	for(i=0; i<=31; i=i+1)
	   mem[i]=0;
end

endmodule
