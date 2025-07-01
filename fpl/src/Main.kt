import java.io.FileNotFoundException
import java.io.FileReader
import java.io.FileWriter

var debug = false

val stdLibFiles = listOf("stdlib/memory.fpl",
                         "stdlib/stringBuilder.fpl",
                         "stdlib/list.fpl",
                         "stdlib/bitVector.fpl",
                         "stdlib/printf.fpl")
val osStdLibFiles = listOf("stdlib/stringBuilder.fpl","stdlib/list.fpl","stdlib/bitVector.fpl")
const val stdLibPath = "C:\\Users\\simon\\falcon3\\fpl\\"
const val startFile =  "${stdLibPath}stdlib\\start_stdlib.f32"

fun main(args:Array<String>) {
    Log.clear()
    val files = mutableListOf<String>()
    val asmFiles = mutableListOf<String>()
    var stopAt = StopAt.HEXFILE
    var noStdLib = false
    var osProject = false
    for (arg in args) {
        when (arg) {
            "-d" -> debug = true
            "-parse" -> stopAt = StopAt.PARSE
            "-typecheck" -> stopAt = StopAt.TYPECHECK
            "-codegen" -> stopAt = StopAt.CODEGEN
            "-execute" -> stopAt = StopAt.EXECUTE
            "-assembly" -> stopAt = StopAt.ASSEMBLY
            "-noStdlib" -> noStdLib = true
            "-os" -> osProject = true
            else -> if (arg.startsWith("-")) {
                println("Unknown option $arg")
                return
            } else if (arg.endsWith(".fpl"))
                files.add(arg)
            else if (arg.endsWith(".f32"))
                asmFiles.add(arg)
            else
                Log.error("Unknown argument $arg")
            }
        }

    try {
        val sf = if (noStdLib) emptyList() else
                          if (osProject) osStdLibFiles
                          else stdLibFiles
        val assemblyFiles = (if (noStdLib) emptyList() else listOf(startFile) ) + asmFiles

        val lexers = sf.map { Lexer(it, FileReader(stdLibPath+it)) } + files.map { Lexer(it, FileReader(it)) }
        println(compile(lexers, stopAt, assemblyFiles, true))

    } catch (e: FileNotFoundException) {
        println("File not found ${e.message}")
    }
}


fun runAssembler(filenames:List<String>) {
    val process = ProcessBuilder("f32asm.exe", *filenames.toTypedArray())
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()

    val exitCode = process.waitFor()
    if (exitCode != 0)
        Log.error(process.inputStream.bufferedReader().readText())
}

fun runProgram(stopAtException: Boolean) : String {
    val process = ProcessBuilder("f32sim", "-t", "-a" , "asm.hex")
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
    val exitCode = process.waitFor()
    if (exitCode != 0)
        Log.error("Execution failed with exit code $exitCode")
    return process.inputStream.bufferedReader().readText().replace("\r\n", "\n")
}


enum class StopAt{PARSE, TYPECHECK, CODEGEN, REG_ALLOC, ASSEMBLY, HEXFILE, EXECUTE}

fun compile(lexers:List<Lexer>, stopAt: StopAt, assemblyFiles:List<String> = emptyList(),
            stopAtException: Boolean=false
            ) : String {
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
    val ret = runProgram(stopAtException)
    return ret
}