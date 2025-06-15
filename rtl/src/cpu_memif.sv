`timescale 1ns / 1ps

// The cpu_execute block drives the memory bus, and the cpu_
// Keep track of the CPU's memory requests

module cpu_memif(
    (* preserve_for_debug *)input logic clock,
    input logic reset,
    input logic stall,

    // Drive the CPU Data bus
    (* preserve_for_debug *)output logic        cpud_request,     // CPU requests a bus transaction. Asserts for one cycle.
    (* preserve_for_debug *)output logic [31:0] cpud_addr,        // Address of data to read/write
    (* preserve_for_debug *)output logic        cpud_write,       // 1 = write, 0 = read
    (* preserve_for_debug *)output logic [3:0]  cpud_byte_enable, // For a write, which bytes to write.
    (* preserve_for_debug *)output logic [31:0] cpud_wdata,       // Data to write
    (* preserve_for_debug *)input  logic [31:0] cpud_rdata,       // Data read from memory
    (* preserve_for_debug *)input  logic        cpud_ack,         // Memory has responded to the request.

    // Connections to the Execute stage
    (* preserve_for_debug *)input  logic        p3_request,       // CPU requests a bus transaction. Asserts for one cycle.
    (* preserve_for_debug *)input  logic [31:0] p3_addr,          // Address of data to read/write
    (* preserve_for_debug *)input  logic        p3_write,         // 1 = write, 0 = read
    (* preserve_for_debug *)input  logic [3:0]  p3_byte_enable,   // For a write, which bytes to write.
    (* preserve_for_debug *)input  logic [31:0] p3_wdata,         // Data to write
    (* preserve_for_debug *)input  logic [1:0]  p3_size,          // 00 = byte, 01 = halfword, 10 = word
    (* preserve_for_debug *)input  logic        p3_misaligned_address,    
    (* preserve_for_debug *)input  logic        p3_access_deny,

    // output signals to cpu_completion stage
    (* preserve_for_debug *)output logic        p4_write_pending,
    (* preserve_for_debug *)output logic        p4_read_pending, 
    (* preserve_for_debug *)output logic        p4_misaligned_address,
    (* preserve_for_debug *)output logic        p4_load_access_fault,
    (* preserve_for_debug *)output logic        p4_store_access_fault,
    (* preserve_for_debug *)output logic [31:0] p4_mem_rdata,
    (* preserve_for_debug *)output logic [31:0] p4_mem_addr
);  

logic        next_cpud_request;     // CPU requests a bus transaction. Asserts for one cycle.
logic [31:0] next_cpud_addr;        // Address of data to read/write
logic        next_cpud_write;       // 1 = write, 0 = read
logic [3:0]  next_cpud_byte_enable; // For a write, which bytes to write.
logic [31:0] next_cpud_wdata;       // Data to write




(* preserve_for_debug *) logic        p4_write_pending_d, p4_write_pending_q;
(* preserve_for_debug *) logic        p4_read_pending_d,  p4_read_pending_q;
(* preserve_for_debug *) logic [1:0]  read_size, read_size_d;
(* preserve_for_debug *) logic [1:0]  addr_lsb_d, addr_lsb_q;
(* preserve_for_debug *) logic [31:0] p4_mem_rdata_d;
(* preserve_for_debug *) logic [31:0] p4_mem_addr_d;
(* preserve_for_debug *) logic        p3_load_access_fault, p3_store_access_fault;

(* preserve_for_debug *)logic        qualified_request;

// synthesis translate_off
integer fh;
initial begin
    fh = $fopen("rtl_mem.log", "w");
end
// synthesis translate_on

always_comb begin
    next_cpud_request  = 1'b0;
    next_cpud_addr     = 32'h00000000;
    next_cpud_write    = 1'bx;
    next_cpud_byte_enable = 4'bx;
    next_cpud_wdata    = 32'h00000000;
    p4_write_pending_d = p4_write_pending_q;
    p4_read_pending_d  = p4_read_pending_q;
    read_size_d        = read_size;
    p4_mem_rdata_d     = p4_mem_rdata;
    p4_mem_addr_d      = p4_mem_addr;
    addr_lsb_d         = addr_lsb_q;


    qualified_request = p3_request && !p3_misaligned_address && !p3_access_deny;
    p3_load_access_fault = p3_access_deny && !p3_write;
    p3_store_access_fault = p3_access_deny && p3_write;

    if (p3_request && !stall) begin
        next_cpud_request     = qualified_request;
        next_cpud_addr        = p3_addr;
        next_cpud_write       = p3_write;
        next_cpud_byte_enable = p3_byte_enable;
        next_cpud_wdata       = p3_wdata;
        read_size_d           = p3_size;
        p4_mem_addr_d         = p3_addr;
    end

    // When write tranaction is detected we assert write_pending on the next cycle
    // But when we receive an ACK - we deassert immediately to allow the CPU to resume
    // as soon as possible
    if (cpud_ack && p4_write_pending_d)
        p4_write_pending_d = 0;
    p4_write_pending = p4_write_pending_d;
    if (qualified_request && p3_write && !stall)
        p4_write_pending_d = 1;

    // when an request for a read is detected we assert read_pending on the next cycle
    // When the ACK is received we deassert read_pending and update the read_data on the next cycle.
    // This is to allow time for barrelshifting the data. 

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

    if (qualified_request && !p3_write && !stall) begin
        p4_read_pending_d = 1;
        addr_lsb_d = p3_addr[1:0];
        read_size_d = p3_size;
    end

    // Reset
    if (reset) begin
        p4_write_pending_d = 0;
        p4_read_pending_d = 0;
    end 
end 

always_ff @(posedge clock) begin
    cpud_request <= next_cpud_request;
    cpud_addr <= next_cpud_addr;
    cpud_write <= next_cpud_write;
    cpud_byte_enable <= next_cpud_byte_enable;
    cpud_wdata <= next_cpud_wdata;

    if (!stall) begin
        p4_misaligned_address <= p3_misaligned_address;
        p4_load_access_fault <= p3_load_access_fault;
        p4_store_access_fault <= p3_store_access_fault;
    end
    p4_write_pending_q <= p4_write_pending_d;
    p4_read_pending_q  <= p4_read_pending_d;
    p4_mem_rdata       <= p4_mem_rdata_d;
    p4_mem_addr        <= p4_mem_addr_d;
    read_size     <= read_size_d;
    addr_lsb_q    <= addr_lsb_d;

	 // synthesis translate_off
    if (next_cpud_request)
        $fwrite(fh,"[%x]=%x\n",next_cpud_addr,next_cpud_wdata);
	// synthesis translate_on

end

endmodule