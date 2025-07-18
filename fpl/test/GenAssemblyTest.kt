import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class GenAssemblyTest {
    fun runTest(prog: String, expected:String) {
        val lexer = Lexer("test.fpl", StringReader(prog))
        val output = compile(listOf(lexer), StopAt.ASSEMBLY)
        assertEquals(expected, output)
    }

    @Test
    fun simpleDeclaration() {
        val prog = """
            fun fred(a:Int, b:Int) -> Int
                val x = a+1
                val y = b-1
                return x * y
                
            fun main() -> Int
                val x = fred(1,2)
                return x
        """.trimIndent()

        val expected = """
            # Generated by Falcon Compiler
            /fred(Int,Int):
            # a = R1
            # b = R2
            # x = R1
            # y = R2
            add R1, R1, 1
            sub R2, R2, 1
            mul R8, R1, R2
            ret

            /main():
            # x = R8
            sub SP, SP, 4
            stw R30, SP[0]
            ld R1, 1
            ld R2, 2
            jsr /fred(Int,Int)
            ldw R30, SP[0]
            add SP, SP, 4
            ret


        """.trimIndent()

        runTest(prog, expected)
    }


}