`timescale 1ns / 1ps
`include "cpu.vh"

module cpu_execute(
    input logic clock,
    input logic reset,
    input logic stall,

    input logic [5:0]  p3_op,           // ALU operation to perform
    input logic [7:0]  p3_opx,          // ALU operation specific parameters
    input logic [31:0] p3_data_a,       // ALU input A
    input logic [31:0] p3_data_b,       // ALU input B
    input logic [31:0] p2_pc,           // PC of instruction being decoded
    input logic [31:0] p3_literal,      // ALU literal value
    input logic        p4_jump_taken,   // nullify this instruction if previous instruction jumped

    // outputs to data bus
    output logic        p3_request,     // CPU requests a bus transaction. Asserts for one cycle.
    output logic [31:0] p3_addr,        // Address of data to read/write
    output logic        p3_write,       // 1 = write, 0 = read
    output logic [3:0]  p3_byte_enable, // For a write, which bytes to write.
    output logic [31:0] p3_wdata,       // Data to write
    output logic [1:0]  p3_size,        // 00 = byte, 01 = halfword, 10 = word

    output logic [31:0] p3_alu_out,    // ALU output
    output logic        p3_jump_taken,
    output logic [31:0] p3_jump_addr,   // ALU jump address
    output logic [31:0] p4_alu_out,
    output logic [31:0] p4_mult,
    output logic        p3_misaligned_address,
    output logic        p3_overflow
);

wire [4:0] shift_amount = p3_data_b[4:0];
wire [31:0] branch_target =  p2_pc + (p3_literal << 2);

wire [31:0] mem_addr = p3_data_a + p3_literal;
wire [1:0] address_lsb = mem_addr[1:0];


logic signed [31:0] signed_a;
logic signed [31:0] asr;
logic [31:0] p3_mult;

