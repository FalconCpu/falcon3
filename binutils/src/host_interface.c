#define _CRT_SECURE_NO_WARNINGS
#include <stdio.h>
#include <stdarg.h>
#include <windows.h>
#include <fcntl.h>
#include <conio.h> // For _kbhit and _getch
#include <errno.h>
// #include <unistd.h> // For fcntl on Windows

typedef const char* string;

#define COM_PORT "COM3"
#define BAUD_RATE 2000000

static HANDLE hSerial = INVALID_HANDLE_VALUE;

/// -----------------------------------------------------
///                       fatal
/// -----------------------------------------------------
/// Report an error and exit the program

static void fatal(string message, ...) {
    va_list va;
    va_start(va, message);
    printf("FATAL: ");
    vprintf(message, va);
    printf("\n");

    if (hSerial != INVALID_HANDLE_VALUE)
        CloseHandle(hSerial);
    exit(20);
}

/// -----------------------------------------------------
///                       open_com_port
/// -----------------------------------------------------

static void open_com_port() {
    DCB dcbSerialParams = {0};
    COMMTIMEOUTS timeouts = {0};

    // Open the serial port
    hSerial = CreateFile(COM_PORT, GENERIC_READ | GENERIC_WRITE, 0, NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);

    if (hSerial == INVALID_HANDLE_VALUE)
        fatal("Error opening serial port.");

    // Set serial port parameters
    dcbSerialParams.DCBlength = sizeof(dcbSerialParams);
    if (!GetCommState(hSerial, &dcbSerialParams))
        fatal("Error getting serial port state.\n");

    dcbSerialParams.BaudRate = BAUD_RATE;    // Set your baud rate here
    dcbSerialParams.ByteSize = 8;            // 8-bit data
    dcbSerialParams.StopBits = TWOSTOPBITS;  // Two stop bit
    dcbSerialParams.Parity = NOPARITY;       // No parity
    if (!SetCommState(hSerial, &dcbSerialParams))
        fatal("Error setting serial port state.\n");

    // Set timeouts
    timeouts.ReadIntervalTimeout = 50;
    timeouts.ReadTotalTimeoutConstant = 50;
    timeouts.ReadTotalTimeoutMultiplier = 10;
    timeouts.WriteTotalTimeoutConstant = 50;
    timeouts.WriteTotalTimeoutMultiplier = 10;
    if (!SetCommTimeouts(hSerial, &timeouts))
        fatal("Error setting timeouts.\n");
}

/// -----------------------------------------------------------------
///                    read_from_com_port
/// -----------------------------------------------------------------
/// Reads a byte from the com port, waits until data is availible
/// Returns the byte read, or -1 for an error

static int read_from_com_port() {

    char readBuffer[1];
    DWORD bytesRead = 0;

    if (!ReadFile(hSerial, readBuffer, 1, &bytesRead, NULL))
        return -1;

    if (bytesRead==0)
        return -1;

    return readBuffer[0] & 0xff;
}

/// -----------------------------------------------------------------
///                    read word from com port
/// -----------------------------------------------------------------

static int read_word_from_com_port() {
    int c1 = read_from_com_port();
    int c2 = read_from_com_port();
    int c3 = read_from_com_port();
    int c4 = read_from_com_port();
    if (c1==-1 || c2==-1 || c3==-1 || c4==-1) {
        printf("Error reading from com port\n");
        return -1;
    }
    return (c1&0xff) | ((c2&0xff)<<8) | ((c3&0xff)<<16) | ((c4&0xff)<<24);
}

/// -----------------------------------------------------------------
///                    send_file_to_com_port
/// -----------------------------------------------------------------
/// send the boot rom to the com port

static void send_boot_image(char* file_name) {
    FILE *fh = fopen(file_name, "r");
    if (fh==0)
        fatal("Cannot open file '%s'", file_name);

    int buffer[16384];      // allocate a 64kB

    char line[100];
    int num_words = 0;
    int crc = 0;
    buffer[num_words++] = 0x010002B0; // start marker
    buffer[num_words++] = 0x00000000; // number of words - filled in later

    while( fgets(line, sizeof(line), fh) != NULL ) {
        int c;
        sscanf(line, "%x", &c);
        buffer[num_words++] = c;
        crc += c;
    }
    fclose(fh);
    buffer[num_words++] = crc;

    if (num_words==0)
        fatal("No data in file '%s'", file_name);

    buffer[1] = num_words*4-12;     // Size of data in bytes

    // Send the data
    DWORD bytesWritten = 0;
    if (!WriteFile(hSerial, buffer, num_words*4, &bytesWritten, NULL) || bytesWritten != num_words*4)
        fatal("Error sending program data");
    printf("Sent %ld bytes\n", bytesWritten);

    FILE* fh2 = fopen("uart.txt","w");
    char *cb = (char*)buffer;
    for(int i = 0; i<bytesWritten; i++) {
        fprintf(fh2,"%02x\n", cb[i] & 0xff);
    }
    fclose(fh);
}



/// -----------------------------------------------------------------
///                    command_mode
/// -----------------------------------------------------------------
/// When the FPGA sends an 0xB0 byte we enter command mode.
/// All commands begin with the byte string 0xB0 0xB1 0xB2 with 
/// next byte being the command.
/// If we receive any violations of this we exit command mode back to
/// normal operation mode.

static void command_mode() {
    // The first 0xB0 has already been read by the time we get here

    int c1 = read_from_com_port();
    int c2 = read_from_com_port();
    int c3 = read_from_com_port();
    int c = (0xB0) | (c1<<8) | (c2<<16) | (c3<<24);
    switch(c) {
        case 0x000002B0:       send_boot_image("asm.hex");       break;

        default:
            printf("Unknown command %x\n", c);
            break;
    }
    return;
}


/// -----------------------------------------------------------------
///                    run_loop
/// -----------------------------------------------------------------

static void run_loop() {
    while(1) {
        int c = read_from_com_port();
        if (c==0xB0)
            command_mode();
        else if (c!=-1)
            printf("%c", c);
    }
}


/// -----------------------------------------------------------------
///                    main
/// -----------------------------------------------------------------

int main(int argc, char** argv) {
    open_com_port();
    run_loop();

    CloseHandle(hSerial);
}
