#define _CRT_SECURE_NO_WARNINGS
#include <stdio.h>
#include <stdlib.h>
#include "f32.h"

static int reg[32];  // The CPU registers
static unsigned int pc;       // The program counter

extern int* prog_mem;  // program memory
extern int* data_mem;  // data memory

static FILE* reg_log;  // log register values to this file
static FILE* uart_log;
extern FILE* trace_file;    
static FILE* blit_log;


// ================================================
//                  exception registers
// ================================================

#define CFG_REG_EPC 1
#define CFG_REG_ECAUSE 2
#define CFG_REG_EDATA 3
#define CFG_REG_ESTATUS 4
#define CFG_REG_ESCRATCH 5
#define CFG_REG_STATUS 6
#define CFG_REG_IPC 7
#define CFG_REG_ICAUSE 8
#define CFG_REG_ISTATUS 9
#define CFG_REG_INTVEC 10
#define CFG_REG_TIMER 11
#define CFG_REG_DMPU0 16
#define CFG_REG_DMPU1 17
#define CFG_REG_DMPU2 18
#define CFG_REG_DMPU3 19
#define CFG_REG_DMPU4 20
#define CFG_REG_DMPU5 21
#define CFG_REG_DMPU6 22
#define CFG_REG_DMPU7 23

#define CAUSE_INSTRUCTION_ACCESS_FAULT 1
#define CAUSE_ILLEGAAL_INSTRUCTION 2
#define CAUSE_BREAKPOINT 3
#define CAUSE_LOAD_ADDRESS_MISALIGNED 4
#define CAUSE_LOAD_ACCESS_FAULT 5
#define CAUSE_STORE_ADDRESS_MISALIGNED 6
#define CAUSE_STORE_ACCESS_FAULT 7
#define CAUSE_SYSTEM_CALL 8
#define CAUSE_INDEX_OVERFLOW 9

#define ICAUSE_TIMER 1

#define STATUS_SUPERVISOR 0x00000001
#define STATUS_INTERRUPT  0x00000002

#define DMPU_ENABLED 0x00000001
#define DMPU_EXECUTE 0x00000002
#define DMPU_WRITE   0x00000004
#define DMPU_READ    0x00000008

static int epc;
static int ecause;
static int edata;
static int estatus;
static int escratch;
static int status = STATUS_SUPERVISOR;
static int exception;
static int ipc;
static int icause;
static int istatus;
static int intvec;
static int int_timer;
static int dmpu[8];   // The data memory protection registers


extern int abort_on_exception;

static string exception_names[] = {
    "",
    "Instruction Access Fault",
    "Illegal Instruction",
    "Breakpoint",
    "Load Address Misaligned",
    "Load Access Fault",
    "Store Address Misaligned",
    "Store Access Fault",
    "System Call",
    "Index out of range"
};

void raise_exception(int cause, int value) {
    if (abort_on_exception) {
        printf("EXCEPTION %s: pc=%08x: data=%08x\n", exception_names[cause], pc-4, value);
        for(int i=1; i<=31; i++) {
            printf("$%2d=%08x ", i, reg[i]);
            if (i%6==0)
                printf("\n");
        }
        exit(1);
    }

    estatus = status;
    ecause = cause;
    edata = value;
    epc = pc-4;
    pc = 0xffff0004;
    status |= STATUS_SUPERVISOR;
    if (trace_file)
        fprintf(trace_file, "EXCEPTION: %d %x\n", cause, value);
}

void raise_interrupt(int cause) {
    istatus = status;
    icause = cause;
    ipc = pc;
    pc = intvec;
    status |= STATUS_SUPERVISOR | STATUS_INTERRUPT;
    if (trace_file)
        fprintf(trace_file, "INTERUPT: %d\n", cause);
}



// ================================================
//                  set_reg
// ================================================

