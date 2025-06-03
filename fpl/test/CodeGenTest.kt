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

    @Test
    fun arraysCalls() {
        val prog = """
            fun sum(a:Array<Int>) -> Int
                var total = 0
                for i in 0..10
                    total = total + a[i]
                return total
        """.trimIndent()

        val expected = """
            Function sum
            start
            ld a, R1
            ld T0, 0
            ld total, T0
            ld T1, 0
            ld i, T1
            ld T2, 10
            ld T3, T2
            jmp L3
            L1:
            ldw T4, a[-4]
            idx4 T5, i, T4
            ADD_I T6, a, T5
            ldw T7, T6[0]
            ADD_I T8, total, T7
            ld total, T8
            ADD_I T9, i, 1
            ld i, T9
            L3:
            ble i, T3, L1
            jmp L2
            L2:
            ld R8, total
            jmp L0
            L0:
            ret


        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun arraysAllocationCalls() {
        val prog = """
            fun sum(a:Array<Int>) -> Int
                var total = 0
                for i in 0..10
                    total = total + a[i]
                return total
                
            fun main()
                val a = new Array<Int>(10)
                for i in 0..9
                    a[i] = i
                print(sum(a))
        """.trimIndent()

        val expected = """
            Function sum
            start
            ld a, R1
            ld T0, 0
            ld total, T0
            ld T1, 0
            ld i, T1
            ld T2, 10
            ld T3, T2
            jmp L3
            L1:
            ldw T4, a[-4]
            idx4 T5, i, T4
            ADD_I T6, a, T5
            ldw T7, T6[0]
            ADD_I T8, total, T7
            ld total, T8
            ADD_I T9, i, 1
            ld i, T9
            L3:
            ble i, T3, L1
            jmp L2
            L2:
            ld R8, total
            jmp L0
            L0:
            ret

            Function main
            start
            ld T0, 10
            ld T1, 4
            ld R1, T0
            ld R2, T1
            jsr mallocArray
            ld T2, R8
            ld a, T2
            ld T3, 0
            ld i, T3
            ld T4, 9
            ld T5, T4
            jmp L3
            L1:
            ldw T6, a[-4]
            idx4 T7, i, T6
            ADD_I T8, a, T7
            stw i, T8[0]
            ADD_I T9, i, 1
            ld i, T9
            L3:
            ble i, T5, L1
            jmp L2
            L2:
            ld R1, a
            jsr sum
            ld T10, R8
            ld R1, T10
            jsr printInt
            L0:
            ret


        """.trimIndent()
        runTest(prog, expected)
    }


}