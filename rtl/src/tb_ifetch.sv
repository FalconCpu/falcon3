`timescale 1ns / 1ps

module tb_cpu_ifetch();

    // --- Parameters ---
    parameter CLK_PERIOD = 10; // Clock period in ns
    parameter MEM_LATENCY = 2; // Memory access latency in clock cycles (1 cycle = 1 clock period)

    // --- Testbench Signals (Inputs to DUT) ---
    logic       clock;
    logic       reset;
    logic       stall;          // External stall from later pipeline stages
    logic       p2_bubble;      // Feedback from Decoder: not ready to accept, causes IFetch to re-fetch/bubble
    logic [31:0] p3_jump_addr;
    logic       p3_jump_taken;

    // --- Testbench Signals (Outputs from DUT - Connect to DUT inputs) ---
    logic       cpui_request;
    logic [31:0] cpui_addr;
    logic [31:0] cpui_rdata;    // This will be driven by our memory model
    logic       cpui_ack;      // This will be driven by our memory model

    // --- Testbench Signals (Outputs from DUT - Monitor these for correctness) ---
    logic [31:0] p2_instr;
    logic [31:0] p2_pc;
    logic       p2_instr_valid;

    // --- Internal Memory Model Signals ---
    // A simple instruction memory array. Let's make it 1KB of 32-bit words (4KB total)
    logic [31:0] instruction_mem [0:1023]; // Address range 0x0 to 0x0FFF (word addresses)

    // Memory model internal state
    logic         mem_request_active;
    logic [31:0]  mem_current_addr;
    logic [31:0]  mem_read_data;
    logic         mem_ack_internal; // Used to generate cpui_ack after latency
    logic [7:0]   mem_latency_countdown; // Counter for memory latency

    // --------------------------------------------------------------------
    // Instantiate the Design Under Test (DUT)
    // --------------------------------------------------------------------
    cpu_ifetch UUT (
        .clock(clock),
        .reset(reset),
        .stall(stall),
        .p2_bubble(p2_bubble),

        .cpui_request(cpui_request),
        .cpui_addr(cpui_addr),
        .cpui_rdata(cpui_rdata), // Connect to memory model output
        .cpui_ack(cpui_ack),     // Connect to memory model output

        .p2_instr(p2_instr),
        .p2_pc(p2_pc),
        .p2_instr_valid(p2_instr_valid),

        .p3_jump_addr(p3_jump_addr),
        .p3_jump_taken(p3_jump_taken)
    );

    // --------------------------------------------------------------------
    // Clock Generator
    // --------------------------------------------------------------------
    initial begin
        clock = 0;
        forever #(CLK_PERIOD / 2) clock = ~clock;
    end

    // --------------------------------------------------------------------
    // Instruction Memory Model
    // Simulates a memory that responds after MEM_LATENCY cycles
    // --------------------------------------------------------------------
    initial begin
        // Initialize memory with dummy instructions
        // Instruction format: 0xXX_YY_ZZ_AA where AA is word address offset
        for (int i = 0; i < 1024; i++) begin
            instruction_mem[i] = {24'hABCDEF, i[7:0]}; // Example instruction
            // You can put specific instruction opcodes here later
        end

        mem_request_active  = 1'b0;
        mem_current_addr    = 32'h0;
        mem_read_data       = 32'h0;
        mem_ack_internal    = 1'b0;
        mem_latency_countdown = 0;
    end

    // Memory response logic
    always_ff @(posedge clock) begin
        // Clear ACK by default unless generated this cycle
        mem_ack_internal <= 1'b0;
        // cpui_rdata       <= mem_read_data; // Always output previous read data until new valid

        if (reset) begin
            mem_request_active  <= 1'b0;
            mem_latency_countdown <= 0;
        end else begin
            if (mem_request_active) begin // If a request is active
                if (mem_latency_countdown > 0) begin
                    mem_latency_countdown <= mem_latency_countdown - 1; // Decrement counter
                end else begin // Latency reached!
                    mem_ack_internal   <= 1'b1; // Assert ACK
                    cpui_rdata         <= instruction_mem[mem_current_addr[11:2]]; // Read data (word addressing)
                    mem_request_active <= 1'b0; // Request handled
                end
            end

            // Check for new request from CPU IFetch
            if (cpui_request && !mem_request_active) begin // New request detected
                mem_request_active  <= 1'b1;           // Mark as active
                mem_current_addr    <= cpui_addr;      // Capture address
                mem_latency_countdown <= MEM_LATENCY - 1; // Start countdown (0-indexed)
                // $display("[%0t] MEM: New request for addr 0x%h, latency %0d cycles", $time, cpui_addr, MEM_LATENCY);
            end
        end
    end

    // Assign internal memory signals to DUT ports
    assign cpui_ack   = mem_ack_internal;


    // --------------------------------------------------------------------
    // Test Stimulus (Initial Block)
    // --------------------------------------------------------------------
    initial begin
        // Dump waves for debugging
        $dumpfile("dump.vcd");
        $dumpvars(0, tb_cpu_ifetch); // Dump all signals in this module's hierarchy

        $display("Simulation started");

        // --- 1. Initial Reset ---
        reset = 1'b1;
        stall = 1'b0;
        p2_bubble = 1'b0;
        p3_jump_taken = 1'b0;
        p3_jump_addr = 32'h0;
        # (CLK_PERIOD * 2); // Hold reset for 2 clock cycles

        reset = 1'b0;
        $display("[%0t] TEST: Reset deasserted. Starting fetch.", $time);
        # (CLK_PERIOD * 1); // Wait one cycle after reset deassertion

        // --- 2. Normal Sequential Fetches ---
        $display("[%0t] TEST: Normal sequential fetches.", $time);
        # (CLK_PERIOD * 10); // Allow several instructions to fetch

        // --- 3. Introduce External Stall ---
        $display("[%0t] TEST: Introducing external stall.", $time);
        stall = 1'b1;
        # (CLK_PERIOD * 5); // Hold stall for 5 cycles
        stall = 1'b0;
        $display("[%0t] TEST: External stall removed.", $time);
        # (CLK_PERIOD * 5); // See pipeline recover

        // --- 4. Introduce Decoder Bubble (back-pressure) ---
        $display("[%0t] TEST: Introducing decoder bubble.", $time);
        p2_bubble = 1'b1;
        # (CLK_PERIOD * 5); // Hold bubble for 5 cycles
        p2_bubble = 1'b0;
        $display("[%0t] TEST: Decoder bubble removed.", $time);
        # (CLK_PERIOD * 5); // See pipeline recover

        // --- 5. Jump/Branch Test ---
        $display("[%0t] TEST: Triggering a jump.", $time);
        p3_jump_addr  = 32'h0000_1000; // Jump to a new address
        p3_jump_taken = 1'b1;
        # (CLK_PERIOD * 1); // Jump signal active for one cycle

        p3_jump_taken = 1'b0; // Deassert jump signal
        # (CLK_PERIOD * 5); // Observe new fetch path

        // --- 6. Combined Stall and Bubble to test Skid Buffer ---
        // Expect one instruction to be skidded
        //$display("[%0t] TEST: Combined stall and bubble to test skid buffer.", $time);
        stall = 1'b1;       // External stall
        p2_bubble = 1'b1;   // Decoder also stalled
        # (CLK_PERIOD * (MEM_LATENCY + 2)); // Allow a fetch to complete while stalled/bubbled
                                           // (Expect a skid buffer entry here)

        stall = 1'b0;       // Remove external stall
        p2_bubble = 1'b0;   // Remove decoder bubble
        //$display("[%0t] TEST: Stall and bubble removed. Expect skid instruction to flow.", $time);
        # (CLK_PERIOD * 5); // Should see the skidded instruction come out, then normal fetches

        // --- 7. Potential Skid Buffer Overflow Test (If you want to try to trigger it) ---
        // This is tricky and depends on precise timing.
        // It's when cpui_ack comes in AND skid_valid_reg is already true AND (stall || p2_bubble) is true.
        // You might need to adjust MEM_LATENCY or timing to hit this exact condition.
        // For MEM_LATENCY=2:
        // C0: IF starts fetch for PC_A. p2_bubble=0. cpui_request=1.
        // C1: IF waits for ACK. p2_bubble=1 (start stall). cpui_request=0 (no new req).
        // C2: MEM_ACK=1 for PC_A. IF wants to put PC_A in skid. IF is stalled. Skid buffer is empty. OK.
        // C3: IF starts fetch for PC_A+4. p2_bubble=1. cpui_request=1.
        // C4: IF waits for ACK. p2_bubble=1. cpui_request=0.
        // C5: MEM_ACK=1 for PC_A+4. IF wants to put PC_A+4 in skid. Skid buffer *has* PC_A. OVERFLOW!
        // This scenario might be difficult to reliably hit with simple testbench.
        // The current error signal is useful for finding it though.

        $display("[%0t] TEST: Trying to induce skid buffer overflow (might not always trigger depending on MEM_LATENCY).", $time);
        stall = 1'b1;
        p2_bubble = 1'b1;
        # (CLK_PERIOD * 1); // Start stall/bubble
        // Wait for first fetch to complete and enter skid buffer
        # (CLK_PERIOD * (MEM_LATENCY + 1));
        // Now, another fetch will complete while skid buffer is full.
        // Make sure IF can issue another request. This requires !in_progress_reg (from prev cycle).
        // This is complex. For now, rely on error detection.
        # (CLK_PERIOD * (MEM_LATENCY + 1));
        stall = 1'b0;
        p2_bubble = 1'b0;
        $display("[%0t] TEST: Attempted overflow test finished.", $time);
        # (CLK_PERIOD * 5);

        // --- 8. Final Cleanup and Finish ---
        $display("[%0t] Simulation finished", $time);
        # (CLK_PERIOD * 2); // Allow final operations to settle
        $finish; // End simulation
    end

    // --------------------------------------------------------------------
    // Monitoring and Debugging
    // --------------------------------------------------------------------
    always @(posedge clock) begin
        if (p2_instr_valid)
            $display("[%0t] Instruction fetched: 0x%h at PC 0x%h", $time, p2_instr, p2_pc);
        // $display("[%0t] CLK: %b, RST: %b, STALL: %b, BUBBLE: %b, JUMP_T: %b, JUMP_A: 0x%h | REQ: %b, ADDR: 0x%h, RDATA: 0x%h, ACK: %b | P2_INSTR: 0x%h, P2_PC: 0x%h, P2_VALID: %b",
        //          $time, clock, reset, stall, p2_bubble, p3_jump_taken, p3_jump_addr,
        //          cpui_request, cpui_addr, cpui_rdata, cpui_ack,
        //          p2_instr, p2_pc, p2_instr_valid);
    end

endmodule