static void set_reg(int reg_num, int value) {
    if (exception)
        return;
    if (reg_num==0)
        return;
    reg[reg_num] = value;
    if (reg_log)
        fprintf(reg_log, "$%2d = %08x\n", reg_num, value);
    if (trace_file)
        fprintf(trace_file, "$%2d = %08x", reg_num, value);
}

// ================================================
//                  alu operation
// ================================================

static int alu_op(int op, int a, int b, int c) {
    switch(op) {
        case 0: return a & b;
        case 1: return a | b;
        case 2: return a ^ b;
        case 3: switch(c&3) {
            case 0: return a << (b & 31);
            case 1: return 0;    
            case 2: return ((unsigned)a) >> (b & 31);
            case 3: return ((signed)a) >> (b & 31);
        }
        case 4: return a + b;
        case 5: return a - b;
        case 6: return a < b;
        case 7: return ((unsigned)a) < ((unsigned)b);
        default: return 0;
    }
}

// ================================================
//                  mul operation
// ================================================

static int mul_op(int op, int a, int b) {
    switch(op) {
        case 0: return a * b;
        case 4: return (b==0) ? -1 : ((unsigned)a) / ((unsigned)b);
        case 5: return (b==0) ? -1 : ((signed)a) / ((signed)b);
        case 6: return (b==0) ? a  : ((unsigned)a) % ((unsigned)b);
        case 7: return (b==0) ? a  : ((signed)a) % ((signed)b);
        default: return 0;
    }
}

// ================================================
//                  branch operation
// ================================================

static int branch_op(int op, int a, int b) {
    switch(op) {
        case 0: return a == b;
        case 1: return a != b;
        case 2: return a < b;
        case 3: return a >= b;
        case 4: return ((unsigned)a) < ((unsigned)b);
        case 5: return ((unsigned)a) >= ((unsigned)b);
        default: return 1;
    }
}

// ================================================
//                  index operation
// ================================================

static int idx_op(int op, int a, int b) {
    if ((unsigned)a >= (unsigned)b)
        raise_exception(CAUSE_INDEX_OVERFLOW, a);

    switch(op) {
        case 0: return a;
        case 1: return a * 2;
        case 2: return a * 4;
        default: return 0;
    }
}


// ================================================
//                  blitter
// ================================================

#define ARGX(A) (A & 0xffff)
#define ARGY(A) ((A >> 16) & 0xffff)

#define BLIT_CMD_SET_DEST_ADDR      0x01  //address:Int, bytesPerRow:Short
#define BLIT_CMD_SET_SRC_ADDR       0x02  //address:Int, bytesPerRow:Short
#define BLIT_CMD_FILL_RECT          0x03  //width:Short, height:Short,x1:Short, y1:Short,  color:Short
#define BLIT_CMD_COPY_RECT          0x04  //width:Short, height:Short, destX:Short, destY:Short, srcX:Short, srcY:Short 
#define BLIT_CMD_COPY_RECT_REVERSED 0x05  //width:Short, height:Short, destX:Short, destY:Short, srcX:Short, srcY:Short
#define BLIT_CMD_SET_CLIP_RECT      0x06  //x1:Short, y1:Short, x2:Short, y2:Short
#define BLIT_CMD_SET_TRANS_COLOR    0x07  //color:Short
#define BLIT_CMD_SET_FONT           0x08  //fontAddr:Int, fontWidth:Short, fontHeight:Short, fontBpr:Short, fontBpc:Short
#define BLIT_CMD_DRAW_CHAR          0x09  //char:Short, _:Short, x:Short, y:Short, color:Short, bgColor:Short
#define BLIT_CMD_DRAW_LINE          0x0A  //x1:Short, y1:Short, x2:Short, y2:Short, color:Short

static int blit_dest = 0;
static int blit_dest_bpr = 0;
static int blit_src = 0;
static int blit_src_bpr = 0;
static int blit_font;
static int blit_font_width;
static int blit_font_height;

