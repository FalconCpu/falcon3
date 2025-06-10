`timescale 1ns / 1ps

module sdram_arbiter(
    input clock,
    input reset,

    // Connections to the SDRAM controller
    output  logic [2:0]  sdram_req,          // Which bus master requests SDRAM access
    output  logic [25:0] sdram_addr,         // Address of data to read/write
    output  logic        sdram_write,        // 1 = write, 0 = read
    output  logic        sdram_burst,        // 1 = burst, 0 = single
    output  logic [3:0]  sdram_byte_enable,  // For a write, which bytes to write.
    output  logic [31:0] sdram_wdata,        // Data to write
    input   logic        sdram_ack,          // SDRAM has recieved the request (signal master to deassert)
    input   logic [31:0] sdram_rdata,        // Data read from SDRAM
    input   logic [2:0]  sdram_rdvalid,      // Which bus master the data is for
    input   logic        sdram_complete,     // SDRAM controller is done with the current burst

    // Bus master 1
    input   logic        bus1_request,
    input   logic [25:0] bus1_addr,
    input   logic        bus1_write,
    input   logic        bus1_burst,
    input   logic [3:0]  bus1_byte_enable,
    input   logic [31:0] bus1_wdata,
    output  logic        bus1_ack,
    output  logic [31:0] bus1_rdata,
    output  logic        bus1_rdvalid,
    output  logic        bus1_complete,

    // Bus master 2
    input   logic        bus2_request,
    input   logic [25:0] bus2_addr,
    input   logic        bus2_write,
    input   logic        bus2_burst,
    input   logic [3:0]  bus2_byte_enable,
    input   logic [31:0] bus2_wdata,
    output  logic        bus2_ack,
    output  logic [31:0] bus2_rdata,
    output  logic        bus2_rdvalid,
    output  logic        bus2_complete,

    // Bus master 3
    input   logic        bus3_request,
    input   logic [25:0] bus3_addr,
    input   logic        bus3_write,
    input   logic        bus3_burst,
    input   logic [3:0]  bus3_byte_enable,
    input   logic [31:0] bus3_wdata,
    output  logic        bus3_ack,
    output  logic [31:0] bus3_rdata,
    output  logic        bus3_rdvalid,
    output  logic        bus3_complete
);

logic [2:0]  bus_master, bus_master_q;


always_comb begin


    // Keep the bus master from the previous cycle, if it hasn't finished
    bus_master = bus_master_q;

    // Chose the new bus master
    if (bus_master==0) begin
        if (bus1_request) 
            bus_master = 1;
        else if (bus2_request) 
            bus_master = 2;
        else if (bus3_request) 
            bus_master = 3;
    end

    sdram_req = bus_master;
    sdram_addr =  bus_master==1 ? bus1_addr :
                  bus_master==2 ? bus2_addr :
                  bus_master==3 ? bus3_addr : 26'bx;
    sdram_write = bus_master==1 ? bus1_write :
                  bus_master==2 ? bus2_write :
                  bus_master==3 ? bus3_write : 1'bx;
    sdram_byte_enable = bus_master==1 ? bus1_byte_enable :
                  bus_master==2 ? bus2_byte_enable :
                  bus_master==3 ? bus3_byte_enable : 4'bx;
    sdram_wdata = bus_master==1 ? bus1_wdata :
                  bus_master==2 ? bus2_wdata :
                  bus_master==3 ? bus3_wdata : 32'bx;
    sdram_burst = bus_master==1 ? bus1_burst :
                  bus_master==2 ? bus2_burst :
                  bus_master==3 ? bus3_burst : 1'bx;

    // Pass data from the SDRAM controller to the bus master
    bus1_rdvalid = (sdram_rdvalid==1);
    bus1_rdata = bus1_rdvalid ? sdram_rdata : 32'bx;
    bus1_complete = sdram_complete && bus1_rdvalid;
    bus2_rdvalid = (sdram_rdvalid==2);
    bus2_rdata = bus2_rdvalid ? sdram_rdata : 32'bx;
    bus2_complete = sdram_complete && bus2_rdvalid;
    bus3_rdvalid = (sdram_rdvalid==3);
    bus3_rdata = bus3_rdvalid ? sdram_rdata : 32'bx;
    bus3_complete = sdram_complete && bus3_rdvalid;


    // Pass on the ack from the bus master to the SDRAM controller
    bus1_ack = (bus_master==1) ? sdram_ack : 1'b0;
    bus2_ack = (bus_master==2) ? sdram_ack : 1'b0;
    bus3_ack = (bus_master==3) ? sdram_ack : 1'b0;

    if (sdram_ack || reset)    
        bus_master = 0;
end

always_ff @(posedge clock) begin
    bus_master_q <= bus_master;
end

endmodule