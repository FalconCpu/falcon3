import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class TypeCheckTest {
    fun runTest(prog: String, expected:String) {
        val lexer = Lexer("test.fpl", StringReader(prog))
        val output = compile(listOf(lexer), StopAt.TYPECHECK)
        assertEquals(expected, output)
    }

    @Test
    fun simpleDeclaration() {
        val prog = """
            val x = 1
            var y = "hello world"
        """.trimIndent()

        val expected = """
            top
              file: test
                decl: GLOBAL:x:Int
                  int: 1 (Int)
                decl: GLOBAL:y:String
                  string: "hello world" (String)

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun arithmeticOps() {
        val prog = """
            val intSum = 10 + 20
            var intDiff = 50 - 15
            val intProd = 5 * 6
            var intDiv = 100 / 4
            val intMod = 17 % 3
            val charToInt = 'A' + 1  # Char promotes to Int
            val realSum = 10.5 + 20.2
            var realProd = 2.5 * 4.0
        """.trimIndent()

        val expected = """
            top
              file: test
                decl: GLOBAL:intSum:Int
                  ADD_I (Int)
                    int: 10 (Int)
                    int: 20 (Int)
                decl: GLOBAL:intDiff:Int
                  SUB_I (Int)
                    int: 50 (Int)
                    int: 15 (Int)
                decl: GLOBAL:intProd:Int
                  MUL_I (Int)
                    int: 5 (Int)
                    int: 6 (Int)
                decl: GLOBAL:intDiv:Int
                  DIV_I (Int)
                    int: 100 (Int)
                    int: 4 (Int)
                decl: GLOBAL:intMod:Int
                  MOD_I (Int)
                    int: 17 (Int)
                    int: 3 (Int)
                decl: GLOBAL:charToInt:Int
                  ADD_I (Int)
                    int: 65 (Char)
                    int: 1 (Int)
                decl: GLOBAL:realSum:Real
                  ADD_R (Real)
                    real: 10.5 (Real)
                    real: 20.2 (Real)
                decl: GLOBAL:realProd:Real
                  MUL_R (Real)
                    real: 2.5 (Real)
                    real: 4.0 (Real)

        """.trimIndent()
        runTest(prog, expected)
    }

}