import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.FileReader
import java.io.StringReader

class OsDevTest {
    private val stdlibFiles = listOf("stdlib/memory.fpl")
    private val stdLibAsm = listOf("stdlib/start_stdlib.f32")


    fun runTest(prog: String, expected: String) {
        val stdLibLexers = stdlibFiles.map { Lexer(it, FileReader(it)) }
        val lexer = Lexer("test.fpl", StringReader(prog))
        val output = compile(stdLibLexers + lexer, StopAt.EXECUTE, stdLibAsm)
        assertEquals(expected, output)
    }

    @Test
    fun helloWorld() {
        val prog = """
            class Cat(val name:String, val age:Int)
                fun free()
                    print("Freeing ", name, "\n")
            
            fun main()
                initializeMemorySystem()
                print("Initial freelist\n")
                dumpMemorySystem()
        
                print("\nAllocating some blocks\n")
                val x = new Array[1,2,3,4]
                print("x = ", x as Int, "\n")              
                val y = new Cat("Fred", 5)
                print("y = ", y as Int, "\n")
                
                dumpMemorySystem()
                
                print("\nFreeing some blocks\n")
                free x
                free y
                dumpMemorySystem()
                
        """.trimIndent()

        val expected = """
            Hello world
        """.trimIndent()

        runTest(prog, expected)
    }
}