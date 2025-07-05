`timescale 1ns/1ns

module cpu_mpu (
    input logic        clock,
    input logic        reset,
    input logic        supervisor,             // 1 = supervisor mode, 0 = user mode

    input logic        cpud_request,    // CPU requests a bus transaction. Asserts for one cycle.
    input logic        cpud_write,      // 1 = write, 0 = read
    input logic [31:0] cpud_addr,       // Address of data to read/write
    input logic [31:0] csr_dmpu0,
    input logic [31:0] csr_dmpu1,
    input logic [31:0] csr_dmpu2,
    input logic [31:0] csr_dmpu3,
    input logic [31:0] csr_dmpu4,
    input logic [31:0] csr_dmpu5,
    input logic [31:0] csr_dmpu6,
    input logic [31:0] csr_dmpu7,

    output logic       access_deny     // Signal an access violation. Reported one cycle after cpud_request.
);

// csr_dmpu format:-
// 31:12   Base Address
// 11      Reserved
// 10      Executable
// 9       Writable
// 8       Readable
// 7:4     Reserved
// 3:0     Log2 Size (0=4k, 1=8k, 2=16k, 3=32k, 4=64k, 5=128k, 6=256k, 7=512k,...))

integer i;
logic        hit, read_match, write_match;
logic [7:0]  region_pass;

logic [31:0] csr_dmpu[0:7];
assign csr_dmpu[0] = csr_dmpu0;
assign csr_dmpu[1] = csr_dmpu1;
assign csr_dmpu[2] = csr_dmpu2;
assign csr_dmpu[3] = csr_dmpu3;
assign csr_dmpu[4] = csr_dmpu4;
assign csr_dmpu[5] = csr_dmpu5;
assign csr_dmpu[6] = csr_dmpu6;
assign csr_dmpu[7] = csr_dmpu7;

logic [19:0] mask;
logic [19:0] addr_mask;
logic [19:0] addr_base;

always_comb begin
    region_pass = 8'h0;

    for (i=0; i<8; i=i+1) begin
        case(csr_dmpu[i][3:0])
            4'h0    : mask = 20'b1111_1111_1111_1111_1111;   // 4k
            4'h1    : mask = 20'b1111_1111_1111_1111_1110;   // 8k
            4'h2    : mask = 20'b1111_1111_1111_1111_1100;   // 16k
            4'h3    : mask = 20'b1111_1111_1111_1111_1000;   // 32k
            4'h4    : mask = 20'b1111_1111_1111_1111_0000;   // 64k
            4'h5    : mask = 20'b1111_1111_1111_1110_0000;   // 128k
            4'h6    : mask = 20'b1111_1111_1111_1100_0000;   // 256k
            4'h7    : mask = 20'b1111_1111_1111_1000_0000;   // 512k
            4'h8    : mask = 20'b1111_1111_1111_0000_0000;   // 1M
            4'h9    : mask = 20'b1111_1111_1110_0000_0000;   // 2M
            4'hA    : mask = 20'b1111_1111_1100_0000_0000;   // 4M
            4'hB    : mask = 20'b1111_1111_1000_0000_0000;   // 8M
            4'hC    : mask = 20'b1111_1111_0000_0000_0000;   // 16M
            4'hD    : mask = 20'b1111_1110_0000_0000_0000;   // 32M
            4'hE    : mask = 20'b1111_1100_0000_0000_0000;   // 64M
            default : mask = 20'b1111_1000_0000_0000_0000;   // 128M
        endcase

        addr_base = csr_dmpu[i][31:12];
        addr_mask = cpud_addr[31:12] & mask;

        hit = addr_base==addr_mask;
        read_match = csr_dmpu[i][8] && !cpud_write;
        write_match = csr_dmpu[i][9] && cpud_write;
        if (hit && (read_match || write_match))
            region_pass[i] = 1'b1;
    end
    access_deny = cpud_request && !supervisor && (region_pass == 8'h00);
end

wire unused_ok = &{1'b0, cpud_addr[11:0]};

endmodule