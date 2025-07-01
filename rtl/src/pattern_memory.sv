`timescale 1ns/1ps

module pattern_memory(
    input  logic clock,
    
    // CPU Data Bus
    input  logic        cpud_request,     // CPU requests a bus transaction. Asserts for one cycle.
    input  logic [15:0] cpud_addr,        // Address of data to read/write
    input  logic        cpud_write,       // 1 = write, 0 = read
    input  logic [3:0]  cpud_byte_enable, // For a write, which bytes to write.
    input  logic [31:0] cpud_wdata,       // Data to write
    output logic [31:0] cpud_rdata,       // Data read from memory
    output logic        cpud_ack,         // Memory has responded to the request.

    // Blitter interface
    input  logic        blit_request,     // Blitter requests a bus transaction. Asserts for one cycle.
    input  logic [15:0] blit_addr,        // Address of data to read/write
    input  logic        blit_write,       // 1 = write, 0 = read
    input  logic [3:0]  blit_byte_enable, // For a write, which bytes to write.
    input  logic [31:0] blit_wdata,       // Data to write
    output logic [31:0] blit_rdata,       // Data read from memory
    output logic        blit_ack          // Memory has responded to the request.
);


logic [31:0] cpu_q;
logic [31:0] blit_q;
logic        cpud_read;
logic        blit_read;


ram64k  ram64k_inst (
    .clock(clock),
    .address_a(cpud_addr[15:2]),
    .address_b(blit_addr[15:2]),
    .byteena_a(cpud_byte_enable),
    .byteena_b(blit_byte_enable),
    .data_a(cpud_wdata),
    .data_b(blit_wdata),
    .wren_a(cpud_write && cpud_request),
    .wren_b(blit_write && blit_request),
    .q_a(cpu_q),
    .q_b(blit_q)
  );

assign cpud_rdata = cpud_read ? cpu_q  : 32'b0;
assign blit_rdata = blit_read ? blit_q : 32'b0;

always_ff @(posedge clock) begin
    cpud_read  <= cpud_request && !cpud_write;
    cpud_ack   <= cpud_request;
    blit_read  <= blit_request && !blit_write;
    blit_ack   <= blit_request;
end

wire unused = &{1'b0, cpud_addr[1:0], blit_addr[1:0]};

endmodule
