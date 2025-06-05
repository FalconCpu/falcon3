import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.FileReader
import java.io.StringReader

class ExecuteTest {
    private val stdlibFiles = listOf<String>()
    private val stdLibAsm = listOf("Stdlib/start.f32")


    fun runTest(prog: String, expected: String) {
        val stdLibLexers = stdlibFiles.map { Lexer(it, FileReader(it)) }
        val lexer = Lexer("test.fpl", StringReader(prog))
        val output = compile(stdLibLexers + lexer, StopAt.EXECUTE, stdLibAsm)
        assertEquals(expected, output)
    }

    @Test
    fun whileLoop() {
        val prog = """
            fun main()
                var i = 0
                while i < 10
                    print(i,"\n")
                    i = i + 1
        """.trimIndent()

        val expected = """
            0
            1
            2
            3
            4
            5
            6
            7
            8
            9

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun ifTest() {
        val prog = """
            fun main()
                var i = 0
                while i < 5
                    if i=0
                        print("zero\n")
                    elsif i=1
                        print("one\n")
                    else
                        print("other\n")
                    i = i + 1
        """.trimIndent()

        val expected = """
            zero
            one
            other
            other
            other

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun repeatLoop() {
        val prog = """
            fun main()
                var i = 0
                repeat
                    print(i,"\n")
                    i = i + 1
                until i=10
        """.trimIndent()

        val expected = """
            0
            1
            2
            3
            4
            5
            6
            7
            8
            9

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun forLoop() {
        val prog = """
            fun main()
                for i in 0..10
                    print(i,"\n")
        """.trimIndent()

        val expected = """
            0
            1
            2
            3
            4
            5
            6
            7
            8
            9
            10

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun forLoopExclusive() {
        val prog = """
            fun main()
                for i in 0..<10
                    print(i,"\n")
        """.trimIndent()

        val expected = """
            0
            1
            2
            3
            4
            5
            6
            7
            8
            9

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun forLoopDescending() {
        val prog = """
            fun main()
                for i in 10..>0
                    print(i,"\n")
        """.trimIndent()

        val expected = """
            10
            9
            8
            7
            6
            5
            4
            3
            2
            1

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
            45
        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun arraysOutOfBounds() {
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
            EXCEPTION Index out of range: pc=ffff01c4: data=0000000a

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun stringIndex() {
        val prog = """
            fun printSpaced(s:String)
                for i in 0..< s.length
                    print(s[i]," ")
                
            fun main()
                printSpaced("Hello world")
        """.trimIndent()

        val expected = """
            H e l l o   w o r l d 
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
            0
            1
            2
            3
            4
            5
            6
            7
            8
            9

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun arrayInitializer() {
        val prog = """
            fun main()
                val a = new Array<Int>(10){it*2}
                for x in a
                    print(x," ")
        """.trimIndent()

        val expected = """
            0 2 4 6 8 10 12 14 16 18 
        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun localArrayInitializer() {
        val prog = """
            fun main()
                val a = local Array<Int>(10){it*2}
                for x in a
                    print(x," ")
        """.trimIndent()

        val expected = """
            0 2 4 6 8 10 12 14 16 18 
        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun classConstructor() {
        val prog = """
            class Cat(val name:String, ageInYears:Int)
                var ageInMonths = ageInYears * 12
            
            fun printCat(c:Cat)
                print(c.name, " ", c.ageInMonths, "\n")
                
            fun main()
                val c = new Cat("Tom", 3)
                printCat(c)
        """.trimIndent()

        val expected = """
            Tom 36

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
            Tom says hello
            
        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun methodsTest2() {
        val prog = """
            class MyClass 
                var counter: Int = 0
            
                fun increment()
                    counter = counter + 1 # Implicit `this.counter`             
            
                fun incrementTwice()
                    increment()           # <-- THIS IS THE CASE: Implicit `this.increment()`
                    increment()
                    
            fun main()
                val c = new MyClass()
                c.incrementTwice()
                print(c.counter)
        """.trimIndent()

        val expected = """
            2
        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun plusEqTest2() {
        val prog = """
            fun main()
                var x = 0
                while x<10
                    print(x,"\n")
                    x += 1
        """.trimIndent()

        val expected = """
            0
            1
            2
            3
            4
            5
            6
            7
            8
            9

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun stringCompareTest() {
        val prog = """
            fun compare(a:String, b:String) 
                print("Comparing a=\"",a,"\", b=\"", b,"\"    ")
                if a<b then print("a<b ")
                if a<=b then print("a<=b ")
                if a=b then print("a=b ")
                if a!=b then print("a!=b ")
                if a>=b then print("a>=b ")
                if a>b then print("a>b")
                print("\n")
            
            
            fun main()
                val s0 = ""
                val s1 = "a"
                val s2 = "b"
                val s3 = "apple"     
                val s4 = "apricot"
                val s5 = "applepie"   # Identical to s3 up to s3's length
                val s6 = "banana"
                val s7 = "apple"      # Identical to s3 for direct equality test
                
                compare(s0,s1)
                compare(s1,s2)
                compare(s2,s3)
                compare(s3,s4)
                compare(s3,s5)
                compare(s3,s6)
                compare(s3,s7)
                compare(s6,s7)
        """.trimIndent()

        val expected = """
            Comparing a="", b="a"    a<b a<=b a!=b 
            Comparing a="a", b="b"    a<b a<=b a!=b 
            Comparing a="b", b="apple"    a!=b a>=b a>b
            Comparing a="apple", b="apricot"    a<b a<=b a!=b 
            Comparing a="apple", b="applepie"    a<b a<=b a!=b 
            Comparing a="apple", b="banana"    a<b a<=b a!=b 
            Comparing a="apple", b="apple"    a<=b a=b a>=b 
            Comparing a="banana", b="apple"    a!=b a>=b a>b

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun breakTest() {
        val prog = """
            fun main()
                for x in 0..9
                    print(x," \n")
                    if x = 5 then break
                print("done")
                   
        """.trimIndent()

        val expected = """
            0 
            1 
            2 
            3 
            4 
            5 
            done
        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun continueTest() {
        val prog = """
            fun main()
                for x in 0..9
                    if x % 2 != 0 then continue # If x is odd, skip the print statement and go to next iteration
                    print(x,"\n")
                print("done")
                   
        """.trimIndent()

        val expected = """
            0
            2
            4
            6
            8
            done
        """.trimIndent()
        runTest(prog, expected)
    }


    @Test
    fun varargTest() {
        val prog = """
            fun fred(a:Int, b:Int...)
                print("Argument a: ",a,"\n")
                print("Argument b: ")
                for x in b
                    print(x," ")
                print("\n")
                
            fun main()
                fred(1,2,3,4,5)
        """.trimIndent()

        val expected = """
        """.trimIndent()
        runTest(prog, expected)
    }



}