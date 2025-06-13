import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.FileReader
import java.io.StringReader

class OsComponentTest {
    private val stdlibFiles = listOf("falconos/exception.fpl")
    private val stdLibAsm = listOf("falconos/start.f32")


    fun runTest(prog: String, expected: String) {
        val stdLibLexers = stdlibFiles.map { Lexer(it, FileReader(it)) }
        val lexer = Lexer("test.fpl", StringReader(prog))
        val output = compile(stdLibLexers + lexer, StopAt.EXECUTE, stdLibAsm)
        assertEquals(expected, output)
    }

    @Test
    fun firstTaskTest() {
        val prog = """
            fun main()
                var a = 10
        """.trimIndent()

        val expected = """
            """.trimIndent()
        runTest(prog, expected)
    }
}