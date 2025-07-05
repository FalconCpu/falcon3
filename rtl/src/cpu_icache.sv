`timescale 1ns/1ns

module  cpu_icache(
    input  logic clock,
    input  logic reset,

    // CPU interface : CPU requests instruction words
    input  logic        cpui_request,     // CPU requests a bus transaction. 
    input  logic [31:0] cpui_addr,        // Address of instruction to read
    output logic [31:0] cpui_rdata,       // Instruction read from memory
    output logic        cpui_ack,         // Indicate the data is ready.

    // IRAM interface : Pass on requests that are not cacheable
    output logic        iram_request,
    output logic [15:0] iram_addr,        
    input  logic [31:0] iram_rdata,       
    input  logic        iram_ack,
    
    // SDRAM interface : Interface to the sdram arbiter
    output logic        cpui_sdram_request,
    output logic [25:0] cpui_sdram_addr,
    input  logic        cpui_sdram_ack,
    input  logic [31:0] cpui_sdram_rdata,
    input  logic        cpui_sdram_rdvalid,
    input  logic        cpui_sdram_complete
);

// We only cache the SDRAM address space. 
// 
// 16kB cache made up of 256 64-byte cache lines
// Address:-
// 25:14 - Tag
// 13:6  - Index
// 5:2   - Offset
// 1:0   - Byte offset

logic [11:0] tag_ram [255:0];
logic [31:0] data_ram [4095:0];
logic [255:0] valid_ram;

// Read ports from the rams
logic [31:0] fetched_cache_data;
logic [11:0] fetched_cache_tag;
logic fetched_cache_valid;

// registers
logic        req, next_req;           // Do we have a request?
logic [25:0] req_addr, next_req_addr;
logic [25:0] cpui_sdram_addr_q;
logic        cpui_sdram_request_q, next_cpui_sdram_request;
logic        cpui_sdram_active, cpui_sdram_active_q;
logic        sdram_match_addr, next_sdram_match_addr; 

// combinational logic
logic cache_hit;
logic cache_miss;
wire [5:0] addr_inc = cpui_sdram_addr_q[5:0] + 6'd4;

always_comb begin
    cpui_sdram_request = cpui_sdram_request_q;
    cpui_sdram_addr = cpui_sdram_addr_q;
    cpui_sdram_active = cpui_sdram_active_q;
    next_cpui_sdram_request = cpui_sdram_request_q && !cpui_sdram_ack;

    // If the address is in the ROM then pass it through.
    if (cpui_addr[31:16]==16'hffff) begin
        iram_request = cpui_request;
        iram_addr = cpui_addr[15:0];
    end else begin
        iram_request = 1'b0;
        iram_addr = 16'hx;
    end

    // Check to see if we have data to pass on
    cache_hit = req && req_addr[25:14]==fetched_cache_tag && fetched_cache_valid;
    cache_miss = req && !cache_hit;

    if (cpui_sdram_rdvalid && sdram_match_addr) begin
        cpui_rdata = cpui_sdram_rdata;
        cpui_ack = 1'b1;
    end else if (iram_ack) begin
        cpui_rdata = iram_rdata;
        cpui_ack = 1'b1;
    end else if (cache_hit) begin
        cpui_rdata = fetched_cache_data;
        cpui_ack = 1'b1;
    end else begin
        cpui_rdata = 32'hx;
        cpui_ack = 1'b0;
    end

    // SDRAM interface. Deal with ongoing transaction first, then deal with new requests
    if (cpui_sdram_rdvalid)             
        // Received a word from SDRAM - increment the address wrapping on cache line boundaries
        cpui_sdram_addr = {cpui_sdram_addr_q[25:6], addr_inc};
    if (cpui_sdram_complete)            
        // SDRAM has finished the request
        cpui_sdram_active = 1'b0;

    if (cache_miss && !cpui_sdram_active) begin 
        // Initiate a new SDRAM request. The SDRAM will respond by sending 16 words(64 bytes) of data, followed by a complete.
        next_cpui_sdram_request = 1'b1;
        cpui_sdram_addr = cpui_addr[25:0];
        cpui_sdram_active = 1'b1;
    end

    // Update the cache request addr
    next_req_addr = req_addr;
    next_req      = req;
    if (cpui_ack) 
        next_req = 1'b0;
    if (cpui_request && cpui_addr[31:26]==0) begin
        next_req_addr = cpui_addr[25:0];
        next_req = 1'b1;
    end

    next_sdram_match_addr = cpui_sdram_addr_q[25:2]==cpui_addr[25:2];
end

always_ff @(posedge clock) begin
    // Update the cache
    if (cpui_sdram_rdvalid) begin
        data_ram[cpui_sdram_addr_q[13:2]] <= cpui_sdram_rdata;
        tag_ram[cpui_sdram_addr_q[13:6]] <= cpui_sdram_addr_q[25:14];
        valid_ram[cpui_sdram_addr_q[13:6]] <= cpui_sdram_complete;
    end

    // Update the registers
    fetched_cache_data <= data_ram[cpui_addr[13:2]];
    fetched_cache_tag <= tag_ram[cpui_addr[13:6]];
    fetched_cache_valid <= valid_ram[cpui_addr[13:6]];
    req <= next_req;
    req_addr <= next_req_addr;
    cpui_sdram_request_q <= next_cpui_sdram_request;
    cpui_sdram_addr_q <= cpui_sdram_addr;
    cpui_sdram_active_q <= cpui_sdram_active;
    sdram_match_addr <= next_sdram_match_addr;

    if (reset) begin
        valid_ram <= 256'b0;
        cpui_sdram_active_q <= 1'b0;
        cpui_sdram_request_q <= 1'b0;
    end
end

// synthesis translate_off
always @(posedge clock) begin
    if (cpui_request && req && !cpui_ack)
        $display("Error %t: CPU requested %x when cache already busy", $time, cpui_addr);
end
// synthesis translate_on

wire unused_ok = &{1'b0,req_addr[1:0]};

endmodule

    
