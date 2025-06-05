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
                  ADD_I (Int)
                    int: 10 (Int)
                    int: 20 (Int)
                decl: GLOBAL:intDiff:Int
                  SUB_I (Int)
                    int: 50 (Int)
                    int: 15 (Int)
                decl: GLOBAL:intProd:Int
                  MUL_I (Int)
                    int: 5 (Int)
                    int: 6 (Int)
                decl: GLOBAL:intDiv:Int
                  DIV_I (Int)
                    int: 100 (Int)
                    int: 4 (Int)
                decl: GLOBAL:intMod:Int
                  MOD_I (Int)
                    int: 17 (Int)
                    int: 3 (Int)
                decl: GLOBAL:charToInt:Int
                  ADD_I (Int)
                    int: 65 (Char)
                    int: 1 (Int)
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
                    assign
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
                    assign
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
                    assign
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
                  assign
                    member: name (String)
                      var: this (Cat)
                    var: name (String)
                  assign
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
                  assign
                    member: name (String)
                      var: this (Cat)
                    var: name (String)
                  assign
                    member: age (Int)
                      var: this (Cat)
                    var: age (Int)
                function: main
                  decl: VAR:c:Cat?
                    int: 0 (Null)
                  assign
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



}