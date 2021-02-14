
import javax.print.attribute.IntegerSyntax

abstract class GenericVariable {
    open fun less(other: GenericVariable): Boolean { return false }
    open fun equal(other: GenericVariable): Boolean { return false }
    open fun add(other: GenericVariable): Unit { }
    open fun string(): String { return "" }
    open fun getFloat(): Double { return 0.0 }
}

open class TypedGenericVariable<T: Comparable<T>>(initial: T): GenericVariable() {
    protected var _value: T = initial
    val value: T get() = _value
    override fun less(other: GenericVariable): Boolean {
        try {
            return this._value < (other as TypedGenericVariable<T>)._value
        } catch (e: Exception) {
            return false
        }
    }
    override fun equal(other: GenericVariable): Boolean {
        try {
            return _value == (other as TypedGenericVariable<T>)._value
        } catch (e: Exception) {
            return false
        }
    }
    override fun string(): String {
        return _value.toString()
    }
}

open class NumericGenericVariable<T: Comparable<T>>(initial: T, initial_numeric: Double): TypedGenericVariable<T>(initial) {
    protected var _num_value: Double = initial_numeric
    override fun add(other: GenericVariable): Unit {
        try {
            var rhs: Double = (other as NumericGenericVariable<T>)._num_value
            _num_value += rhs
        } catch (e: Exception) {
        }
    }
    override fun getFloat(): Double { return _num_value }
}

/*
fun test(j: GenericVariable): Boolean {
    var i = TypedGenericVariable<Int>(0)
    var a = mutableListOf<GenericVariable>(i)
    var j = NumericGenericVariable<Int>(1, 1.0)
    a.add(i)
    a.add(j)
    for (aa in a) {
        println(aa.string())
    }
    return i.less(j)
}
*/
