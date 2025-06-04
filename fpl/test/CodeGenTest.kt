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
            ldw T4, a[size]
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
                for i in 0 ..< a.size
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
            ldw T2, a[size]
            ld T3, T2
            jmp L3
            L1:
            ldw T4, a[size]
            idx4 T5, i, T4
            ADD_I T6, a, T5
            ldw T7, T6[0]
            ADD_I T8, total, T7
            ld total, T8
            ADD_I T9, i, 1
            ld i, T9
            L3:
            blt i, T3, L1
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
            jsr callocArray
            ld T2, R8
            ld a, T2
            ld T3, 0
            ld i, T3
            ld T4, 9
            ld T5, T4
            jmp L3
            L1:
            ldw T6, a[size]
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

    @Test
    fun forInArray() {
        val prog = """
            fun printArray(array:Array<Int>)
                for item in array
                    print(item,"\n")
                
            fun main()
                val a = new Array<Int>(10)
                for i in 0..9
                    a[i] = i
                printArray(a)
        """.trimIndent()

        val expected = """
            Function printArray
            start
            ld array, R1
            ldw T0, array[size]
            MUL_I T1, T0, 4
            ADD_I T2, array, T1
            ld V36, array
            jmp L3
            L1:
            ldw T3, V36[0]
            ld item, T3
            ld R1, item
            jsr printInt
            ld T4, OBJ0
            ld R1, T4
            jsr printString
            ADD_I T5, V36, 4
            ld V36, T5
            L3:
            blt V36, T2, L1
            jmp L2
            L2:
            L0:
            ret

            Function main
            start
            ld T0, 10
            ld T1, 4
            ld R1, T0
            ld R2, T1
            jsr callocArray
            ld T2, R8
            ld a, T2
            ld T3, 0
            ld i, T3
            ld T4, 9
            ld T5, T4
            jmp L3
            L1:
            ldw T6, a[size]
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
            jsr printArray
            L0:
            ret


        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun arrayInitializer() {
        val prog = """
            fun main()
                val a = new Array<Int>(10){it*2}
        """.trimIndent()

        val expected = """
            Function main
            start
            ld T0, 10
            ld T1, 4
            ld R1, T0
            ld R2, T1
            jsr mallocArray
            ld T2, R8
            ld V36, 0
            ld V37, T2
            jmp L2
            L1:
            ld it, V36
            ld T3, 2
            MUL_I T4, it, T3
            stw T4, V37[0]
            ADD_I T5, V36, 1
            ld V36, T5
            ADD_I T6, V37, 4
            ld V37, T6
            L2:
            blt V36, T0, L1
            ld a, T2
            L0:
            ret


        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun classReferenceTest() {
        val prog = """
            class Cat
                var name:String
                var age:Int
            
            fun printCat(c:Cat)
                print(c.name)
                print(c.age)
        """.trimIndent()

        val expected = """
            Function printCat
            start
            ld c, R1
            ldw T0, c[name]
            ld R1, T0
            jsr printString
            ldw T1, c[age]
            ld R1, T1
            jsr printInt
            L0:
            ret

            Function Cat
            start
            ld this, R1
            L0:
            ret


        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun classConstructor() {
        val prog = """
            class Cat(val name:String, ageInYears:Int)
                var ageInMonths = ageInYears * 12
            
            fun printCat(c:Cat)
                print(c.name)
                print(c.ageInMonths)
                
            fun main()
                val c = new Cat("Tom", 3)
                printCat(c)
        """.trimIndent()

        val expected = """
            Function printCat
            start
            ld c, R1
            ldw T0, c[name]
            ld R1, T0
            jsr printString
            ldw T1, c[ageInMonths]
            ld R1, T1
            jsr printInt
            L0:
            ret

            Function main
            start
            ld T0, Cat/class
            ld R1, T0
            jsr mallocObject
            ld T1, R8
            ld T2, OBJ0
            ld T3, 3
            ld R1, T1
            ld R2, T2
            ld R3, T3
            jsr Cat
            ld c, T1
            ld R1, c
            jsr printCat
            L0:
            ret

            Function Cat
            start
            ld this, R1
            ld name, R2
            ld ageInYears, R3
            stw name, this[name]
            ld T0, 12
            MUL_I T1, ageInYears, T0
            stw T1, this[ageInMonths]
            L0:
            ret


        """.trimIndent()
        runTest(prog, expected)
    }



}