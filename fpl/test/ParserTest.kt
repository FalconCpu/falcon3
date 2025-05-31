import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class ParserTest {
    fun runTest(prog: String, expected:String) {
        val lexer = Lexer("test.fpl", StringReader(prog))
        val output = compile(listOf(lexer), StopAt.PARSE)
        assertEquals(expected, output)
    }

    @Test
    fun simpleDeclaration() {
        val prog = """
            val x = 1
            var y = "hello world"
        """.trimIndent()

        val expected = """
            File test
             Decl VAL x
              Intlit 1
             Decl VAR y
              Stringlit hello world

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun expressions1() {
        val prog = """
            val x = 1 + 2
            var y = x * 3 - 4   # tests precedence
            var z = (x + 2) * 3
            val a = (y=2) or (z>=3)
            
        """.trimIndent()

        val expected = """
            File test
             Decl VAL x
              Binop PLUS
               Intlit 1
               Intlit 2
             Decl VAR y
              Binop MINUS
               Binop STAR
                Id x
                Intlit 3
               Intlit 4
             Decl VAR z
              Binop STAR
               Binop PLUS
                Id x
                Intlit 2
               Intlit 3
             Decl VAL a
              Binop OR
               Binop EQ
                Id y
                Intlit 2
               Binop GTE
                Id z
                Intlit 3

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun malformedInteger() {
        val prog = """
            val x = 123a
        """.trimIndent()

        val expected = """
            test.fpl:1.9-1.12: Malformed integer literal '123a'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun realNumbers() {
        val prog = """
            val x = 123.4
        """.trimIndent()

        val expected = """
            File test
             Decl VAL x
              Reallit 123.4

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun malformedRealNumbers() {
        val prog = """
            val x = 123.c
        """.trimIndent()

        val expected = """
            test.fpl:1.9-1.13: Malformed real literal '123.c'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun charLiterals() {
        val prog = """
            val x = 'x'
        """.trimIndent()

        val expected = """
            File test
             Decl VAL x
              Charlit x

        """.trimIndent()

        runTest(prog, expected)
    }


    @Test
    fun malformedCharLiterals() {
        val prog = """
            val x = 'xs'
        """.trimIndent()

        val expected = """
            test.fpl:1.9-1.12: Malformed character literal 'xs'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun malformedExpression() {
        val prog = """
            val x = and 4
        """.trimIndent()

        val expected = """
            test.fpl:1.9-1.11: Got 'and' when expecting primary expression
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun indexExpression() {
        val prog = """
            val x = a[4]
        """.trimIndent()

        val expected = """
            File test
             Decl VAL x
              Index
               Id a
               Intlit 4

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun memberExpression() {
        val prog = """
            val x = fred.age
        """.trimIndent()

        val expected = """
            File test
             Decl VAL x
              Member age
               Id fred

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun functionCall() {
        val prog = """
            val x = fred(1,2,3)
        """.trimIndent()

        val expected = """
            File test
             Decl VAL x
              Call
               Id fred
               Intlit 1
               Intlit 2
               Intlit 3

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun unaryOperators() {
        val prog = """
            val x = -5
            val y = not x
        """.trimIndent()

        val expected = """
            File test
             Decl VAL x
              Minus
               Intlit 5
             Decl VAL y
              Not
               Id x

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun declarationWithType() {
        val prog = """
            val x : Int = -5
            val y : Array<String?>
        """.trimIndent()

        val expected = """
            File test
             Decl VAL x
              Type Int
              Minus
               Intlit 5
             Decl VAL y
              TypeArray
               TypeNullable
                Type String

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun malformedTypes() {
        val prog = """
            val x : Int = -5
            val y : and
        """.trimIndent()

        val expected = """
            test.fpl:2.9-2.11: Got 'and' when expecting type expression
        """.trimIndent()

        runTest(prog, expected)
    }


    @Test
    fun functionDefinition() {
        val prog = """
            fun fred(a:Int, b:Int) -> Int
                b = b + 1
                return a + b
            
        """.trimIndent()

        val expected = """
            File test
             Function fred
              Parameter a
               Type Int
              Parameter b
               Type Int
              Type Int
              Assign
               Id b
               Binop PLUS
                Id b
                Intlit 1
              ExprStmt
               Return
                Binop PLUS
                 Id a
                 Id b

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun functionBadEnd() {
        val prog = """
            fun fred(a:Int, b:Int) -> Int
                b = b + 1
                return a + b
            end if    # error should be end fun
        """.trimIndent()

        val expected = """
            test.fpl:4.1-4.3: Got 'end if' when expecting 'end fun'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun functionMissingBody() {
        val prog = """
            fun fred(a:Int, b:Int) -> Int
            
            val a = 1

        """.trimIndent()

        val expected = """
            test.fpl:3.1-3.3: Missing indented block
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun ifStatement() {
        val prog = """
            fun fred(a:Int) -> String
                if a > 0
                    return "positive"
                elsif a < 0
                    return "negative"
                else
                    return "zero"
        """.trimIndent()

        val expected = """
            File test
             Function fred
              Parameter a
               Type Int
              Type String
              If
               IfClause
                Binop GT
                 Id a
                 Intlit 0
                ExprStmt
                 Return
                  Stringlit positive
               IfClause
                Binop LT
                 Id a
                 Intlit 0
                ExprStmt
                 Return
                  Stringlit negative
               IfClause
                ExprStmt
                 Return
                  Stringlit zero

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun ifThenStatement() {
        // One-line if bodies can be put on the same line with THEN. This should not affect the AST.

        val prog1 = """
            fun fred(a:Int) -> String
                if a > 0
                    return "positive"
                elsif a < 0 
                    return "negative"
                else
                    return "zero"
        """.trimIndent()

        val prog2 = """
            fun fred(a:Int) -> String
                if a > 0 then return "positive"
                elsif a < 0 
                    return "negative"
                else
                    return "zero"
        """.trimIndent()

        val prog3 = """
            fun fred(a:Int) -> String
                if a > 0 then return "positive"
                elsif a < 0 then return "negative"
                else return "zero"
        """.trimIndent()

        val expected = """
            File test
             Function fred
              Parameter a
               Type Int
              Type String
              If
               IfClause
                Binop GT
                 Id a
                 Intlit 0
                ExprStmt
                 Return
                  Stringlit positive
               IfClause
                Binop LT
                 Id a
                 Intlit 0
                ExprStmt
                 Return
                  Stringlit negative
               IfClause
                ExprStmt
                 Return
                  Stringlit zero

        """.trimIndent()

        runTest(prog1, expected)
        runTest(prog2, expected)
        runTest(prog3, expected)
    }

    @Test
    fun ifAndOr() {
        val prog = """
            fun fred(a:Int, b:Int) -> String
                if a > 0 and b > 0
                    return "both positive"
                elsif a < 0 or b < 0
                    return "one negative"
                else
                    return "mixed"
        """.trimIndent()

        val expected = """
            File test
             Function fred
              Parameter a
               Type Int
              Parameter b
               Type Int
              Type String
              If
               IfClause
                Binop AND
                 Binop GT
                  Id a
                  Intlit 0
                 Binop GT
                  Id b
                  Intlit 0
                ExprStmt
                 Return
                  Stringlit both positive
               IfClause
                Binop OR
                 Binop LT
                  Id a
                  Intlit 0
                 Binop LT
                  Id b
                  Intlit 0
                ExprStmt
                 Return
                  Stringlit one negative
               IfClause
                ExprStmt
                 Return
                  Stringlit mixed

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun whileTest() {
        val prog = """
            fun fred(a:Int)
                var x = 0
                while x < a
                    x = x + 1
        """.trimIndent()

        val expected = """
            File test
             Function fred
              Parameter a
               Type Int
              Decl VAR x
               Intlit 0
              While
               Binop LT
                Id x
                Id a
               Assign
                Id x
                Binop PLUS
                 Id x
                 Intlit 1

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun repeatTest() {
        val prog = """
            fun fred(a:Int)
                var x = 0
                repeat
                    x = x + 1
                until x >= a
        """.trimIndent()

        val expected = """
            File test
             Function fred
              Parameter a
               Type Int
              Decl VAR x
               Intlit 0
              Repeat
               Binop GTE
                Id x
                Id a
               Assign
                Id x
                Binop PLUS
                 Id x
                 Intlit 1

        """.trimIndent()

        runTest(prog, expected)
    }



}