static void blit_op(int op, int a1, int a2, int a3) {
    switch(op) {
        case BLIT_CMD_SET_DEST_ADDR:
            blit_dest = a1;
            blit_dest_bpr = ARGX(a2);
            //fprintf(blit_log, "blit dest %x %d\n", blit_dest, blit_dest_bpr);
            break;

        case BLIT_CMD_SET_SRC_ADDR:
            blit_src = a1;
            blit_src_bpr = ARGX(a2);
            //fprintf(blit_log, "blit src %x %d\n", blit_src, blit_src_bpr);
            break;

        case BLIT_CMD_FILL_RECT:
            fprintf(blit_log, "fill rect X=%d Y=%d WIDTH=%d HEIGHT=%d COLOR=%d\n", ARGX(a2), ARGY(a2), ARGX(a1), ARGY(a1), ARGX(a3));
            break;

        case BLIT_CMD_COPY_RECT:
            fprintf(blit_log, "copy rect WIDTH=%d HEIGHT=%d DESTX=%d DESTY=%d SRCX=%d SRCY=%d FROM=%x TO=%x\n", ARGX(a1), ARGY(a1), ARGX(a2), ARGY(a2), ARGX(a3), ARGY(a3), blit_src, blit_dest);
            break;

        case BLIT_CMD_COPY_RECT_REVERSED:
            fprintf(blit_log, "copy rev  WIDTH=%d HEIGHT=%d DESTX=%d DESTY=%d SRCX=%d SRCY=%d FROM=%x TO=%x\n", ARGX(a1), ARGY(a1), ARGX(a2), ARGY(a2), ARGX(a3), ARGY(a3), blit_src, blit_dest);
            break;

        case BLIT_CMD_SET_CLIP_RECT:
            fprintf(blit_log, "clip rect X1=%d Y1=%d X2=%d Y2=%d\n", ARGX(a1), ARGY(a1), ARGX(a2), ARGY(a2));
            break;

        case BLIT_CMD_SET_TRANS_COLOR:
            fprintf(blit_log, "trans color %d\n", ARGX(a3));
            break;

        case BLIT_CMD_SET_FONT:
            fprintf(blit_log, "font %x %d %d %d %d\n", a1, ARGX(a2), ARGY(a2), ARGX(a3), ARGY(a3));
            break;

        case BLIT_CMD_DRAW_CHAR:
            fprintf(blit_log, "draw char X=%d Y=%d CHAR=%d COLOR=%d BGCOL=%d\n", ARGX(a1), ARGX(a2), ARGY(a2), ARGX(a3), ARGY(a3));
            break;

        case BLIT_CMD_DRAW_LINE:
            fprintf(blit_log, "draw line X1=%d Y1=%d X2=%d Y2=%d COLOR=%d\n", ARGX(a1), ARGY(a1), ARGX(a2), ARGY(a2), ARGX(a3));
            break;

        default:
            fprintf(blit_log, "unknown blit op %d\n", op);
    }
}



// ================================================
//                  write_hwregs
// ================================================

static int blit1,blit2,blit3;

static void write_hwregs(unsigned int addr, int value, int mask) {

    switch(addr & 0xFFFFFFFC) {
        case 0xE0000000:
            fprintf(uart_log, "7-Segment = %06x\n", value&0xffffff);
            printf("7-Segment = %06x\n", value&0xffffff);
            break;

        case 0xE0000004:
            fprintf(uart_log, "LEDs = %x\n", value&0x3ff);
            printf("LEDs = %x\n", value&0x3ff);
            break;

        case 0xE0000010:
            fprintf(uart_log, "%c", value);
            printf("%c", value);
            break;

        case 0xE0000080:
            blit_op(value, blit1, blit2, blit3);
            break;

        case 0xE0000084:
            blit1 = (blit1 & ~mask) | (value & mask);
            break;

        case 0xE0000088:
            blit2 = (blit2 & ~mask) | (value & mask);
            break;

        case 0xE000008C:
            blit3 = (blit3 & ~mask) | (value & mask);
            break;

        default:
            printf("write_hwregs(%08x, %08x)\n", addr, value);
        break;
    }
}

