`timescale 1ns/1ns

module cpu_divider(
    input             clock,
    input             start,            // pulsed to start division
    input [31:0]      data_a,           // numerator  
    input [31:0]      data_b,           // denominator
    input             signed_div,       // perform signed division
    output     [31:0] quotient,
    output     [31:0] remainder,
    output reg        done
);

reg [4:0]  this_count=31, next_count=31;
reg [31:0] this_numerator, next_numerator;
reg [31:0] this_denominator, next_denominator;
reg [31:0] this_quotient, next_quotient;
reg [31:0] this_remainder, next_remainder;
reg        this_sign, next_sign;
reg        next_done;
reg signed [31:0] n,s;

assign quotient = this_quotient;
assign remainder = this_remainder;

always @(*) begin
    next_numerator = this_numerator;
    next_denominator = this_denominator;
    next_quotient = this_quotient;
    next_remainder = this_remainder;
    next_count = this_count;
    next_sign = this_sign;
    next_done = done;
    n = 32'bx;
    s = 32'bx;

    if (start) begin
        if (signed_div && data_a[31]==1'b1)
            next_numerator = -data_a;
        else
            next_numerator = data_a;
        if (signed_div && data_b[31]==1'b1)
            next_denominator = -data_b;
        else
            next_denominator = data_b;
        next_sign = signed_div && data_a[31]!=data_b[31];
        if (data_b==0) begin
            next_quotient = 32'hffffffff;
            next_remainder = data_a;
            next_done = 1;
        end else if (next_numerator==this_numerator && next_denominator==this_denominator && next_sign==this_sign) begin
            // If we have already done this division, just return the result
            next_quotient = this_quotient;
            next_remainder = this_remainder;
            next_done = 1;
        end else begin
            next_quotient = 0;
            next_remainder = 0;
            next_count = 31;
            next_done = 0;
        end

    end else if (!done) begin
        n = {this_remainder[30:0], this_numerator[this_count]};
        s = n - this_denominator;
        if (s>=0) begin
            next_remainder = s;
            next_quotient = {this_quotient[30:0], 1'b1};
        end else begin
            next_remainder = n;
            next_quotient = {this_quotient[30:0], 1'b0};
        end 
        next_count = this_count - 1'b1;
        if (this_count==0) begin
            next_done = 1;
            if (this_sign) begin
                next_quotient = -next_quotient;
                next_remainder = -next_remainder;
            end
        end
    end
end

always @(posedge clock) begin
    this_count     <= next_count;
    this_quotient  <= next_quotient;
    this_remainder <= next_remainder;
    done           <= next_done;
    this_numerator <= next_numerator;
    this_denominator <= next_denominator;
    this_sign      <= next_sign;
end

endmodule