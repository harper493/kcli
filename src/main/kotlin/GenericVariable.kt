abstract class GenericVariable {
    open fun less(other: GenericVariable): Boolean { return false }
    open fun equal(other: GenericVariable): Boolean { return false }
    open fun add(other: GenericVariable) { }
    override fun toString(): String = ""
    open fun toFloat(): Double = 0.0
    open fun toBool(): Boolean = false
}

open class TypedGenericVariable<T: Comparable<T>>(initial: T): GenericVariable() {
    var value: T = initial; private set
    override fun less(other: GenericVariable): Boolean {
        return try {
            this.value < (other as TypedGenericVariable<T>).value
        } catch (e: Exception) {
            false
        }
    }
    override fun equal(other: GenericVariable): Boolean {
        return try {
            value == (other as TypedGenericVariable<*>).value
        } catch (e: Exception) {
            false
        }
    }
    override fun toString(): String {
        return value.toString()
    }
}

open class NumericGenericVariable<T: Comparable<T>>(initial: T, initialNumeric: Double): TypedGenericVariable<T>(initial) {
    var numValue: Double = initialNumeric; private set
    override fun add(other: GenericVariable) {
        try {
            numValue += (other as NumericGenericVariable<*>).numValue
        } catch (e: Exception) {
        }
    }
    override fun toFloat() = numValue
    override fun toBool() = numValue!=0.0
}