// ================================================
//                  read_hwregs
// ================================================

static int read_hwregs(unsigned int addr) {
    switch(addr & 0xFFFFFFFC) {
        case 0xE0000010:   // UART TX
            return 0x3ff;  // Report the space in the fifo - fake it to always be empty

        case 0xE0000014:
            return -1;      // UART RX

        case 0xE0000030:   // Simulation flag
            return 1;      // Returns 1 in simulations, zero on real hardware

        case 0xE0000028:   // VGA_Y pos
            return 480;

        case 0xE0000080:    // Fake the blitter queue - say there is always space
            return 255;

        case 0xE0000084:
            return blit1;

        case 0xE0000088:
            return blit2;

        case 0xE000008C:
            return blit3;

        default:
            printf("read_hwregs(%08x)\n", addr);
            return 0xdeadbeef;

        break;
    }
}

// ================================================
//                  check_dmpu
// ================================================
// Test to see if a given memory access is allowed

static int check_dmpu(int access, int address) {
    if (status & STATUS_SUPERVISOR)
        return 1;       // Supervisor mode allows all accesses

    for(int i=0; i<8; i++) {
        if (!(dmpu[i] & DMPU_ENABLED))
            continue;   // Skip disabled entries
        if (!(dmpu[i] & access))
            continue;   // Skip entries that don't match the access type
        int size = (dmpu[i]>>8) & 0x15;
        int mask = 0xFFFFF000 << size;
        if ((address & mask) == (dmpu[i] & mask))
            return 1;   // Match
    }
    return 0;
}



// ================================================
//                  read_memory
// ================================================

static int read_memory(unsigned int addr) {
    if (addr < 0x4000000)
        return data_mem[addr>>2];
    else if (addr>=0xE0000000 && addr<0xE0001000)
        return read_hwregs(addr);
    else if (addr>=0xffff0000)
        return prog_mem[(addr & 0xffff)>>2];
    else
        return 0xBAADF00D;
}


// ================================================
//                  write_memory
// ================================================

static void write_memory(unsigned int addr, int value, int mask) {
    if (exception)
        return;
    if (addr < 0x4000000) {
        int a = addr >> 2;
        data_mem[a] = (data_mem[a] & ~mask) | (value & mask);
        if (trace_file)
            fprintf(trace_file, "[%08x] = %08x", addr, data_mem[a]);
    } else if (addr>=0xE0000000 && addr<0xE0001000) {
        write_hwregs(addr, value, mask);
        if (trace_file)
            fprintf(trace_file, "[%08x] = %08x", addr, value);
    } else if (addr>=0xffff0000) {
        int a = (addr & 0xffff) >> 2;
        prog_mem[a] = (prog_mem[a] & ~mask) | (value & mask);
    }
}

// ================================================
//                  write_memory_size
// ================================================

static void write_memory_size(unsigned int addr, int value, int size) {
    if (!check_dmpu(DMPU_WRITE, addr)) {
        raise_exception(CAUSE_STORE_ACCESS_FAULT, addr);
        return;
    }

    int mask = 0;
    int shift = (addr & 3) * 8;
    switch(size) {
        case 0: 
            // Byte
            mask = 0xff << shift; 
            value = (value & 0xff) << shift;
            break;
        case 1: 
            // Halfword
            if (addr & 1)
                raise_exception(CAUSE_STORE_ADDRESS_MISALIGNED, addr);
            mask = 0xffff << shift;
            value = (value & 0xffff) << shift;
            break;

        case 2: 
            // Word
            if (addr & 3)
                raise_exception(CAUSE_STORE_ADDRESS_MISALIGNED, addr);
            mask = 0xffffffff; 
            break;

        default:
            fatal("write_memory_size: invalid size %d", size);
    }

    write_memory(addr, value, mask);
}

// ================================================
//                  read_memory_size
// ================================================

