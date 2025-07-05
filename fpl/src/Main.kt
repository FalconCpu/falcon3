import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.FileWriter

var debug = false

val stdLibFiles = listOf("memory.fpl",
                         "stringBuilder.fpl",
                         "list.fpl",
                         "bitVector.fpl",
                         "printf.fpl")
val osStdLibFiles = listOf("stdlib/stringBuilder.fpl","stdlib/list.fpl","stdlib/bitVector.fpl")
const val stdLibPath = "C:\\Users\\simon\\falcon3\\falconos\\stdlib\\"
const val startFile =  "${stdLibPath}start.f32"


fun main(args:Array<String>) {
    Log.clear()
    val files = mutableListOf<String>()
    val asmFiles = mutableListOf<String>()
    var stopAt = StopAt.HEXFILE
    var noStdLib = false
    var osProject = false

    // If no arguments provided, try to load project file
    val argsx = if (args.isEmpty())
        parseProjectFile()
    else
        args.toList()

    var i = 0
    while (i < argsx.size) {
        val arg = argsx[i]
        when (arg) {
            "-d" -> debug = true
            "-parse" -> stopAt = StopAt.PARSE
            "-typecheck" -> stopAt = StopAt.TYPECHECK
            "-codegen" -> stopAt = StopAt.CODEGEN
            "-execute" -> stopAt = StopAt.EXECUTE
            "-assembly" -> stopAt = StopAt.ASSEMBLY
            "-bin" -> stopAt = StopAt.BINFILE
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
        i++
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

fun parseProjectFile(): List<String> {
    try {
        val args = mutableListOf<String>()
        println("Debug: Current working directory: ${System.getProperty("user.dir")}")
        val projectFile = File("files.fplprj")
        val commentRegex = "#.+".toRegex()
        for (line in projectFile.readLines()) {
            val trimmedLine = line.replace(commentRegex,"").trim()
            if (trimmedLine.isNotEmpty())
                args += trimmedLine.split("\\s+".toRegex())
        }
        return args
    } catch (e: Exception) {
        println("Error reading project file: ${e.message}")
        return emptyList()
    }
}

fun runAssembler(filenames:List<String>, format:String) {
    val process = ProcessBuilder("f32asm.exe", format,  *filenames.toTypedArray())
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


enum class StopAt{PARSE, TYPECHECK, CODEGEN, REG_ALLOC, ASSEMBLY, HEXFILE, BINFILE, EXECUTE}

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
    astTop.writeSymbolMap()
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

    // Save the assembly code to a file
    val asmFile = FileWriter("asm.f32")
    asmFile.write(sb.toString())
    asmFile.close()

    // If target is a binary file - run assembler and stop here
    if (stopAt == StopAt.BINFILE) {
        runAssembler(assemblyFiles + "asm.f32", "-bin")
        if (Log.hasErrors())             return Log.getErrors()
        return ""
    }

    runAssembler(assemblyFiles + "asm.f32","-hex")
    if (Log.hasErrors())             return Log.getErrors()
    if (stopAt == StopAt.HEXFILE)   {
        // Check if the hex file was created and get its size
        val hexFile = File("asm.hex")
        if (hexFile.exists()) {
            val fileSize = (hexFile.length()/9)*4  // 8Hex digits + 1 byte for LF -> 4 bytes of code
            println("HEX file created: asm.hex ($fileSize bytes)")
        } else
            error("Unknown error generating HEX file")
        return ""
    }

    // Run the simulated machine
    val ret = runProgram(stopAtException)
    return ret
}