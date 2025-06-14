import java.io.FileWriter

var debug = false
//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {
}


fun runAssembler(filenames:List<String>) {
    val process = ProcessBuilder("f32asm.exe", *filenames.toTypedArray())
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()

    val exitCode = process.waitFor()
    if (exitCode != 0)
        Log.error(process.inputStream.bufferedReader().readText())
}

fun runProgram() : String {
    val process = ProcessBuilder("f32sim", "-t", "asm.hex")
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
    val exitCode = process.waitFor()
    if (exitCode != 0)
        Log.error("Execution failed with exit code $exitCode")
    return process.inputStream.bufferedReader().readText().replace("\r\n", "\n")
}


enum class StopAt{PARSE, TYPECHECK, CODEGEN, REG_ALLOC, ASSEMBLY, HEXFILE, EXECUTE}

fun compile(lexers:List<Lexer>, stopAt: StopAt, assemblyFiles:List<String> = emptyList()) : String {
    Log.clear()
    allFunctions.clear()
    allValues.clear()
    allClasses.clear()
    allGlobalVars.clear()
    ValueString.allStrings.clear()

    // Parse the input files
    val astTop = Parser.parse(lexers)
    if (Log.hasErrors())   return Log.getErrors()
    if (stopAt == StopAt.PARSE) return astTop.prettyPrint()

    // Type check the program
    val tstTop = astTop.typeCheck()
    if (Log.hasErrors())   return Log.getErrors()
    if (stopAt == StopAt.TYPECHECK) return tstTop.prettyPrint()

    // Generate IR code for the program
    tstTop.codeGen()
    if (Log.hasErrors())   return Log.getErrors()
    if (stopAt == StopAt.CODEGEN) return allFunctions.dump()

    // Run the backend to optimize the IR code and allocate registers
    allFunctions.runBackend()
    if (Log.hasErrors())   return Log.getErrors()
    if (stopAt == StopAt.REG_ALLOC) return allFunctions.dump()

    // Generate assembly code
    val sb =  StringBuilder()
    genAssemblyHeader(sb)
    for (func in allFunctions)
        func.genAssembly(sb)
    allClasses.generateClassDescriptors(sb)
    allValues.emit(sb)
    if (Log.hasErrors())   return Log.getErrors()
    if (stopAt == StopAt.ASSEMBLY) return sb.toString()

    // Save the assembly code to a file and run the assembler
    val asmFile = FileWriter("asm.f32")
    asmFile.write(sb.toString())
    asmFile.close()
    runAssembler(assemblyFiles + "asm.f32")
    if (Log.hasErrors())             return Log.getErrors()
    if (stopAt == StopAt.HEXFILE)   {
        println("HEX file created")
        return ""
    }

    // Run the simulated machine
    val ret = runProgram()
    return ret
}