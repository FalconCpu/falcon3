`timescale 1ns/1ns

module blit_top(
    input  logic clock,
    input  logic reset,

    // Connection to the HWREGS
    input  logic [95:0] blit_cmd,
    input  logic        blit_cmd_valid,
    output logic [7:0]  blit_fifo_slots_free,
    output logic [31:0] blit_status,

    // Write port to SDRAM arbiter
    output logic        blitw_sdram_req,
    output logic [25:0] blitw_sdram_addr,
    output logic [31:0] blitw_sdram_wdata,
    output  logic [3:0]  blitw_sdram_byte_enable,
    input  logic        blitw_sdram_ack,

    // read port to SDRAM arbiter
    output logic        blitr_sdram_req,
    output logic [25:0] blitr_sdram_addr,
    input  logic        blitr_sdram_ack,
    input  logic [31:0] blitr_sdram_rdata,
    input  logic        blitr_sdram_rdvalid,
    input  logic        blitr_sdram_complete,

    // Connections to the patram
    output logic        blitr_patram_req,
    output logic [15:0] blitr_patram_addr,
    input  logic [31:0] blitr_patram_rdata,
    input  logic        blitr_patram_rdvalid

);

// Temporary tie-off for now
assign blit_status = {30'b0, fifo_overflow, 1'b0};

logic [95:0] p1_cmd;
logic        p1_cmd_valid;
logic        next_cmd;
logic        fifo_overflow;
logic        stall;

logic [25:0] dest_addr;
logic [15:0] dest_bpl;
logic [31:0] src_addr;
logic [15:0] src_bpl;
logic [7:0]  fg_color;
logic [7:0]  bg_color;
logic [8:0]  p4_transparent_color;
logic [15:0] clip_x1;
logic [15:0] clip_y1;
logic [15:0] clip_x2;
logic [15:0] clip_y2;
logic [15:0] p2_dest_x;
logic [15:0] p2_dest_y;
logic [15:0] p2_src_x;
logic [15:0] p2_src_y;
logic        p2_write;
logic [1:0]  p2_op;
logic [1:0]  p3_op;
logic [31:0] p3_src_addr;
logic [2:0]  p3_src_bit_index;
logic        p4_write;
logic        p3_write;
logic [7:0]  p4_src_data;
logic [25:0] p4_addr;
logic        p4_idle;
logic        p5_write;
logic [25:0] p5_addr;
logic [7:0]  p5_data;
logic        p5_idle;
logic        p6_write;
logic [25:0] p6_addr;
logic [31:0] p6_data;
logic [3:0]  p6_byte_enable;
logic        fifo_full;




blit_fifo  blit_fifo_inst (
    .clock(clock),
    .reset(reset),
    .cmd_in(blit_cmd),
    .cmd_in_valid(blit_cmd_valid),
    .cmd_out(p1_cmd),
    .cmd_out_valid(p1_cmd_valid),
    .fifo_slots_free(blit_fifo_slots_free),
    .fifo_overflow(fifo_overflow),
    .next_cmd(next_cmd && !stall)
  );

// Pipeline stage 1
blit_command  blit_command_inst (
    .clock(clock),
    .reset(reset),
    .stall(stall),
    .cmd(p1_cmd),
    .cmd_valid(p1_cmd_valid),
    .next_cmd(next_cmd),
    .dest_addr(dest_addr),
    .dest_bpl(dest_bpl),
    .src_addr(src_addr),
    .src_bpl(src_bpl),
    .clip_x1(clip_x1),
    .clip_y1(clip_y1),
    .clip_x2(clip_x2),
    .clip_y2(clip_y2),
    .fg_color(fg_color),
    .bg_color(bg_color),
    .p4_transparent_color(p4_transparent_color),
    .p2_dest_x(p2_dest_x),
    .p2_dest_y(p2_dest_y),
    .p2_src_x(p2_src_x),
    .p2_src_y(p2_src_y),
    .p2_op(p2_op),
    .p3_op(p3_op),
    .p2_write(p2_write),
    .p4_idle(p4_idle),
    .fifo_full(fifo_full)
  );

  // Pipeline stage 2
  blit_calc_addr  blit_calc_addr_inst (
    .clock(clock),
    .stall(stall),
    .reset(reset),
    .dest_addr(dest_addr),
    .dest_bpl(dest_bpl),
    .src_addr(src_addr),
    .src_bpl(src_bpl),
    .clip_x1(clip_x1),
    .clip_y1(clip_y1),
    .clip_x2(clip_x2),
    .clip_y2(clip_y2),
    .p2_op(p2_op),
    .p2_dest_x(p2_dest_x),
    .p2_dest_y(p2_dest_y),
    .p2_src_x(p2_src_x),
    .p2_src_y(p2_src_y),
    .p2_write(p2_write),
    .p4_addr(p4_addr),
    .p3_write(p3_write),
    .p3_src_addr(p3_src_addr),
    .p3_src_bit_index(p3_src_bit_index)
  );

// Pipeline stage 3
blit_mem_read  blit_mem_read_inst (
    .clk(clock),
    .reset(reset),
    .p3_src_addr(p3_src_addr),
    .p3_src_bit_index(p3_src_bit_index),
    .fg_color(fg_color),
    .bg_color(bg_color),
    .p3_op(p3_op),
    .p3_write(p3_write),
    .p4_src_data(p4_src_data),
    .p4_write(p4_write),
    .stall(stall),
    .blitr_sdram_req(blitr_sdram_req),
    .blitr_sdram_addr(blitr_sdram_addr),
    .blitr_sdram_ack(blitr_sdram_ack),
    .blitr_sdram_rdata(blitr_sdram_rdata),
    .blitr_sdram_rdvalid(blitr_sdram_rdvalid),
    .blitr_sdram_complete(blitr_sdram_complete),
    .blitr_patram_req(blitr_patram_req),
    .blitr_patram_addr(blitr_patram_addr),
    .blitr_patram_rdata(blitr_patram_rdata),
    .blitr_patram_rdvalid(blitr_patram_rdvalid)
  );

  // pipeline stage 4
blit_transparency  blit_transparency_inst (
    .clock(clock),
    .p4_addr(p4_addr),
    .p4_data(p4_src_data),
    .p4_write(p4_write),
    .p4_idle(p4_idle),
    .transparent_color(p4_transparent_color),
    .p5_addr(p5_addr),
    .p5_data(p5_data),
    .p5_write(p5_write),
    .p5_idle(p5_idle)
  );

// Pipeline stage 5
blit_merge  blit_merge_inst (
    .clock(clock),
    .reset(reset),
    .p5_addr(p5_addr),
    .p5_data(p5_data),
    .p5_write(p5_write),
    .p5_idle(p5_idle),
    .p6_addr(p6_addr),
    .p6_data(p6_data),
    .p6_byte_enable(p6_byte_enable),
    .p6_write(p6_write)
  );

// Pipeline stage 6
blit_write_fifo  blit_write_fifo_inst (
    .clock(clock),
    .reset(reset),
    .in_write(p6_write),
    .in_addr(p6_addr),
    .in_data(p6_data),
    .in_byte_enable(p6_byte_enable),
    .out_req(blitw_sdram_req),
    .out_addr(blitw_sdram_addr),
    .out_data(blitw_sdram_wdata),
    .out_byte_enable(blitw_sdram_byte_enable),
    .out_ack(blitw_sdram_ack),
    .fifo_full(fifo_full)
  );

endmodule