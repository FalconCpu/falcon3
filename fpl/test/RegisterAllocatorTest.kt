import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class RegisterAllocatorTest {
    fun runTest(prog: String, expected:String) {
        val lexer = Lexer("test.fpl", StringReader(prog))
        val output = compile(listOf(lexer), StopAt.REG_ALLOC)
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
            ld R8, 3
            ret


        """.trimIndent()

        runTest(prog, expected)
    }


}