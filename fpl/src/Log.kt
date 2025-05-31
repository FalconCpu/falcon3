object Log {
    val allErrors = mutableListOf<String>()

    fun error(message:String) {
        allErrors.add(message)
    }

    fun error(location:Location, message:String) {
        allErrors.add("$location: $message")
    }

    fun hasErrors() = allErrors.isNotEmpty()

    fun getErrors() = allErrors.joinToString("\n")

    fun clear() {
        allErrors.clear()
    }
}