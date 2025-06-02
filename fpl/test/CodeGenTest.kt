import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class CodeGenTest {
    fun runTest(prog: String, expected:String) {
        val lexer = Lexer("test.fpl", StringReader(prog))
        val output = compile(listOf(lexer), StopAt.CODEGEN)
        assertEquals(expected, output)
    }

    @Test
    fun simpleDeclaration() {
        val prog = """
            fun main() -> Int
                val x = 1
                val y = 2
                return x + y
        """.trimIndent()

        val expected = """
            Function main
            start
            ld T0, 1
            ld x, T0
            ld T1, 2
            ld y, T1
            ADD_I T2, x, y
            ld R8, T2
            jmp L0
            L0:
            ret


        """.trimIndent()

        runTest(prog, expected)
    }
}