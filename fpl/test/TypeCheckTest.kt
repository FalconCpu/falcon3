import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class TypeCheckTest {
    fun runTest(prog: String, expected:String) {
        val lexer = Lexer("test.fpl", StringReader(prog))
        val output = compile(listOf(lexer), StopAt.TYPECHECK)
        assertEquals(expected, output)
    }

    @Test
    fun simpleDeclaration() {
        val prog = """
            val x = 1
            var y = "hello world"
        """.trimIndent()

        val expected = """
            top
              file: test
                decl: GLOBAL:x:Int
                  int: 1 (Int)
                decl: GLOBAL:y:String
                  string: "hello world" (String)

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun arithmeticOps() {
        val prog = """
            val intSum = 10 + 20
            var intDiff = 50 - 15
            val intProd = 5 * 6
            var intDiv = 100 / 4
            val intMod = 17 % 3
            val charToInt = 'A' + 1  # Char promotes to Int
            val realSum = 10.5 + 20.2
            var realProd = 2.5 * 4.0
        """.trimIndent()

        val expected = """
            top
              file: test
                decl: GLOBAL:intSum:Int
                  int: 30 (Int)
                decl: GLOBAL:intDiff:Int
                  int: 35 (Int)
                decl: GLOBAL:intProd:Int
                  int: 30 (Int)
                decl: GLOBAL:intDiv:Int
                  int: 25 (Int)
                decl: GLOBAL:intMod:Int
                  int: 2 (Int)
                decl: GLOBAL:charToInt:Int
                  int: 66 (Int)
                decl: GLOBAL:realSum:Real
                  ADD_R (Real)
                    real: 10.5 (Real)
                    real: 20.2 (Real)
                decl: GLOBAL:realProd:Real
                  MUL_R (Real)
                    real: 2.5 (Real)
                    real: 4.0 (Real)

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun functionCalls() {
        val prog = """
            fun add(x:Int, y:Int) -> Int
                return x + y
            
            fun main() -> Int
                return add(10, 20)
        """.trimIndent()

        val expected = """
            top
              file: test
                function: add
                  expr-stmt
                    return (Nothing)
                      ADD_I (Int)
                        var: x (Int)
                        var: y (Int)
                function: main
                  expr-stmt
                    return (Nothing)
                      call (Int)
                        function: add ((Int,Int)->Int)
                        int: 10 (Int)
                        int: 20 (Int)

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
            top
              file: test
                function: sum
                  decl: VAR:total:Int
                    int: 0 (Int)
                  for: i
                    range: LTE_I (Range<Int>)
                      int: 0 (Int)
                      int: 10 (Int)
                    assign EQ_I
                      var: total (Int)
                      ADD_I (Int)
                        var: total (Int)
                        index (Int)
                          var: a (Array<Int>)
                          var: i (Int)
                  expr-stmt
                    return (Nothing)
                      var: total (Int)

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
                
            fun main() -> Int
                val a = new Array<Int>(10)
                for i in 0..9
                    a[i] = i
                return sum(a)
        """.trimIndent()

        val expected = """
            top
              file: test
                function: sum
                  decl: VAR:total:Int
                    int: 0 (Int)
                  for: i
                    range: LTE_I (Range<Int>)
                      int: 0 (Int)
                      int: 10 (Int)
                    assign EQ_I
                      var: total (Int)
                      ADD_I (Int)
                        var: total (Int)
                        index (Int)
                          var: a (Array<Int>)
                          var: i (Int)
                  expr-stmt
                    return (Nothing)
                      var: total (Int)
                function: main
                  decl: VAR:a:Array<Int>
                    new-array (Array<Int>)
                      int: 10 (Int)
                  for: i
                    range: LTE_I (Range<Int>)
                      int: 0 (Int)
                      int: 9 (Int)
                    assign EQ_I
                      index (Int)
                        var: a (Array<Int>)
                        var: i (Int)
                      var: i (Int)
                  expr-stmt
                    return (Nothing)
                      call (Int)
                        function: sum ((Array<Int>)->Int)
                        var: a (Array<Int>)

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
            top
              file: test
                class: Cat
                function: printCat
                  print
                    member: name (String)
                      var: c (Cat)
                  print
                    member: age (Int)
                      var: c (Cat)

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
            top
              file: test
                class: Cat
                  assign EQ_I
                    member: name (String)
                      var: this (Cat)
                    var: name (String)
                  assign EQ_I
                    member: ageInMonths (Int)
                      var: this (Cat)
                    MUL_I (Int)
                      var: ageInYears (Int)
                      int: 12 (Int)
                  function: Cat/greet
                    print
                      member: name (String)
                        var: this (Cat)
                      string: " says hello
            " (String)
                function: main
                  decl: VAR:c:Cat
                    new-object (Cat)
                      string: "Tom" (String)
                      int: 3 (Int)
                  expr-stmt
                    call (Unit)
                      method: greet
                        var: c (Cat)

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun nullableTest() {
        val prog = """
            class Cat(val name:String, val age:Int)
            
            fun main()
                var c : Cat? = null
                c = new Cat("Tom", 3)
        """.trimIndent()

        val expected = """
            top
              file: test
                class: Cat
                  assign EQ_I
                    member: name (String)
                      var: this (Cat)
                    var: name (String)
                  assign EQ_I
                    member: age (Int)
                      var: this (Cat)
                    var: age (Int)
                function: main
                  decl: VAR:c:Cat?
                    int: 0 (Null)
                  assign EQ_I
                    var: c (Cat?)
                    new-object (Cat)
                      string: "Tom" (String)
                      int: 3 (Int)

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun nullableTestError() {
        val prog = """
            class Cat(val name:String, val age:Int)
            
            fun printCat(c:Cat?)
                print(c.name)       # an error as c may be null
        """.trimIndent()

        val expected = """
            test.fpl:4.11-4.11: Cannot access 'name' as expression may be null
        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun constantDeclarations() {
        val prog = """
            const TEN = 8+2
            const TWELVE = TEN + 2
            const NAME = "Fred"
            const M1 = -1
            
            fun main()
                val a = TEN + TWELVE
                val b = NAME
                val c = M1
        """.trimIndent()

        val expected = """
            top
              file: test
                null-stmt
                null-stmt
                null-stmt
                null-stmt
                function: main
                  decl: VAR:a:Int
                    int: 22 (Int)
                  decl: VAR:b:String
                    string: "Fred" (String)
                  decl: VAR:c:Int
                    int: -1 (Int)

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun castTest() {
        val prog = """
            class HwRegs
                var led : Int
                val sw : Int

            fun main()
                val hwregs = 0xE0000000 as HwRegs
                hwregs.led = 0x12345678
        """.trimIndent()

        val expected = """
            top
              file: test
                class: HwRegs
                function: main
                  decl: VAR:hwregs:HwRegs
                    cast (HwRegs)
                      int: -536870912 (Int)
                  assign EQ_I
                    member: led (Int)
                      var: hwregs (HwRegs)
                    int: 305419896 (Int)

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun plusEq() {
        val prog = """
            fun main()
                var x = 5
                x += 4
                x -= 2
        """.trimIndent()

        val expected = """
            top
              file: test
                function: main
                  decl: VAR:x:Int
                    int: 5 (Int)
                  assign ADD_I
                    var: x (Int)
                    int: 4 (Int)
                  assign SUB_I
                    var: x (Int)
                    int: 2 (Int)

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
                fred()
                fred(1,2,3,4,"5")
                
        """.trimIndent()

        val expected = """
            test.fpl:6.5-6.8: Got 0 arguments when expecting at least 1
            test.fpl:7.18-7.20: Got type 'String' when expecting 'Int'
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
            top
              file: test
                function: fred
                  when 
                    var: a (Int)
                    when-clause
                      int: 1 (Int)
                      expr-stmt
                        return (Nothing)
                          string: "one" (String)
                    when-clause
                      int: 2 (Int)
                      expr-stmt
                        return (Nothing)
                          string: "two" (String)
                    when-clause
                      expr-stmt
                        return (Nothing)
                          string: "other" (String)

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun whenTestDuplicateInt() {
        val prog = """
            fun fred(a:Int) -> String
                when a
                    1 -> return "one"
                    2 -> return "two"
                    2 -> return "two"
                    else -> return "other"
        """.trimIndent()

        val expected = """
            test.fpl:5.9-5.9: Duplicate value '2'
        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun whenTestDuplicateString() {
        val prog = """
            fun fred(a:String) -> String
                when a
                    "1" -> return "one"
                    "2" -> return "two"
                    "2" -> return "two"
                    else -> return "other"
        """.trimIndent()

        val expected = """
            test.fpl:5.9-5.11: Duplicate value '2'
        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun whenTestMisplacedElse() {
        val prog = """
            fun fred(a:String) -> String
                when a
                    "1" -> return "one"
                    else -> return "other"
                    "2" -> return "two"
        """.trimIndent()

        val expected = """
            test.fpl:4.9-4.12: else clause must be the last clause in when
        """.trimIndent()
        runTest(prog, expected)
    }

}