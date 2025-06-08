import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class PathContextTest {
    fun runTest(prog: String, expected: String) {
        val lexer = Lexer("test.fpl", StringReader(prog))
        val output = compile(listOf(lexer), StopAt.TYPECHECK)
        assertEquals(expected, output)
    }

    @Test
    fun uninitialisedTest1() {
        val prog = """
            fun fred() -> Int
                var x:Int
                return x        # Should give an error as x is uninitialized
        """.trimIndent()

        val expected = """
            test.fpl:3.12-3.12: Symbol 'x' is uninitialized
        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun uninitialisedTest2() {
        val prog = """
            fun fred() -> Int
                val x:Int
                x = 1           # although x is a val so normally assigning to it would be an error. 
                                # but as we can prove it is uninitialized we should accept this
                return x        # Should not give an error as x is now initialized
        """.trimIndent()

        val expected = """
            top
              file: test
                function: fred()
                  decl: VAR:x:Int
                  assign EQ_I
                    var: x (Int)
                    int: 1 (Int)
                  expr-stmt
                    return (Nothing)
                      var: x (Int)

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun uninitialisedTest3() {
        val prog = """
            fun fred(a:Int) -> Int
                val x:Int
                if (a=1)
                    x = 1  
                x = 2 
                return x
        """.trimIndent()

        val expected = """
            test.fpl:5.5-5.5: 'x' may already be initialised
        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun uninitialisedTest4() {
        val prog = """
            fun fred(a:Int) -> Int
                val x:Int
                if (a=1)
                    x = 1
                    return 0
                x = 2 
                return x
        """.trimIndent()

        val expected = """
            top
              file: test
                function: fred(Int)
                  decl: VAR:x:Int
                  if
                    if-clause
                      EQ_I (Bool)
                        var: a (Int)
                        int: 1 (Int)
                      assign EQ_I
                        var: x (Int)
                        int: 1 (Int)
                      expr-stmt
                        return (Nothing)
                          int: 0 (Int)
                  assign EQ_I
                    var: x (Int)
                    int: 2 (Int)
                  expr-stmt
                    return (Nothing)
                      var: x (Int)

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun nullPtr1() {
        val prog = """
            class Cat(val name:String, val age:Int)

            fun fred()
                var c : Cat? = new Cat("Fred", 10)
                print(c.name)
                c = null
                print(c.name)
        """.trimIndent()

        val expected = """
            test.fpl:7.11-7.11: Cannot access field 'name' of expression of type Null
        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun nullPtr2() {
        val prog = """
            class Cat(val name:String, val age:Int)

            fun fred(c:Cat?)
                if (c!=null)
                    print(c.name)
                else
                    print("No cat")
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
                function: fred(Cat?)
                  if
                    if-clause
                      NEQ_I (Bool)
                        var: c (Cat?)
                        int: 0 (Null)
                      print
                        member: name (String)
                          var: c (Cat)
                    if-clause
                      print
                        string: "No cat" (String)

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun nullPtrAndOr() {
        val prog = """
            # Define a simple class for testing
            class Cat(val name: String, val age: Int)
                fun meow()
                    print(name," says meow!\n")

            # Main test function
            fun testComplexSmartCasting(myCat: Cat?, otherCat: Cat?, someBoolean: Bool)
                # --- Initial State ---
                # At this point, 'myCat', 'otherCat' are Cat? (MAYBE_NULL)

                print("--- Starting testComplexSmartCasting ---")

                # --- Test Case 1: AND (&&) for refining a single variable ---
                # Condition: myCat is not null AND its age is greater than 5
                if (myCat != null and myCat.age > 5) 
                    # Inside 'if' block:
                    # 'myCat' should be smart-casted to Cat (NON_NULL) because both parts of && must be true.
                    myCat.meow()      # EXPECTED: PASS (smart-casted to Cat)
                    print("Inside AND block: myCat is a non-null, older cat.")
                else
                    # Inside 'else' block:
                    # This path is taken if (myCat == null) OR (myCat != null && myCat.age <= 5)
                    # So, 'myCat' is MAYBE_NULL here (could be null, or a young non-null cat).
                    myCat.meow()    # EXPECTED: FAIL (Cannot access field as expression may be null)
                    print("Inside AND block else: myCat is either null or a young cat.")
                
                # After 'if/else' block:
                # 'myCat' reverts to MAYBE_NULL (merge of 'then' and 'else' paths).
                myCat.meow() # EXPECTED: FAIL (Cannot access field as expression may be null)
                print("--- After AND block ---")
        """.trimIndent()

        val expected = """
            test.fpl:24.9-24.13: Cannot access 'meow' as expression may be null
            test.fpl:29.5-29.9: Cannot access 'meow' as expression may be null
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun whileLoop1() {
        val prog = """
            class Box(val contents: Int)
            
            fun main() 
                var b : Box? = new Box(10)
                var count = 0
                while count<5
                    print(b.contents)     # OK as b can never be null
                    count = count + 1
                print("finished: ",b.contents)
        """.trimIndent()

        val expected = """
            top
              file: test
                class: Box
                  assign EQ_I
                    member: contents (Int)
                      var: this (Box)
                    var: contents (Int)
                function: main()
                  decl: VAR:b:Box?
                    new-object (Box)
                      int: 10 (Int)
                  decl: VAR:count:Int
                    int: 0 (Int)
                  while
                    LT_I (Bool)
                      var: count (Int)
                      int: 5 (Int)
                    print
                      member: contents (Int)
                        var: b (Box)
                    assign EQ_I
                      var: count (Int)
                      ADD_I (Int)
                        var: count (Int)
                        int: 1 (Int)
                  print
                    string: "finished: " (String)
                    member: contents (Int)
                      var: b (Box)

        """.trimIndent()

        runTest(prog, expected)
    }

//    @Test
//    fun whileLoop() {
//        val prog = """
//            class Box(val contents: Int)
//
//            fun main()
//                var b : Box? = new Box(10)
//                var count = 0
//                while count<5
//                    print(b.contents)     # error as b could be null - on second iteration
//                    b = null
//                    count = count + 1
//                print("finished: ",b.contents)
//        """.trimIndent()
//
//        val expected = """
//            test.fpl:7.15-7.15: Cannot access 'contents' as expression may be null
//            test.fpl:10.24-10.24: Cannot access 'contents' as expression may be null
//        """.trimIndent()
//
//        runTest(prog, expected)
//    }


}