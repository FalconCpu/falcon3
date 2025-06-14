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
    fun helloWorld() {
        val prog = """
            fun main()
                print("Hello, world!\n")
                while true
                    val a = 0   # Dummy
        """.trimIndent()

        val expected = """
            Hello, world!

            """.trimIndent()
        runTest(prog, expected)
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
            EXCEPTION Index out of range: pc=ffff01c8: data=0000000a
            $ 1=00001008 $ 2=0000000a $ 3=0000000a $ 4=0000000a $ 5=00000000 $ 6=00000000 
            $ 7=00000000 $ 8=0000002d $ 9=00000000 $10=00001000 $11=00000000 $12=00000000 
            $13=00000000 $14=00000000 $15=00000000 $16=00000000 $17=00000000 $18=00000000 
            $19=00000000 $20=00000000 $21=00000000 $22=00000000 $23=00000000 $24=00000000 
            $25=00000000 $26=00000000 $27=00000000 $28=00000000 $29=00000100 $30=ffff0228 
            $31=03fffffc 
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
            Argument a: 1
            Argument b: 2 3 4 5 

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun whenTest() {
        val prog = """
            fun fred(a:Int) -> String
                when a
                    1 -> return "one"
                    2 -> return "two"
                    else -> return "other"
            
            fun main()
                for i in 0..4
                    print(i," ",fred(i),"\n")
        """.trimIndent()

        val expected = """
            0 other
            1 one
            2 two
            3 other
            4 other

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun whenStringsTest() {
        val prog = """
            fun fred(a:String)
                when a
                    "one" -> print(1,"\n")
                    "two" -> print(2,"\n")
                    "three" -> print(3,"\n")
                    else -> print("else\n")
            
            fun main()
                fred("zero")
                fred("one")
                fred("two")
                fred("three")
                fred("four")
        """.trimIndent()

        val expected = """
            else
            1
            2
            3
            else

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun initializerList() {
        val prog = """
            fun print_array(a:Array<String>)
                for s in a
                    print(s,"\n")
                    
            fun main()
                val array = new Array<String>["one","two","three"]
                print_array(array)
        """.trimIndent()

        val expected = """
            one
            two
            three

        """.trimIndent()
        runTest(prog, expected)
    }


    @Test
    fun initializerListImpliedType() {
        val prog = """
            fun print_array(a:Array<String>)
                for s in a
                    print(s,"\n")
                    
            fun main()
                val array = new Array["one","two","three"]
                print_array(array)
        """.trimIndent()

        val expected = """
            one
            two
            three

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun ifExpr() {
        val prog = """
            fun fred(a:Int) -> String
                return if a=1 then "one" else "other"
            
            fun main()
                for i in 0..4
                    print(i," ",fred(i),"\n")

        """.trimIndent()

        val expected = """
            0 other
            1 one
            2 other
            3 other
            4 other

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
            Today is: 0
            Tomorrow is: 1
            The light is: 0
            Starting the week!
            Weekend equality is false.
            Traffic light equality is false.
            --- Test Complete ---

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun overloadFuncCall() {
        val prog = """
            fun doSomething(a:Int)
                print("Int ",a,"\n")
                
            fun doSomething(a:String)
                print("String ",a,"\n")
                
            fun main()
                doSomething(1)
                doSomething("hello")
        """.trimIndent()

        val expected = """
            Int 1
            String hello

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun fixedArrayTest() {
        val prog = """
            fun printFixedArray(a:FixedArray<Int>(10))
                for i in a
                    print(i,"\n")
                    
            fun main() 
                val a = local FixedArray<Int>(10){it*3}
                printFixedArray(a)
        """.trimIndent()

        val expected = """
            0
            3
            6
            9
            12
            15
            18
            21
            24
            27


        """.trimIndent()
        runTest(prog, expected)
    }


}