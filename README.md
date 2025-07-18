Thie is my third attempt at building a computer from scratch.

It runs on a CycloneV FPGA (on a Terassic DE1-SOC board).

It features a 100Mhz custom CPU (the F32), SDRAM memory, VGA display - with 
blitter to accelerate 2D graphics operations and hardware layering
to compose multiple windows on screen. 

The CPU is somewhat inspired by RISC-V, but I tried to design everything from scratch.

There is an assembler/disassembler/emulator for it in the binutils directory.

The CPU and peripherals are implemented in systemVerilog and can be found in the 'rtl' directory. 
The CPU is a fairly standard 5 stage pipeline (Instruction Fetch, Decode, Execute, Complete, WriteBack).
Exceptions and Interupts are implemented. It currently has an instruction cache, but not yet a data cache.
It has a memory protection unit (rather than a full blown paged memory management) - which can allow user 
code read/write/execute priveledges on up to 8 blocks of memory each a power of 2 size, at a natural alignment for its size.

Then I have a compiler which translates my own 'fpl' programming language into f32 assembly. 
Think of C semantics, but with Kotlin syntax, and a few bits borrowed from Python and Zig.
The language is strongly typed, with path dependant typing. 

Finally I have started to write an operating system in FPL. Currently it has syscalls, memory alocation, and co-operative multitasking.
It can load programs over the UART from the host PC.  I'm starting to implement a GUI windowing system - but very early days. 
