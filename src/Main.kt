import java.io.InputStream
import java.io.OutputStream

// Using algebraic data types in kotlin for the AST
sealed class Statement {
    data class Assign(val lValue: LValue, val rValue: RValue) : Statement()
    data class Print(val rValue: RValue) : Statement()
    object OpenScope : Statement()
    object CloseScope : Statement()
}

typealias LValue = String

// Modularly define the syntax and semantics by defining parse and eval functions along with the AST node
sealed class RValue {
    abstract fun eval(symbolTable: SymbolTable): Int?
    data class Name(val name: String) : RValue() {
        override fun eval(symbolTable: SymbolTable): Int? = symbolTable.get(name)
    }

    data class Value(val value: Int) : RValue() {
        override fun eval(symbolTable: SymbolTable): Int? = value
    }
    companion object {
        fun parse(token: String): RValue = token.toIntOrNull()?.let { RValue.Value(it) } ?: RValue.Name(token)
    }
}

// for dealing with Scope constructs we use a stack of maps
typealias SymbolTable = ArrayDeque<MutableMap<LValue, Int>>

fun SymbolTable.get(name: LValue): Int? {
    for (scopeTable in this.asReversed()) {
        scopeTable[name]?.let { return it }
    }
    return null
}

fun SymbolTable.put(name: LValue, value: Int) {
    this.last()[name] = value
}

class Interpreter(val inputStream: InputStream, val outputStream: OutputStream) {
    val symbolTable: SymbolTable = ArrayDeque(listOf(mutableMapOf<LValue, Int>()))

    /**
     * Parses a single line of input into a `Statement`.
     *
     * @param line The input line to be parsed. Typically, contains a command or expression that can
     *             represent either scope control, a print statement, or an assignment.
     * @return A `Statement` object representing the parsed line, or `null` if the input line
     *         does not match a valid syntax or is empty.
     */
    fun parseLine(line: String): Statement? {
        val tokens = line.split("\\s+".toRegex()) // Split by one or more whitespace characters
        return if (tokens.size == 1 && tokens[0] == "}") {
            Statement.CloseScope
        } else if (tokens.size == 2 && tokens[0] == "scope" && tokens[1] == "{") {
            Statement.OpenScope
        } else if (tokens.size == 2 && tokens[0] == "print") { // Print
            Statement.Print(RValue.parse(tokens[1]))
        } else if (tokens.size == 3 && tokens[1] == "=") { // Assign
            Statement.Assign(tokens[0], RValue.parse(tokens[2]))
        } else {
            null
        }
    }

    /**
     * Evaluates the provided `Statement` and performs the associated operations.
     *
     * @param statement The `Statement` to be evaluated. It can be an assignment, print statement,
     *                  scope control statement, or null.
     * @return A `String` representing the result of the evaluation (e.g., output of a print statement
     *         or an error message if the statement is invalid), or `null` if no output is generated.
     */
    fun eval(statement: Statement?): String? {
        when (statement) {
            is Statement.Assign -> statement.rValue.eval(symbolTable)?.let { symbolTable.put(statement.lValue, it) }
            Statement.OpenScope -> symbolTable.addLast(mutableMapOf())
            Statement.CloseScope -> symbolTable.removeLast()
            is Statement.Print -> return statement.rValue.eval(symbolTable).toString()
            null -> return "Invalid syntax"
        }
        return null
    }

    /**
     * Reroute the input stream to the output stream after parsing and evaluating each line.
     * Ensure the buffer is flushed so that we see the output immediately.
     */
    fun interpret() {
        inputStream.bufferedReader().useLines { lines ->
            outputStream.bufferedWriter().use { writer ->
                lines.map (::parseLine).map (::eval).filterNotNull().forEach {writer.write(it); writer.newLine(); writer.flush()}
            }
        }
    }
}

fun main() {
    println("Cutelin interpreter 1.0.0 is fired up and ready to go!")
    Interpreter(System.`in`, System.out).interpret()
}
