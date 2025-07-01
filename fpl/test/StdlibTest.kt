import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.FileReader
import java.io.StringReader

class StdLibTest {
    private val stdlibFiles = listOf("stdlib/memory.fpl","stdlib/list.fpl","stdlib/printf.fpl", "stdlib/bitVector.fpl",
        "stdlib/graphics.fpl")
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

    @Test
    fun graphicsTest() {
        val prog = """
            fun main()
                initializeWindows()
                val wdw = newWindow(0,0,640,480,"Window 1")
                val gc = wdw.getGraphicsContext()
                gc.drawRect(100,100,400,150)
                gc.setColor(128)
                gc.drawLine(100,100,400,150)
                gc.drawText(100,10,"Hello World!")
        """.trimIndent()

        val expected = """
            write_hwregs(e0001000, 00001038)
            write_hwregs(e0001004, 00000000)
            write_hwregs(e0001008, 00000000)
            write_hwregs(e000100c, 00000280)
            write_hwregs(e0001010, 000001e0)
            write_hwregs(e0001014, 00000280)
            write_hwregs(e0001018, 00000001)
            Blit Cmd 1: 1038, 280
            Blit Cmd 2: 0, 1e00280
            Blit Cmd 3: 0, 0
            Blit Cmd 4: ff, 0
            Blit Cmd b: f0000000, 200c0c08
            Blit Cmd c: 100, 200c0c08
            Blit Cmd 4: ff, 0
            Blit Cmd 6: 0, 27f
            Blit Cmd 6: 27f, 1df027f
            Blit Cmd 6: 1df027f, 1df0000
            Blit Cmd 6: 1df0000, 0
            Blit Cmd 6: e0000, e027f
            Blit Cmd a: 20002, 57
            Blit Cmd a: 2000a, 69
            Blit Cmd a: 20012, 6e
            Blit Cmd a: 2001a, 64
            Blit Cmd a: 20022, 6f
            Blit Cmd a: 2002a, 77
            Blit Cmd a: 20032, 20
            Blit Cmd a: 2003a, 31
            Blit Cmd 1: 1038, 280
            Blit Cmd 2: 100002, 1de027e
            Blit Cmd 3: 2, 10
            Blit Cmd 4: ff, 0
            Blit Cmd b: f0000000, 200c0c08
            Blit Cmd c: 100, 200c0c08
            Blit Cmd 5: 640064, 960190
            Blit Cmd 4: 80, 0
            Blit Cmd 6: 640064, 960190
            Blit Cmd a: a0064, 48
            Blit Cmd a: a006c, 65
            Blit Cmd a: a0074, 6c
            Blit Cmd a: a007c, 6c
            Blit Cmd a: a0084, 6f
            Blit Cmd a: a008c, 20
            Blit Cmd a: a0094, 57
            Blit Cmd a: a009c, 6f
            Blit Cmd a: a00a4, 72
            Blit Cmd a: a00ac, 6c
            Blit Cmd a: a00b4, 64
            Blit Cmd a: a00bc, 21
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun twoWindowsTest() {
        val prog = """
            fun main()
                initializeWindows()
                val wdw1 = newWindow(0,0,320,400,"Window 1")
                val gc = wdw1.getGraphicsContext()
                gc.drawRect(100,100,400,150)
                gc.setColor(128)
                gc.drawLine(100,100,400,150)
                
                val wdw2 = newWindow(120,80,300,200,"Window 2")
                val gc2 = wdw2.getGraphicsContext()
                gc2.drawText(100,10,"Hello World!")
                
                val wdw3 = newWindow(120,80,300,200,"Window 3")
                val gc3 = wdw3.getGraphicsContext()
                gc3.setColor(130)
                gc3.drawLine(0,0,300,200)
                gc3.drawLine(300,0,0,200)

                var x = 100
                var y = 100
                var dx = 1
                var dy = 1
                while true
                    wdw2.move(x,y)
                    x += dx
                    y += dy
                    if (x >= 400) then dx = -1
                    if (x <= 0) then dx = 1
                    if (y >= 300) then dy = -1
                    if (y <= 0) then dy = 1
                    gc2.waitVSync()
                
        """.trimIndent()

        val expected = """
            write_hwregs(e0001000, 00001038)
            write_hwregs(e0001004, 00000000)
            write_hwregs(e0001008, 00000000)
            write_hwregs(e000100c, 00000280)
            write_hwregs(e0001010, 000001e0)
            write_hwregs(e0001014, 00000280)
            write_hwregs(e0001018, 00000001)
            Blit Cmd 1: 1038, 280
            Blit Cmd 2: 0, 1e00280
            Blit Cmd 3: 0, 0
            Blit Cmd 4: ff, 0
            Blit Cmd b: f0000000, 200c0c08
            Blit Cmd c: 100, 200c0c08
            Blit Cmd 4: ff, 0
            Blit Cmd 6: 0, 27f
            Blit Cmd 6: 27f, 1df027f
            Blit Cmd 6: 1df027f, 1df0000
            Blit Cmd 6: 1df0000, 0
            Blit Cmd 6: e0000, e027f
            Blit Cmd a: 20002, 57
            Blit Cmd a: 2000a, 69
            Blit Cmd a: 20012, 6e
            Blit Cmd a: 2001a, 64
            Blit Cmd a: 20022, 6f
            Blit Cmd a: 2002a, 77
            Blit Cmd a: 20032, 20
            Blit Cmd a: 2003a, 31
            Blit Cmd 1: 1038, 280
            Blit Cmd 2: 100002, 1de027e
            Blit Cmd 3: 2, 10
            Blit Cmd 4: ff, 0
            Blit Cmd b: f0000000, 200c0c08
            Blit Cmd c: 100, 200c0c08
            Blit Cmd 5: 640064, 960190
            Blit Cmd 4: 80, 0
            Blit Cmd 6: 640064, 960190
            Blit Cmd a: a0064, 48
            Blit Cmd a: a006c, 65
            Blit Cmd a: a0074, 6c
            Blit Cmd a: a007c, 6c
            Blit Cmd a: a0084, 6f
            Blit Cmd a: a008c, 20
            Blit Cmd a: a0094, 57
            Blit Cmd a: a009c, 6f
            Blit Cmd a: a00a4, 72
            Blit Cmd a: a00ac, 6c
            Blit Cmd a: a00b4, 64
            Blit Cmd a: a00bc, 21
        """.trimIndent()

        runTest(prog, expected)
    }

}
