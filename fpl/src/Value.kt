import kotlin.math.min

// This class is used to represent compile time data

val allValues = mutableListOf<Value>()

sealed class Value(val index:Int, val type:Type) {
    override fun toString(): String = "OBJ$index"
    abstract fun emit(sb:StringBuilder)
}

// Integer Values are a bit of a special case as they don't need to go in the .data section (hence don't have an index)
class ValueInt(val value:Int, type:Type) : Value(-1, type) {
    override fun emit(sb: StringBuilder) {
        error("ValueInt should not be in allValues list")
    }
}

class ValueString private constructor(val value:String, index:Int, type:Type) : Value(index, type) {

    override fun toString(): String = "OBJ$index"

    override fun emit(sb:StringBuilder) {
        val comment = value.substring(0, min(value.length, 20)).replace("\n", "")
        sb.append("dcw ${value.length}\n")
        sb.append("OBJ$index: # $comment\n")
        for (c in value.chunked(4)) {
            val data = c[0].code +
                    (if (c.length > 1) (c[1].code shl 8) else 0) +
                    (if (c.length > 2) (c[2].code shl 16) else 0) +
                    (if (c.length > 3) (c[3].code shl 24) else 0)
            sb.append("dcw $data\n")
        }
        sb.append("\n")
    }

    companion object {
        val allStrings = mutableMapOf<String, ValueString>()
        fun create(value:String, type:Type) = allStrings.getOrPut(value) {
            val new = ValueString(value, allStrings.size, type)
            allStrings[value] = new
            allValues += new
            new
        }
    }
}

// Class descriptors are also a special case, as they are generated separately. We just need to reference them
class ValueClassDescriptor(val value:TypeClass) : Value(-1, value) {
    override fun toString() = "$value/class"

    override fun emit(sb: StringBuilder) {
        error("ValueClassDescriptor should not be in allValues list")
    }
}

fun List<Value>.emit(sb: StringBuilder) {
    for(value in this)
        value.emit(sb)
}