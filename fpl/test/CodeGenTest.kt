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
            Function main()
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
            Function sum(Array<Int>)
            start
            ld a, R1
            ld T0, 0
            ld total, T0
            ld T1, 0
            ld i, T1
            ld T2, 10
            ld T3, T2
            jmp L4
            L1:
            ldw T4, a[size]
            idx4 T5, i, T4
            ADD_I T6, a, T5
            ldw T7, T6[0]
            ADD_I T8, total, T7
            ld total, T8
            L3:
            ADD_I T9, i, 1
            ld i, T9
            L4:
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
            Function sum(Array<Int>)
            start
            ld a, R1
            ld T0, 0
            ld total, T0
            ld T1, 0
            ld i, T1
            ldw T2, a[size]
            ld T3, T2
            jmp L4
            L1:
            ldw T4, a[size]
            idx4 T5, i, T4
            ADD_I T6, a, T5
            ldw T7, T6[0]
            ADD_I T8, total, T7
            ld total, T8
            L3:
            ADD_I T9, i, 1
            ld i, T9
            L4:
            blt i, T3, L1
            jmp L2
            L2:
            ld R8, total
            jmp L0
            L0:
            ret

            Function main()
            start
            ld T0, 10
            ld T1, 4
            ld T2, 1
            ld R1, T0
            ld R2, T1
            ld R3, T2
            jsr mallocArray(Int,Int,Bool)
            ld T3, R8
            ld a, T3
            ld T4, 0
            ld i, T4
            ld T5, 9
            ld T6, T5
            jmp L4
            L1:
            ldw T7, a[size]
            idx4 T8, i, T7
            ADD_I T9, a, T8
            stw i, T9[0]
            L3:
            ADD_I T10, i, 1
            ld i, T10
            L4:
            ble i, T6, L1
            jmp L2
            L2:
            ld R1, a
            jsr sum(Array<Int>)
            ld T11, R8
            ld R1, T11
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
            Function printArray(Array<Int>)
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
            L4:
            ADD_I T5, V36, 4
            ld V36, T5
            L3:
            blt V36, T2, L1
            jmp L2
            L2:
            L0:
            ret

            Function main()
            start
            ld T0, 10
            ld T1, 4
            ld T2, 1
            ld R1, T0
            ld R2, T1
            ld R3, T2
            jsr mallocArray(Int,Int,Bool)
            ld T3, R8
            ld a, T3
            ld T4, 0
            ld i, T4
            ld T5, 9
            ld T6, T5
            jmp L4
            L1:
            ldw T7, a[size]
            idx4 T8, i, T7
            ADD_I T9, a, T8
            stw i, T9[0]
            L3:
            ADD_I T10, i, 1
            ld i, T10
            L4:
            ble i, T6, L1
            jmp L2
            L2:
            ld R1, a
            jsr printArray(Array<Int>)
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
            Function main()
            start
            ld T0, 10
            ld T1, 4
            ld T2, 0
            ld R1, T0
            ld R2, T1
            ld R3, T2
            jsr mallocArray(Int,Int,Bool)
            ld T3, R8
            ld V37, 0
            ld V38, T3
            jmp L2
            L1:
            ld it, V37
            ld T4, 2
            MUL_I T5, it, T4
            stw T5, V38[0]
            ADD_I T6, V37, 1
            ld V37, T6
            ADD_I T7, V38, 4
            ld V38, T7
            L2:
            blt V37, T0, L1
            ld a, T3
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
            Function printCat(Cat)
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
            Function printCat(Cat)
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

            Function main()
            start
            ld T0, Cat/class
            ld R1, T0
            jsr mallocObject(ClassDescriptor)
            ld T1, R8
            ld T2, OBJ0
            ld T3, 3
            ld R1, T1
            ld R2, T2
            ld R3, T3
            jsr Cat
            ld c, T1
            ld R1, c
            jsr printCat(Cat)
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

    @Test
    fun methodsTest() {
        val prog = """
            class Cat(val name:String, ageInYears:Int)
                var ageInMonths = ageInYears * 12
                fun greet()
                    print(name," says hello\n")
            
            fun main()
                val c = new Cat("Tom", 3)
                c.greet()
        """.trimIndent()

        val expected = """
            Function Cat/greet()
            start
            ld this, R1
            ldw T0, this[name]
            ld R1, T0
            jsr printString
            ld T1, OBJ0
            ld R1, T1
            jsr printString
            L0:
            ret

            Function main()
            start
            ld T0, Cat/class
            ld R1, T0
            jsr mallocObject(ClassDescriptor)
            ld T1, R8
            ld T2, OBJ1
            ld T3, 3
            ld R1, T1
            ld R2, T2
            ld R3, T3
            jsr Cat
            ld c, T1
            ld R1, c
            jsr Cat/greet()
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

    @Test
    fun varargTest() {
        val prog = """
            fun fred(a:Int, b:Int...)
                val c = a+b[0]
                
            fun main()
                fred(1,2,3,4,5)
        """.trimIndent()

        val expected = """
            Function fred(Int,Int...)
            start
            ld a, R1
            ld b, R2
            ld T0, 0
            ldw T1, b[size]
            idx4 T2, T0, T1
            ADD_I T3, b, T2
            ldw T4, T3[0]
            ADD_I T5, a, T4
            ld c, T5
            L0:
            ret

            Function main()
            start
            ld T0, 4
            stw T0, SP[0]
            ld T1, 2
            stw T1, SP[4]
            ld T2, 3
            stw T2, SP[8]
            ld T3, 4
            stw T3, SP[12]
            ld T4, 5
            stw T4, SP[16]
            ld T5, 1
            ADD_I T6, SP, 4
            ld R1, T5
            ld R2, T6
            jsr fred(Int,Int...)
            L0:
            ret


        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun whenTest() {
        val prog = """
            fun fred(a:Int) -> String
                when a
                    1 -> 
                        return "one"
                    2 -> 
                        return "two"
                    else ->
                        return "other"
        """.trimIndent()

        val expected = """
            Function fred(Int)
            start
            ld a, R1
            ld T0, 1
            beq a, T0, L2
            ld T1, 2
            beq a, T1, L3
            jmp L4
            jmp L1
            L2:
            ld T2, OBJ0
            ld R8, T2
            jmp L0
            jmp L1
            L3:
            ld T3, OBJ1
            ld R8, T3
            jmp L0
            jmp L1
            L4:
            ld T4, OBJ2
            ld R8, T4
            jmp L0
            jmp L1
            L1:
            L0:
            ret


        """.trimIndent()
        runTest(prog, expected)
    }


    @Test
    fun enumTest() {
        val prog = """
            enum DayOfWeek [ MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY ]
            enum TrafficLight [ RED, AMBER, GREEN ]

            fun main()
                # 1. Access an enum member and assign it to a variable
                val today = DayOfWeek.MONDAY
                val tomorrow = DayOfWeek.TUESDAY
            
                # 2. Print enum members directly (assuming they resolve to printable values, likely integers)
                print("Today is: ", today, "\n")
                print("Tomorrow is: ", tomorrow, "\n")
                print("The light is: ", TrafficLight.RED, "\n")
            
                # 3. Use enum members in a simple comparison
                if today = DayOfWeek.MONDAY
                    print("Starting the week!\n")
                else
                    print("Not Monday.\n")
            
                if DayOfWeek.SATURDAY = DayOfWeek.SUNDAY
                    print("Weekend equality is true.\n")
                else
                    print("Weekend equality is false.\n")
            
                # 4. Test a different enum's member
                if TrafficLight.GREEN = TrafficLight.RED
                    print("Traffic light equality is true.\n")
                else
                    print("Traffic light equality is false.\n")
            
                print("--- Test Complete ---\n")
                 
        """.trimIndent()

        val expected = """
            Function main()
            start
            ld T0, 0
            ld today, T0
            ld T1, 1
            ld tomorrow, T1
            ld T2, OBJ0
            ld R1, T2
            jsr printString
            ld R1, today
            jsr printInt
            ld T3, OBJ1
            ld R1, T3
            jsr printString
            ld T4, OBJ2
            ld R1, T4
            jsr printString
            ld R1, tomorrow
            jsr printInt
            ld T5, OBJ1
            ld R1, T5
            jsr printString
            ld T6, OBJ3
            ld R1, T6
            jsr printString
            ld T7, 0
            ld R1, T7
            jsr printInt
            ld T8, OBJ1
            ld R1, T8
            jsr printString
            ld T9, 0
            beq today, T9, L2
            jmp L3
            L3:
            jmp L4
            L2:
            ld T10, OBJ4
            ld R1, T10
            jsr printString
            jmp L1
            L4:
            ld T11, OBJ5
            ld R1, T11
            jsr printString
            jmp L1
            L1:
            ld T12, 5
            ld T13, 6
            beq T12, T13, L6
            jmp L7
            L7:
            jmp L8
            L6:
            ld T14, OBJ6
            ld R1, T14
            jsr printString
            jmp L5
            L8:
            ld T15, OBJ7
            ld R1, T15
            jsr printString
            jmp L5
            L5:
            ld T16, 2
            ld T17, 0
            beq T16, T17, L10
            jmp L11
            L11:
            jmp L12
            L10:
            ld T18, OBJ8
            ld R1, T18
            jsr printString
            jmp L9
            L12:
            ld T19, OBJ9
            ld R1, T19
            jsr printString
            jmp L9
            L9:
            ld T20, OBJ10
            ld R1, T20
            jsr printString
            L0:
            ret


        """.trimIndent()
        runTest(prog, expected)
    }


    @Test
    fun fixedArrayTest() {
        val prog = """
            fun main()
                val array = new FixedArray<Int>(10)
                for i in 0..<array.size
                    array[i] = i
        """.trimIndent()

        val expected = """
            Function main()
            start
            ld T0, 4
            ld T1, 10
            ld T2, 1
            ld R1, T1
            ld R2, T0
            ld R3, T2
            jsr mallocArray(Int,Int,Bool)
            ld T3, R8
            ld array, T3
            ld T4, 0
            ld i, T4
            ld T5, 10
            ld T6, T5
            jmp L4
            L1:
            ld T7, 10
            idx4 T8, i, T7
            ADD_I T9, array, T8
            stw i, T9[0]
            L3:
            ADD_I T10, i, 1
            ld i, T10
            L4:
            blt i, T6, L1
            jmp L2
            L2:
            L0:
            ret

            
        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun embeddedFieldsTest() {
        val prog = """
            class TCB 
                var   pc : Int
                local regs : FixedArray<Int>(32)
                local dmpu : FixedArray<Int>(8)
        
            fun main()
                val task = new TCB()
                task.pc = 10
                task.regs[4] = 0x1234
                task.dmpu[0] = 0x5678
                
                print("pc = ",task.pc,"\n")
                print("regs[4] = ",task.regs[4],"\n")
                print("dmpu[0] = ",task.dmpu[0],"\n")
        """.trimIndent()

        val expected = """
            Function main()
            start
            ld T0, TCB/class
            ld R1, T0
            jsr mallocObject(ClassDescriptor)
            ld T1, R8
            ld R1, T1
            jsr TCB
            ld task, T1
            ld T2, 10
            stw T2, task[pc]
            ld T3, 4660
            ADD_I T4, task, 4
            ld T5, 4
            ld T6, 32
            idx4 T7, T5, T6
            ADD_I T8, T4, T7
            stw T3, T8[0]
            ld T9, 22136
            ADD_I T10, task, 132
            ld T11, 0
            ld T12, 8
            idx4 T13, T11, T12
            ADD_I T14, T10, T13
            stw T9, T14[0]
            ld T15, OBJ0
            ld R1, T15
            jsr printString
            ldw T16, task[pc]
            ld R1, T16
            jsr printInt
            ld T17, OBJ1
            ld R1, T17
            jsr printString
            ld T18, OBJ2
            ld R1, T18
            jsr printString
            ADD_I T19, task, 4
            ld T20, 4
            ld T21, 32
            idx4 T22, T20, T21
            ADD_I T23, T19, T22
            ldw T24, T23[0]
            ld R1, T24
            jsr printInt
            ld T25, OBJ1
            ld R1, T25
            jsr printString
            ld T26, OBJ3
            ld R1, T26
            jsr printString
            ADD_I T27, task, 132
            ld T28, 0
            ld T29, 8
            idx4 T30, T28, T29
            ADD_I T31, T27, T30
            ldw T32, T31[0]
            ld R1, T32
            jsr printInt
            ld T33, OBJ1
            ld R1, T33
            jsr printString
            L0:
            ret

            Function TCB
            start
            ld this, R1
            L0:
            ret


        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun genericClassTest() {
        val prog = """
            class List<T>
                var size : Int
                var body : Array<T>
                
                fun get(i : Int) -> T
                    return body[i]
              
                
            fun main()
                val list = new List<Int>()
                val x = list.get(0)
                val y = x + 1
        """.trimIndent()

        val expected = """
            Function List/get(Int)
            start
            ld this, R1
            ld i, R2
            ldw T0, this[body]
            ldw T1, T0[size]
            idx4 T2, i, T1
            ADD_I T3, T0, T2
            ldw T4, T3[0]
            ld R8, T4
            jmp L0
            L0:
            ret

            Function main()
            start
            ld T0, List/class
            ld R1, T0
            jsr mallocObject(ClassDescriptor)
            ld T1, R8
            ld R1, T1
            jsr List
            ld list, T1
            ld T2, 0
            ld R1, list
            ld R2, T2
            jsr List/get(Int)
            ld T3, R8
            ld x, T3
            ld T4, 1
            ADD_I T5, x, T4
            ld y, T5
            L0:
            ret

            Function List
            start
            ld this, R1
            L0:
            ret


            """.trimIndent()

        runTest(prog, expected)
    }


}