always_comb begin
    // default assignments
    p3_alu_out = 32'hxxxxxxxx;
    p3_jump_addr = 32'hxxxxxxxx;
    p3_jump_taken = 0;
    p3_request = 0;
    p3_write = 0;
    p3_wdata = 32'hxxxxxxxx;
    p3_byte_enable = 4'bx;
    p3_misaligned_address = 0;
	p3_size = 2'b0;
    signed_a = $signed(p3_data_a);
    asr = signed_a >>> shift_amount;
    p3_mult = 32'bx;
    p3_overflow = 0;

    case(p3_op) 
        `OP_AND:    p3_alu_out = p3_data_a & p3_data_b;
        `OP_OR:     p3_alu_out = p3_data_a | p3_data_b;
        `OP_XOR:    p3_alu_out = p3_data_a ^ p3_data_b;
        `OP_SHIFT:  p3_alu_out = (p3_opx==8'h00) ? p3_data_a << shift_amount :      // logical shift left
                                 (p3_opx==8'hfe) ? p3_data_a >> shift_amount :       // logical shift right
                                 (p3_opx==8'hff) ? asr :      // arithmetic shift right
                                 32'bx;
        `OP_ADD:    p3_alu_out = p3_data_a + p3_data_b;
        `OP_SUB:    p3_alu_out = p3_data_a - p3_data_b;
        `OP_CLT:    p3_alu_out = signed_a < $signed(p3_data_b);
        `OP_CLTU:   p3_alu_out = $unsigned(p3_data_a) < $unsigned(p3_data_b);
        
        `OP_BEQ: begin 
            p3_jump_addr=branch_target;
            p3_jump_taken = p3_data_a == p3_data_b;
        end

        `OP_BNE: begin 
            p3_jump_addr=branch_target;
            p3_jump_taken = p3_data_a != p3_data_b;
        end

        `OP_BLT: begin
            p3_jump_addr=branch_target;
            p3_jump_taken = $signed(p3_data_a) < $signed(p3_data_b);
        end

        `OP_BGE: begin
            p3_jump_addr=branch_target;
            p3_jump_taken = $signed(p3_data_a) >= $signed(p3_data_b);
        end

        `OP_BLTU: begin
            p3_jump_addr=branch_target;
            p3_jump_taken = $unsigned(p3_data_a) < $unsigned(p3_data_b);
        end

        `OP_BGEU: begin
            p3_jump_addr=branch_target;
            p3_jump_taken = $unsigned(p3_data_a) >= $unsigned(p3_data_b);
        end

        `OP_JMP: begin
            p3_jump_addr = branch_target;
            p3_alu_out = p2_pc;
            p3_jump_taken = 1;
        end

        `OP_JMPR: begin
            p3_jump_addr = p3_data_a + (p3_literal << 2);
            p3_alu_out = p2_pc;
            p3_jump_taken = 1;
        end

        `OP_LDB: begin 
            p3_request = 1;
            p3_write = 0;
            p3_size = 2'b00;
        end

        `OP_LDH: begin 
            p3_request = 1;
            p3_write = 0;
            p3_size = 2'b01;
            p3_misaligned_address = address_lsb[0];
        end

        `OP_LDW: begin
            p3_request = 1;
            p3_write = 0;
            p3_size = 2'b10;
            p3_misaligned_address = address_lsb[1] || address_lsb[0];
         end

        `OP_LDBU: begin
            p3_request = 1;
            p3_write = 0;
            p3_size = 2'b00;
        end

        `OP_LDHU: begin
            p3_request = 1;
            p3_write = 0;
            p3_size = 2'b01;
            p3_misaligned_address = address_lsb[0];
         end

        `OP_STB: begin
            p3_request = 1;
            p3_write = 1;
            if (address_lsb == 2'b00) begin
                p3_byte_enable = 4'b0001;
                p3_wdata = {24'bx, p3_data_b[7:0]};
            end else if (address_lsb == 2'b01) begin
                p3_byte_enable = 4'b0010;
                p3_wdata = {16'bx, p3_data_b[7:0], 8'bx};
            end else if (address_lsb == 2'b10) begin
                p3_byte_enable = 4'b0100;
                p3_wdata = {8'bx, p3_data_b[7:0], 16'bx};
            end else begin
                p3_byte_enable = 4'b1000;
                p3_wdata = {p3_data_b[7:0], 24'bx};
            end
         end

        `OP_STH: begin 
            p3_request = 1;
            p3_write = 1;
            if (address_lsb == 2'b00) begin
                p3_byte_enable = 4'b0011;
                p3_wdata = {16'bx, p3_data_b[15:0]};
            end else if (address_lsb==2'b10) begin
                p3_byte_enable = 4'b1100;
                p3_wdata = {p3_data_b[15:0], 16'bx};
            end else begin
                p3_misaligned_address = 1;
            end
        end

        `OP_STW: begin 
            p3_request = 1;
            p3_write = 1;
            if (address_lsb == 2'b00) begin
                p3_byte_enable = 4'b1111;
                p3_wdata = p3_data_b;
            end else begin
                p3_misaligned_address = 1;
            end
        end

        `OP_MUL: begin 
            p3_mult = p3_data_a * p3_data_b;
        end

        `OP_DIVU,
        `OP_DIVS,
        `OP_MODU,
        `OP_MODS: begin end  // These are handled by the separate divider module

        `OP_CSRR,   
        `OP_CSRW,    
        `OP_RTE,    
        `OP_SYS:    begin end // These are handled by the separate exception module

        `OP_IDX1:   begin
            p3_alu_out = p3_data_a;
            p3_overflow = p3_data_a >= p3_data_b;
        end

        `OP_IDX2:   begin
            p3_alu_out = p3_data_a << 1;
            p3_overflow = p3_data_a >= p3_data_b;
        end

        `OP_IDX4:   begin
            p3_alu_out = p3_data_a << 2;
            p3_overflow = p3_data_a >= p3_data_b;
        end


        `OP_LD:     p3_alu_out = p3_data_b;
        `OP_LDPC:   p3_alu_out = branch_target;
        default:    p3_alu_out = 32'hx;
    endcase

    if (stall || p4_jump_taken) begin
        p3_request = 0;
    end

    if (!p3_request) begin
        p3_misaligned_address = 0;
    end

    p3_addr = p3_request ? mem_addr : 32'h00000000;
    
    // nullify this instruction if the previous one is a jump
    if (p4_jump_taken) begin
        p3_request = 0;
        p3_alu_out = 32'hx;
        p3_jump_taken = 0;
        p3_misaligned_address = 0;
    end
end


always_ff @(posedge clock) begin
    if(!stall) begin
        p4_alu_out <= p3_alu_out;
        p4_mult <= p3_mult;
    end

end

endmodule