static int read_memory_size(unsigned int addr, int size) {
    if (!check_dmpu(DMPU_READ, addr))
        raise_exception(CAUSE_LOAD_ACCESS_FAULT, addr);

    int value = read_memory(addr& 0xfffffffc);;
    int shift = (addr & 3) * 8;
    switch(size) {
        case 0:
            // Byte
            value = (value >> shift) & 0xff;
            if (value & 0x80)
                value = value | 0xffffff00;
            break;
        case 1:
            // Halfword
            if (addr & 1)
                raise_exception(CAUSE_LOAD_ADDRESS_MISALIGNED, addr);
            value = (value >> shift) & 0xffff;
            if (value & 0x8000)
                value = value | 0xffff0000;
            break;

        case 2:
            // Word
            if (addr & 3)
                raise_exception(CAUSE_LOAD_ADDRESS_MISALIGNED, addr);
            break;

        default:
            fatal("read_memory_size: invalid size %d", size);
    }
    return value;
}

// ================================================
//                  read_cfg
// ================================================

static int read_cfg(int cfg_reg) {
    int ret;
    switch(cfg_reg) {
        case CFG_REG_EPC:      ret = epc; break;
        case CFG_REG_ECAUSE:   ret = ecause; break;
        case CFG_REG_EDATA:    ret = edata; break;
        case CFG_REG_ESTATUS:  ret = estatus; break;
        case CFG_REG_ESCRATCH: ret = escratch; break;
        case CFG_REG_STATUS:   ret = status; break;
        case CFG_REG_IPC:      ret = ipc; break;
        case CFG_REG_ICAUSE:   ret = icause; break;
        case CFG_REG_ISTATUS:  ret = istatus; break;
        case CFG_REG_INTVEC:   ret = intvec; break;
        case CFG_REG_TIMER:    ret = int_timer; break;
        case CFG_REG_DMPU0:    ret = dmpu[0]; break;
        case CFG_REG_DMPU1:    ret = dmpu[1]; break;
        case CFG_REG_DMPU2:    ret = dmpu[2]; break;
        case CFG_REG_DMPU3:    ret = dmpu[3]; break;
        case CFG_REG_DMPU4:    ret = dmpu[4]; break;
        case CFG_REG_DMPU5:    ret = dmpu[5]; break;
        case CFG_REG_DMPU6:    ret = dmpu[6]; break;
        case CFG_REG_DMPU7:    ret = dmpu[7]; break;
        default: ret = 0;
    }
    return ret;
}

// ================================================
//                  write_cfg
// ================================================

static void write_cfg(int cfg_reg, int value) {
    switch(cfg_reg) {
        case CFG_REG_EPC:      epc      = value;            break;
        case CFG_REG_ECAUSE:   ecause   = value & 0xFF;     break;
        case CFG_REG_EDATA:    edata    = value;            break;
        case CFG_REG_ESTATUS:  estatus  = value & 0xFF;     break;
        case CFG_REG_ESCRATCH: escratch = value;            break;
        case CFG_REG_STATUS:   status   = value & 0xFF;     break;
        case CFG_REG_IPC:      ipc = value; break;
        case CFG_REG_ICAUSE:   icause = value & 0xFF; break;
        case CFG_REG_ISTATUS:  istatus = value & 0xFF; break;
        case CFG_REG_INTVEC:   intvec = value; break;
        case CFG_REG_TIMER:    int_timer = value; break;
        case CFG_REG_DMPU0:    dmpu[0] = value; break;
        case CFG_REG_DMPU1:    dmpu[1] = value; break;
        case CFG_REG_DMPU2:    dmpu[2] = value; break;
        case CFG_REG_DMPU3:    dmpu[3] = value; break;
        case CFG_REG_DMPU4:    dmpu[4] = value; break;
        case CFG_REG_DMPU5:    dmpu[5] = value; break;
        case CFG_REG_DMPU6:    dmpu[6] = value; break;
        case CFG_REG_DMPU7:    dmpu[7] = value; break;

    }
}

