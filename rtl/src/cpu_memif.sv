`timescale 1ns / 1ps

// The cpu_execute block drives the memory bus, and the cpu_
// Keep track of the CPU's memory requests

module cpu_memif(
    input logic clock,
    input logic reset,

    // snoop on the CPU Data bus
    input  logic        cpud_request,     // CPU requests a bus transaction. Asserts for one cycle.
    input  logic [1:0]  cpud_addr,        // LSB's of the address
    input  logic [1:0]  cpud_size,         // 00 = byte, 01 = halfword, 10 = word
    input  logic        cpud_write,       // 1 = write, 0 = read
    input  logic [31:0] cpud_rdata,       // Data read from memory
    input  logic        cpud_ack,         // Memory has responded to the request.

    // output signals to cpu_memory
    output logic        p4_write_pending,
    output logic        p4_read_pending, 
    output logic [31:0] p4_mem_rdata
);  

reg p4_write_pending_d, p4_write_pending_q;
reg p4_read_pending_d,  p4_read_pending_q;
reg [1:0] read_size, read_size_d;
reg [1:0] addr_lsb_d, addr_lsb_q;
reg [31:0] p4_mem_rdata_d;

always_comb begin
    // When write tranaction is detected we assert write_pending on the next cycle
    // But when we receive an ACK - we deassert immediately to allow the CPU to resume
    // as soon as possible
    p4_write_pending_d = p4_write_pending_q;
    if (cpud_ack && p4_write_pending_d)
        p4_write_pending_d = 0;
    p4_write_pending = p4_write_pending_d;
    if (cpud_request && cpud_write)
        p4_write_pending_d = 1;

    // when an request for a read is detected we assert read_pending on the next cycle
    // When the ACK is received we deassert read_pending and update the read_data on the next cycle.
    // This is to allow time for barrelshifting the data. 
    p4_read_pending_d = p4_read_pending_q;
    read_size_d    = read_size;
    addr_lsb_d     = addr_lsb_q;
    p4_mem_rdata_d    = p4_mem_rdata;

    p4_read_pending = p4_read_pending_d;
    if (cpud_ack && p4_read_pending_d) begin
        p4_read_pending_d = 0;
        if (read_size_d == 2'b00 && addr_lsb_q == 2'b00 )
            p4_mem_rdata_d = {{24{cpud_rdata[7]}},cpud_rdata[7:0]};
        else if (read_size_d == 2'b00 && addr_lsb_q == 2'b01 )
            p4_mem_rdata_d = {{24{cpud_rdata[15]}},cpud_rdata[15:8]};
        else if (read_size_d == 2'b00 && addr_lsb_q == 2'b10 )
            p4_mem_rdata_d = {{24{cpud_rdata[23]}},cpud_rdata[23:16]};
        else if (read_size_d == 2'b00 && addr_lsb_q == 2'b11 )
            p4_mem_rdata_d = {{24{cpud_rdata[31]}},cpud_rdata[31:24]};
        else if (read_size_d == 2'b01 && addr_lsb_q[1] == 1'b0)
            p4_mem_rdata_d = {{16{cpud_rdata[15]}},cpud_rdata[15:0]};
        else if (read_size_d == 2'b01 && addr_lsb_q[1] == 1'b1)
            p4_mem_rdata_d = {{16{cpud_rdata[31]}},cpud_rdata[31:16]};
        else
            p4_mem_rdata_d = cpud_rdata;
    end

    if (cpud_request && !cpud_write) begin
        p4_read_pending_d = 1;
        read_size_d = cpud_size;
        addr_lsb_d = cpud_addr;
    end

    // Reset
    if (reset) begin
        p4_write_pending_d = 0;
        p4_read_pending_d = 0;
    end 
end 

always_ff @(posedge clock) begin
    p4_write_pending_q <= p4_write_pending_d;
    p4_read_pending_q  <= p4_read_pending_d;
    p4_mem_rdata       <= p4_mem_rdata_d;
    addr_lsb_q      <= addr_lsb_d;
    read_size     <= read_size_d;
end

endmodule