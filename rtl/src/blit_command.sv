`timescale 1ns / 1ps

`define BLIT_NOP        8'h00       
`define BLIT_SET_DEST   8'h01       // arg1=bitmap addr, arg2=bytes per line
`define BLIT_SET_CLIP   8'h02       // arg1=y1/x1, arg2=y2/x2
`define BLIT_SET_OFFSET 8'h03       // arg1=x, arg2=y
`define BLIT_SET_COLOR  8'h04       // arg1=foreground, arg2=background
`define BLIT_RECT       8'h05       // arg1=y1/x1, arg2=y2/x2
`define BLIT_LINE       8'h06       // arg1=y1/x1, arg2=y2/x2
`define BLIT_SET_SRC    8'h07       // arg1=bitmap addr, arg2=bytes per line
`define BLIT_SRC_OFFSET 8'h08       // arg1=x, arg2=y
`define BLIT_IMAGE      8'h09       // arg1=y1/x1 arg2=height/width
`define BLIT_CHAR       8'h0a       // arg1=y/x, arg2=char
`define BLIT_FONT       8'h0b       // arg1=font addr, arg2=Offset/BytePerChar/Height/Width 
`define BLIT_TRANSPARENT 8'h0c      // arg1=color

`define OP_COLOR        2'h0
`define OP_SRC          2'h1
`define OP_MONO         2'h2

module blit_command(
    input  logic clock,
    input  logic reset,
    input  logic stall,
    input  logic [95:0] cmd,
    input  logic        cmd_valid,
    output logic        next_cmd,
    input               fifo_full,

    output  logic [25:0] dest_addr,
    output  logic [15:0] dest_bpl,
    output  logic [31:0] src_addr,
    output  logic [15:0] src_bpl,
    output  logic [15:0] clip_x1,
    output  logic [15:0] clip_y1,
    output  logic [15:0] clip_x2,
    output  logic [15:0] clip_y2,
    output  logic [7:0]  fg_color,
    output  logic [7:0]  bg_color,

    output logic [15:0] p2_dest_x,
    output logic [15:0] p2_dest_y,
    output logic [15:0] p2_src_x,
    output logic [15:0] p2_src_y,
    output logic        p2_write,
    output logic [1:0]  p2_op,
    output logic [1:0]  p3_op,
    output logic        p4_idle,
    output  logic [8:0]  p4_transparent_color
);

typedef enum {WAIT, READY, DRAW_RECT, DRAW_LINE_0, DRAW_LINE, DRAW_IMAGE, DRAW_CHAR} t_state;

logic [15:0] offset_x, offset_y;
logic [15:0] x,y;
logic [15:0] width, height;
logic [15:0] dest_x, dest_y;
logic        p2_idle, p3_idle;
logic [15:0] src_offset_x, src_offset_y;
logic [31:0] reg_src_addr;
logic [15:0] reg_src_bpl;
logic [7:0] reg_char;

logic [31:0] font_addr;
logic [7:0]  font_width, font_height, font_byte_per_char, font_offset;
logic [8:0]  p3_transparent_color, p2_transparent_color, transparent_color;

// Signals for Bressenham's algorithm
logic signed [15:0] dx, dy;
logic signed [15:0] sx, sy;
logic signed [15:0] error;
wire signed [15:0] e2 = error * 16'h2;
logic [15:0] end_x, end_y;

wire [31:0] arg1 = cmd[63:32];
wire [31:0] arg2 = cmd[95:64];

// Account for fonts not starting at char 0

t_state state;

always_ff @(posedge clock) begin
    next_cmd  <= 1'b0;
    if (!stall) begin
        p2_write  <= 1'b0;
        p2_dest_x <= 16'bx;
        p2_dest_y <= 16'bx;
        p2_src_x  <= 16'bx;
        p2_src_y  <= 16'bx;
        p3_op     <= p2_op;
        p4_idle   <= p3_idle;
        p3_idle   <= p2_idle;
        p2_idle   <= (state==READY) && !cmd_valid;
        p2_transparent_color <= transparent_color;
        p3_transparent_color <= p2_transparent_color;
        p4_transparent_color <= p3_transparent_color;
    end

    if (stall) begin
        // do nothing
    end else case (state)
        WAIT: begin
            // wait one cycle to let the command settle
            state <= READY;
        end

        READY: if (cmd_valid) case (cmd[7:0])
            `BLIT_NOP: begin
                next_cmd <= 1'b1;
                state <= WAIT;
            end

            `BLIT_SET_DEST: begin
                dest_addr <= arg1[25:0];
                dest_bpl <= arg2[15:0];
                next_cmd <= 1'b1;
                state <= WAIT;
            end

            `BLIT_SET_CLIP: begin
                clip_x1 <= arg1[15:0];
                clip_y1 <= arg1[31:16];
                clip_x2 <= arg2[15:0];
                clip_y2 <= arg2[31:16];
                next_cmd <= 1'b1;
                state <= WAIT;
            end

            `BLIT_SET_OFFSET: begin
                offset_x <= arg1[15:0];
                offset_y <= arg2[15:0];
                next_cmd <= 1'b1;
                state <= WAIT;
            end

            `BLIT_SET_COLOR: begin
                fg_color <= arg1[7:0];
                bg_color <= arg2[7:0];
                next_cmd <= 1'b1;
                state <= WAIT;
            end

            `BLIT_TRANSPARENT: begin
                transparent_color <= arg1[8:0];
                next_cmd <= 1'b1;
                state <= WAIT;
            end

            `BLIT_SET_SRC: begin
                reg_src_addr <= arg1;
                reg_src_bpl <= arg2[15:0];
                next_cmd <= 1'b1;
                state <= WAIT;
            end

            `BLIT_FONT: begin
                font_addr   <= arg1;
                font_width  <= arg2[7:0];
                font_height <= arg2[15:8];
                font_byte_per_char <= arg2[23:16];
                font_offset <= arg2[31:24];
                next_cmd <= 1'b1;
                state <= WAIT;
            end

            `BLIT_SRC_OFFSET: begin
                src_offset_x <= arg1[15:0];
                src_offset_y <= arg2[15:0];
                next_cmd <= 1'b1;
                state <= WAIT;
            end

            `BLIT_RECT: begin
                dest_x <= arg1[15:0] + offset_x;
                dest_y <= arg1[31:16] + offset_y;
                width  <= arg2[15:0]-arg1[15:0];
                height <= arg2[31:16]-arg1[31:16];
                x      <= 0;
                y      <= 0;
                next_cmd <= 1'b1;
                if (arg1[15:0]>=arg2[15:0] || arg1[31:16]>=arg2[31:16])
                    state <= WAIT;
                else
                    state <= DRAW_RECT;
            end

            `BLIT_LINE: begin
                x <= arg1[15:0] + offset_x;
                y <= arg1[31:16] + offset_y;
                end_x <= arg2[15:0] + offset_x;
                end_y <= arg2[31:16] + offset_y;
                if (arg1[15:0]<=arg2[15:0]) begin
                    sx <= 16'h1;
                    dx <= arg2[15:0]-arg1[15:0];
                end else begin
                    sx <= 16'hffff;
                    dx <= arg1[15:0]-arg2[15:0];
                end
                if (arg1[31:16]<=arg2[31:16]) begin
                    sy <= 16'h1;
                    dy <= arg2[31:16]-arg1[31:16];
                end else begin
                    sy <= 16'hffff;
                    dy <= arg1[31:16]-arg2[31:16];
                end
                next_cmd <= 1'b1;
                state <= DRAW_LINE_0;
            end

            `BLIT_IMAGE: begin
                src_addr <= reg_src_addr;
                src_bpl  <= reg_src_bpl;
                dest_x   <= arg1[15:0] + offset_x;
                dest_y   <= arg1[31:16] + offset_y;
                width    <= arg2[15:0];
                height   <= arg2[31:16];
                x        <= 0;
                y        <= 0;
                next_cmd <= 1'b1;
                state <= DRAW_IMAGE;
            end

            `BLIT_CHAR: begin
                reg_char <= arg1[7:0] - font_offset;
                src_bpl  <= {11'b0,font_width[7:3]};
                dest_x   <= arg1[15:0] + offset_x;
                dest_y   <= arg1[31:16] + offset_y;
                width    <= {8'b0,font_width};
                height   <= {8'b0,font_height};
                x        <= 0;
                y        <= 0;
                next_cmd <= 1'b1;
                state    <= DRAW_CHAR;
            end 

            default: begin
                $display("Unknown blit command %x", cmd[7:0]);
                next_cmd <= 1'b1;
                state <= WAIT;
            end
        endcase

        DRAW_RECT:
            if (!fifo_full) begin
                p2_dest_x <= dest_x + x;
                p2_dest_y <= dest_y + y;
                p2_write  <= 1'b1;
                p2_op     <= `OP_COLOR;
                if (x==width) begin
                    x <= 0;
                    y <= y + 16'h1;
                    if (y==height)
                        state <= WAIT;
                end else
                    x <= x + 16'h1;
            end

        DRAW_LINE_0: begin
            error <= dx - dy;
            state <= DRAW_LINE;
        end

        DRAW_LINE: if (!fifo_full) begin
            p2_dest_x <= x;
            p2_dest_y <= y;
            p2_write  <= 1'b1;
            p2_op     <= `OP_COLOR;
            if (x==end_x && y==end_y)
                state <= WAIT;
            else begin
                // Bressenham's algorithm. Rather cryptic way of writing it to avoid having to split out into a combinatorial process
                y <= y + (( e2 < dx ) ? sy : 16'h0);
                x <= x + (( e2 > -dy ) ? sx : 16'h0);
                error <= error + ( (e2 < dx) ? dx : 16'h0 ) + ( (e2 > -dy) ? -dy : 16'h0 );
            end
        end

        DRAW_IMAGE:
            if (!fifo_full) begin
                p2_dest_x <= dest_x + x;
                p2_dest_y <= dest_y + y;
                p2_src_x <= src_offset_x + x;
                p2_src_y <= src_offset_y + y;
                p2_write  <= 1'b1;
                p2_op     <= `OP_SRC;
                if (x+1==width) begin
                    x <= 0;
                    y <= y + 16'h1;
                    if (y+1==height)
                        state <= WAIT;
                end else
                    x <= x + 16'h1;
            end

        DRAW_CHAR:
            if (!fifo_full) begin
                src_addr <= font_addr + (reg_char * font_byte_per_char);
                p2_dest_x <= dest_x + x;
                p2_dest_y <= dest_y + y;
                p2_src_x <= x;
                p2_src_y <= y;
                p2_write  <= 1'b1;
                p2_op     <= `OP_MONO;
                if (x+1==width) begin
                    x <= 0;
                    y <= y + 16'h1;
                    if (y+1==height)
                        state <= WAIT;
                end else
                    x <= x + 16'h1;
            end


    endcase

    if (reset) begin
        state <= READY;
        p2_write <= 1'b0;
        p2_op <= 2'b00;
    end
end



endmodule