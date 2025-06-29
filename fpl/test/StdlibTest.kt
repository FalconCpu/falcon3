import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.FileReader
import java.io.StringReader

class StdLibTest {
    private val stdlibFiles = listOf("stdlib/memory.fpl","stdlib/list.fpl","stdlib/printf.fpl", "stdlib/bitVector.fpl")
    private val stdLibAsm = listOf("Stdlib/start_stdlib.f32")


    fun runTest(prog: String, expected: String) {
        val stdLibLexers = stdlibFiles.map { Lexer(it, FileReader(it)) }
        val lexer = Lexer("test.fpl", StringReader(prog))
        val output = compile(stdLibLexers + lexer, StopAt.EXECUTE, stdLibAsm)
        assertEquals(expected, output)
    }

    @Test
    fun simpleDeclaration() {
        val prog = """
            fun main()
                val x = 2
                print(40+x)
        """.trimIndent()

        val expected = """
            42
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun printString() {
        val prog = """
            fun main()
                print("Hello, World!")
        """.trimIndent()

        val expected = """
            Hello, World!
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun malloc() {
        val prog = """
            class Cat(val name:String, val ageInYears:Int)

            fun main()
                val c = new Cat("Fluffy", 3)
                print(c.name)
        """.trimIndent()

        val expected = """
            Fluffy
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun list_add() {
        val prog = """
            fun main()
                val a = new List<Int>()
                a.add(1)
                a.add(2)
                a.add(3)
                a[1] = 4
                
                for x in a
                    print(x," ")
        """.trimIndent()

        val expected = """
            1 4 3 
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun list_isEmpty() {
        val prog = """
            fun main()
                val a = new List<Int>()
                print(a.isEmpty(), "\n")
                a.add(1)
                a.add(2)
                a.add(3)
                print(a.isEmpty(), "\n")
                print(a.isNotEmpty(), "\n")
                print(a.size, "\n")
        """.trimIndent()

        val expected = """
            1
            0
            1
            3

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun list_clear() {
        val prog = """
            fun main()
                val a = new List<Int>()
                a.add(1)
                a.add(2)
                a.add(3)
                a.clear()
                print(a.isEmpty(), "\n")
        """.trimIndent()

        val expected = """
            1
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun list_removeAt() {
        val prog = """
            fun main()
                val a = new List<String>()
                a.add("apple")
                a.add("banana")
                a.add("cherry")
                a.add("date")
                a.add("elderberry")
                val x = a.removeAt(2)
                
                print("Removed: ", x, "\n")
                print("Remaining: \n")
                for x in a
                    print(x, "\n")
        """.trimIndent()

        val expected = """
            Removed: cherry
            Remaining: 
            apple
            banana
            date
            elderberry

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun list_addAt() {
        val prog = """
            fun main()
                val a = new List<String>()
                a.add("apple")
                a.add("banana")
                a.add("cherry")
                a.add("date")
                a.add("elderberry")
                a.addAt(2, "fig")
                
                for x in a
                    print(x, "\n")
        """.trimIndent()

        val expected = """
            apple
            banana
            fig
            cherry
            date
            elderberry

        """.trimIndent()

        runTest(prog, expected)
    }


    @Test
    fun list_indexOf() {
        val prog = """
            fun main()
                val a = new List<String>()
                a.add("apple")
                a.add("banana")
                a.add("cherry")
                a.add("date")
                a.add("elderberry")
                
                print("Cherry is at ", a.indexOf("cherry"), "\n")
                print("Fig is at ", a.indexOf("fig"), "\n")
        """.trimIndent()

        val expected = """
            Cherry is at 2
            Fig is at -1

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun list_contains() {
        val prog = """
            fun main()
                val a = new List<String>()
                a.add("apple")
                a.add("banana")
                a.add("cherry")
                a.add("date")
                a.add("elderberry")
                
                print("List contains Cherry : ", a.contains("cherry"), "\n")
                print("List contains Fig : ", a.contains("fig"), "\n")
        """.trimIndent()

        val expected = """
            List contains Cherry : 1
            List contains Fig : 0

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun printf_test() {
        val prog = """
            fun main()
                printf("%s!\n", "hello")
                printf("%10s!\n", "hello")
                printf("%-10s!\n", "hello")
                
                printf("%d!\n", 123)
                printf("%05d!\n", 123)
                printf("%5d!\n", 123)
                printf("%-5d!\n", 123)
                
                printf("%d!\n", -456)
                printf("%05d!\n", -456)
                printf("%5d!\n", -456)
                printf("%-5d!\n", -456)

                printf("%x!\n", 0x8ac)
                printf("%05x!\n", 0x8ac)
                printf("%5x!\n", 0x8ac)
                printf("%-5x!\n", 0x8ac)



        """.trimIndent()

        val expected = """
            hello!
                 hello!
            hello     !
            123!
            00123!
              123!
            123  !
            -456!
            -0456!
             -456!
            -456 !
            8AC!
            008AC!
              8AC!
            8AC  !

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun bitVector() {
        val prog = """
            fun main()
                val bv = new BitVector(64)

        """.trimIndent()

        val expected = """
        """.trimIndent()

        runTest(prog, expected)
    }



}
