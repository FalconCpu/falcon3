import java.io.Reader

class Lexer (val fileName:String, val fileHandle: Reader) {
    private var lineNumber = 1
    private var columnNumber = 1
    private var atEof = false
    private var nextEof = false
    private var currentChar = readChar()
    private var nextChar = readChar()
    private var startLine = 1
    private var startColumn = 1
    private var lastLine = 1
    private var lastColumn = 1
    private var lineContinues = true
    private var atStartOfLine = true
    private val indentStack = mutableListOf(1)

    private fun readChar():Char {
        val c = fileHandle.read()
        if (c == -1) {
            nextEof = true
            return '\n'
        }
        return c.toChar()
    }

    private fun nextChar() : Char {
        val ret = currentChar
        currentChar = nextChar
        atEof = nextEof
        lastLine = lineNumber
        lastColumn = columnNumber
        nextChar = readChar()

        if (ret=='\n') {
            lineNumber++
            columnNumber = 1
        } else {
            columnNumber++
        }

        return ret
    }

    private fun skipWhitespaceAndComments() {
        while (!atEof && (currentChar == ' ' || currentChar == '\t' || currentChar == '\r' || currentChar == '#' || (currentChar == '\n' && lineContinues))) {
            if (currentChar == '#')
                while (currentChar != '\n' && !atEof)
                    nextChar()
            else
                nextChar()
        }
    }

    private fun escapedChar() : Char {
        val c = nextChar()
        if (c != '\\')
            return c
        val c2 = nextChar()
        return when (c2) {
            'n' -> '\n'
            't' -> '\t'
            'r' -> '\r'
            else -> c2
        }
    }

    private fun getLocation() = Location(fileName, startLine, startColumn, lastLine, lastColumn)

    private fun readWord() : String {
        val sb = StringBuilder()
        while (currentChar.isLetterOrDigit() || currentChar == '_')
            sb.append(nextChar())
        return sb.toString()
    }

    private fun readNumber() : String {
        val sb = StringBuilder()
        while (currentChar.isLetterOrDigit())
            sb.append(nextChar())
        if (currentChar=='.' && nextChar.isDigit()) {
            sb.append(nextChar())
            while (currentChar.isLetterOrDigit())
                sb.append(nextChar())
        }
        return sb.toString()
    }

    private fun readString() : String {
        val sb = StringBuilder()
        nextChar()  // skip the opening quote
        while (currentChar != '"' && !atEof)
            sb.append(escapedChar())
        if (currentChar == '"')
            nextChar()
        else
            Log.error(getLocation(), "Unterminated string literal")
        return sb.toString()
    }

    private fun readCharLiteral() : String {
        val sb = StringBuilder()
        nextChar()  // skip the opening quote
        while (currentChar != '\'' && !atEof)
            sb.append(escapedChar())
        if (currentChar == '\'')
            nextChar()
        else
            Log.error(getLocation(), "Unterminated character literal")
        return sb.toString()
    }

    private fun readPunctuation() : String {
        val c = nextChar()
        if ( (c=='<' && currentChar=='=') ||
             (c=='>' && currentChar=='=') ||
             (c=='!' && currentChar=='=') ||
             (c=='<' && currentChar=='<') ||
             (c=='>' && currentChar=='>') ||
             (c=='-' && currentChar=='>') ||
             (c=='.' && currentChar=='.') ||
             (c=='?' && currentChar=='.') ||
             (c=='!' && currentChar=='!') ||
             (c=='+' && currentChar=='=') ||
             (c=='-' && currentChar=='=') ||
             (c=='?' && currentChar==':') ) {
            val ret = c.toString() + nextChar()
            if (ret==".." && currentChar == '.')
                return ret + nextChar()
            return ret
        }
        return c.toString()
    }

    fun nextToken() : Token {
        skipWhitespaceAndComments()
        startLine = lineNumber
        startColumn = columnNumber
        lastLine = lineNumber
        lastColumn = columnNumber

        val text : String
        val kind : TokenKind

        if (atEof) {
            if (!atStartOfLine)
                kind = TokenKind.EOL
            else if (indentStack.size > 1) {
                kind = TokenKind.DEDENT
                indentStack.removeAt(indentStack.size - 1)
            } else
                kind = TokenKind.EOF
            text = kind.text

        } else if (atStartOfLine && columnNumber>indentStack.last()) {
            kind = TokenKind.INDENT
            indentStack.add(columnNumber)
            text = kind.text

        } else if (atStartOfLine && columnNumber<indentStack.last()) {
            kind = TokenKind.DEDENT
            indentStack.removeAt(indentStack.size-1)
            text = kind.text
            if (columnNumber > indentStack.last()) {
                Log.error(getLocation(), "Indentation error - got column $columnNumber, expected ${indentStack.last()}")
                indentStack.add(columnNumber)
            }

        } else if (currentChar == '\n') {
            nextChar()
            kind = TokenKind.EOL
            text = kind.text

        } else if (currentChar.isLetter() || currentChar=='_') {
            text = readWord()
            kind = TokenKind.textToKind.getOrDefault(text, TokenKind.ID)

        } else if (currentChar.isDigit()) {
            text = readNumber()
            kind = if (text.contains('.')) TokenKind.REALLIT else TokenKind.INTLIT

        } else if (currentChar=='"') {
            text = readString()
            kind = TokenKind.STRINGLIT

        } else if (currentChar=='\'') {
            text = readCharLiteral()
            kind = TokenKind.CHARLIT

        } else {
            text = readPunctuation()
            kind = TokenKind.textToKind.getOrDefault(text, TokenKind.ERROR)
        }

        if (kind==TokenKind.ERROR)
            Log.error(getLocation(),"Invalid token '$text'")

        lineContinues = kind.lineContinues
        atStartOfLine = kind==TokenKind.EOL || kind==TokenKind.DEDENT

        return Token(getLocation(), kind, text)
    }
}