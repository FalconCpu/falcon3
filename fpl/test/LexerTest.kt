import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class LexerTest {
    fun runTest(prog: String, expected:String) {
        val lexer = Lexer("test.txt", StringReader(prog))
        val sb = StringBuilder()
        while (true) {
            val token = lexer.nextToken()
            if (token.kind == TokenKind.EOF)
                break
            sb.append("${token.kind} $token\n")
        }
        assertEquals(expected, sb.toString())
    }

    fun runTestErrors(prog: String, expected:String) {
        Log.clear()
        val lexer = Lexer("test.txt", StringReader(prog))
        while (true) {
            val token = lexer.nextToken()
            if (token.kind == TokenKind.EOF)
                break
        }
        assertEquals(expected, Log.getErrors())
    }


    @Test
    fun test1() {
        val prog = """
            fun x 123 123.45 (
               # this is a comment
               "hello" ) <= 'x'
            val x = 1
               val y = 2
            else
        """.trimIndent()

        val expected = """
            FUN fun
            ID x
            INTLIT 123
            REALLIT 123.45
            OPENB (
            STRINGLIT hello
            CLOSEB )
            LTE <=
            CHARLIT x
            EOL <end of line>
            VAL val
            ID x
            EQ =
            INTLIT 1
            EOL <end of line>
            INDENT <indent>
            VAL val
            ID y
            EQ =
            INTLIT 2
            EOL <end of line>
            DEDENT <dedent>
            ELSE else
            EOL <end of line>
            
        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun escapedChars() {
        val prog = """
            val a = "\n"
            val d = "\""
        """.trimIndent()

        val expected = """
            VAL val
            ID a
            EQ =
            STRINGLIT 

            EOL <end of line>
            VAL val
            ID d
            EQ =
            STRINGLIT "
            EOL <end of line>

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun indentationError() {
        val prog = """
            val a = "a"
               val d = "b"
             val e = "c"
        """.trimIndent()

        val expected = """
            test.txt:3.2-3.2: Indentation error - got column 2, expected 1
        """.trimIndent()
        runTestErrors(prog, expected)
    }

    @Test
    fun unterminatedString() {
        val prog = """
            val a = "a
        """.trimIndent()

        val expected = """
            test.txt:1.9-1.10: Unterminated string literal
        """.trimIndent()
        runTestErrors(prog, expected)
    }

    @Test
    fun unterminatedCharLiteral() {
        val prog = """
            val a = 'a
        """.trimIndent()

        val expected = """
            test.txt:1.9-1.10: Unterminated character literal
        """.trimIndent()
        runTestErrors(prog, expected)
    }

    @Test
    fun invalidToken() {
        val prog = """
            val a = £
        """.trimIndent()

        val expected = """
            test.txt:1.9-1.9: Invalid token '£'
        """.trimIndent()
        runTestErrors(prog, expected)
    }


}