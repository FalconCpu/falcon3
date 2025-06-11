`timescale 1ns / 1ps

module address_decoder(
    input  logic        clock,
    // connections to the CPU
    input  logic        cpud_request,
    input  logic [31:0] cpud_addr,
    output logic [31:0] cpud_rdata,
    output logic        cpud_ack,

    // connections to the SRAM
    output logic        cpu_dcache_req,
    input  logic        cpu_dcache_ack,
    input  logic [31:0] cpu_dcache_rdata,

    // connections to the Instruction Memory
    output logic        cpu_iram_req,
    input  logic        cpu_iram_ack,
    input  logic [31:0] cpu_iram_rdata,


    // connections to the HWREGS
    output logic        cpu_hwregs_req,
    input  logic        cpu_hwregs_ack,
    input  logic [31:0] cpu_hwregs_rdata
);

logic invalid_addr, invalid_addr_ack;

always_comb begin
    cpu_dcache_req = 1'b0;
    cpu_hwregs_req = 1'b0;
    cpu_iram_req = 1'b0;
    invalid_addr = 1'b0;

    // Route the request to the correct device
    if (cpud_request) begin
        if (cpud_addr[31:26] == 6'h0)
            cpu_dcache_req = 1'b1;
        else if (cpud_addr[31:16] == 16'hE000)
            cpu_hwregs_req = 1'b1;
        else if (cpud_addr[31:16] == 16'hFFFF)
            cpu_iram_req = 1'b1;
        else
            invalid_addr = 1'b1;
    end

    // Merge the ack signals
    cpud_ack = cpu_dcache_ack || cpu_hwregs_ack || cpu_iram_ack || invalid_addr_ack;
    cpud_rdata = cpu_dcache_rdata | cpu_hwregs_rdata | cpu_iram_rdata;
end

always_ff @(posedge clock) begin
    invalid_addr_ack <= invalid_addr;
end

endmodule