// ================================================
//                  execute_instruction
// ================================================

static void execute_instruction(int instr) {
    int k = (instr >> 26) & 0x3f;
    int i =  (instr >> 23) & 0x7;
    int d = (instr >> 18) & 0x1f;
    int a = (instr >> 13) & 0x1f;
    int c = (instr >> 5) & 0xff; 
    if (c&0x80)
        c = c | 0xffffff00; // sign extend
    int b = (instr >> 0) & 0x1f;

    int n13  = (c<<5) | b;
    int n13s = (c<<5) | d;
    int n21 = (c<<13) | (i<<10) | (a<<5) | b;
    int tmp;

    switch (k) {
        case KIND_ALU:  set_reg(d, alu_op(i, reg[a], reg[b], c));  break;
        case KIND_ALUI: set_reg(d, alu_op(i, reg[a], n13, c)); break;
        case KIND_BRA:  if (branch_op(i, reg[a], reg[b])) {
                            pc = pc + n13s * 4;
                            if (trace_file)
                                fprintf(trace_file, "-> %s", find_label(pc));
                        }
                        break;
        case KIND_LD:   set_reg(d, read_memory_size(reg[a] + n13, i)); break;
        case KIND_ST:   write_memory_size(reg[a] + n13s, reg[b], i); break;
        case KIND_JMP:  set_reg(d,pc); 
                        pc += n21*4; 
                        if (trace_file)
                            fprintf(trace_file, "-> %s", find_label(pc));
                        break;
        case KIND_JMPR: tmp = pc;
                        pc = reg[a] + 4*n13; 
                        set_reg(d,tmp);
                        if (trace_file)
                            fprintf(trace_file, "-> %s", find_label(pc));                        
                        break;
        case KIND_LDU:  set_reg(d, n21<<11); break;
        case KIND_LDPC: set_reg(d, pc + n21*4); break;
        case KIND_MUL:  set_reg(d, mul_op(i, reg[a],  reg[b])); break;
        case KIND_MULI: set_reg(d, mul_op(i, reg[a],  n13)); break;
        case KIND_CFG:  int tmp = read_cfg(n13);
                        if (i==1)
                            write_cfg(n13, reg[a]);
                        if (i==0 || i==1)
                            set_reg(d,tmp);
                        if (i==2) {  // RTE
                            if (n13 & 1) {
                                // RTI
                                status = istatus;
                                pc = ipc;
                            } else {
                                // RTE
                                status = estatus;
                                pc = epc;
                            }
                            if (trace_file)
                                fprintf(trace_file, "-> %s", find_label(pc));                        
                        } 
                        if (i==3) {  // SYS
                            raise_exception(CAUSE_SYSTEM_CALL, n13);
                        }
                        break;
        case KIND_IDX:  set_reg(d, idx_op(i, reg[a], reg[b])); break;
        default: raise_exception(CAUSE_ILLEGAAL_INSTRUCTION, instr);
    }
}

// ================================================
//                  execute
// ================================================

void execute() {
    for(int i=0; i<0x1000000; i++)
        data_mem[i] = 0xBAADF00D;

    pc = 0xffff0000;
    int timeout = 10000;
    reg[31] = 0x4000000;
    reg_log = fopen("sim_reg.log", "w");
    uart_log = fopen("sim_uart.log", "wb");
    blit_log = fopen("sim_blit.log", "wb");

    while (timeout>0 && pc!=0) {
        exception = 0;

        if (--int_timer == 0)
            raise_interrupt(ICAUSE_TIMER);

        int instr = read_memory(pc);
        if (trace_file) 
            fprintf(trace_file, "%08x: %-40s", pc, disassemble_line(instr,pc+4));
        pc += 4;
        execute_instruction(instr);
        if (trace_file) 
            fprintf(trace_file, "\n");
        timeout--;    
    }
    if (timeout==0)
        printf("Timeout\n");
}
