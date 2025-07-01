import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.FileReader
import java.io.StringReader

class OsDevTest {
    private val stdlibFiles = listOf("stdlib/memory.fpl","stdlib/stringBuilder.fpl")
    private val stdLibAsm = listOf("stdlib/start_stdlib.f32")


    fun runTest(prog: String, expected: String) {
        val stdLibLexers = stdlibFiles.map { Lexer(it, FileReader(it)) }
        val lexer = Lexer("test.fpl", StringReader(prog))
        val output = compile(stdLibLexers + lexer, StopAt.EXECUTE, stdLibAsm)
        assertEquals(expected, output)
    }

    @Test
    fun memSysTest() {
        val prog = """
            class Cat(val name:String, val age:Int)
                fun free()
                    print("Freeing ", name, "\n")
            
            fun main()
                print("Initial freelist\n")
                dumpMemorySystem()
        
                print("\nAllocating some blocks\n")
                val x = new Array[1,2,3,4]
                print("x = ", x as Int, "\n")              
                val y = new Cat("Fred", 5)
                print("y = ", y as Int, "\n")
                
                dumpMemorySystem()
                
                print("\nFreeing some blocks\n")
                free x
                free y
                dumpMemorySystem()
                
        """.trimIndent()

        val expected = """
            Initial freelist
            MEMORY DUMP
            00001000 Free: 66580480 next=00000000

            Allocating some blocks
            x = 4104
            y = 4136
            MEMORY DUMP
            00001000 Array: Size=32 NumElements=4
            00001020 Object: Size=16 type=Cat
            00001030 Free: 66580432 next=00000000

            Freeing some blocks
            Freeing Fred
            MEMORY DUMP
            00001000 Free: 32 next=00001030
            00001020 Free: 16 next=00001000
            00001030 Free: 66580432 next=00000000

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun doubleFreeTest() {
        val prog = """
            class Cat(val name:String, val age:Int)
                fun free()
                    print("Freeing ", name, "\n")
            
            fun main()
                val a = new Cat("Fred", 5)
                val b = a
                free a
                free b
                print("Should not get here\n")
                
        """.trimIndent()

        val expected = """
            Freeing Fred
            Freeing Fred
            EXCEPTION System Call: pc=ffff0394: data=00000001
            $ 1=00000e02 $ 2=00000010 $ 3=00001000 $ 4=00000000 $ 5=03f7eff0 $ 6=00000000 
            $ 7=e0000000 $ 8=00001008 $ 9=00001008 $10=00000000 $11=00000000 $12=00000000 
            $13=00000000 $14=00000000 $15=00000000 $16=00000000 $17=00000000 $18=00000000 
            $19=00000000 $20=00000000 $21=00000000 $22=00000000 $23=00000000 $24=00000000 
            $25=00000000 $26=00000000 $27=00000000 $28=00000000 $29=00000100 $30=ffff0994 
            $31=03fffff4 
        """.trimIndent()

        runTest(prog, expected)
    }


    @Test
    fun stringBuilder() {
        val prog = """
            fun main()
                val sb = new StringBuilder()
                sb.append("Hello")
                sb.append(" ")
                sb.appendInt(-1234,0)
                sb.append(" ")
                sb.appendHex(0x89ABCDEF,0)
                sb.append(" world\n")
                val string = sb.toString() 
                print(string)
                free sb
                # note we haven't freed the string yet
                
                dumpMemorySystem()
        """.trimIndent()

        val expected = """
            Hello -1234 89ABCDEF world
            MEMORY DUMP
            00001000 Free: 16 next=00001010
            00001010 Free: 16 next=00001020
            00001020 Free: 32 next=00001040
            00001040 Free: 48 next=000010A0
            00001070 Array: Size=48 NumElements=27
            000010A0 Free: 66580320 next=00000000

        """.trimIndent()

        runTest(prog, expected)
    }



}