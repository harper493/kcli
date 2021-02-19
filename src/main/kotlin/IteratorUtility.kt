/**
 * Take two different collections and zip them into a list of pairs of corresponding
 * elements. (This must surely exist somewhere but I couldn't find it).
 */

fun <U, V> zip(u: Iterable<U>, v: Iterable<V>): List<Pair<U, V>> {
    val result: MutableList<Pair<U, V>> = mutableListOf()
    val ui = u.iterator()
    val vi = v.iterator()
    while (ui.hasNext() && vi.hasNext()) {
        result.add(Pair(ui.next(), vi.next()))
    }
    return result
}

/**
 * Take three different collections and zip them together into a list of triples.
 */

fun <U, V, W> zip(u: Iterable<U>, v: Iterable<V>, w: Iterable<W>): List<Triple<U, V, W>> {
    val result: MutableList<Triple<U, V, W>> = mutableListOf()
    val ui = u.iterator()
    val vi = v.iterator()
    val wi = w.iterator()
    while (ui.hasNext() && vi.hasNext() && wi.hasNext()) {
        result.add(Triple(ui.next(), vi.next(), wi.next()))
    }
    return result
}

/**
 * Given a list of lists, return a list of lists transposed, i.e. if they
 * were in column order, they are now in row order
 */

fun<T> Iterable<Iterable<T>>.transpose() : Iterable<Iterable<T>> {
    val colIters = map{ it.iterator() }
    val result: MutableList<List<T>> = mutableListOf()
    if (colIters.isNotEmpty()) {
        while (colIters.map { it.hasNext() }.all { it }) {
            result.add(colIters.map { it.next() })
        }
    }
    return result
}

/**
 * Given a list of lists, return the size of the largest sub-list
 */

fun<T> Iterable<List<T>>.maxSize() : Int = map{it.size}.maxOrNull() ?: 0

/**
 * Make a [Sequence] returning elements from the iterable and saving a copy of each.
 * When the iterable is exhausted, return elements from the saved copy. Repeats indefinitely.
 *
 */

fun<T> Iterable<T>.cycle(): Sequence<T> = sequence {
    val saved = mutableListOf<T>()
    for (elem in this@cycle) {
        saved.add(elem)
        yield(elem)
    }
    while (true) {
        for (elem in saved) yield(elem)
    }
}

/**
 * Return the given iterable if it is not empty, else null
 */

fun<T> Iterable<T>.anyOrNull(): Iterable<T>? = if (any()) this else null

/**
 * Chain one iterator after another
 */

fun<T> Iterable<T>.chain(next: Iterable<T>) = sequence<T> {
    for (elem in this@chain) {
        yield(elem)
    }
    for (elem in next) {
        yield(elem)
    }
}

/**
 * Append one list to another
 */

fun<T> MutableList<T>.append(other: Iterable<T>): MutableList<T> {
    for (t in other) {
        add(t)
    }
    return this
}

/**
 * Append one set to another
 */

fun<T> MutableSet<T>.append(other: Iterable<T>): MutableSet<T> {
    for (t in other) {
        add(t)
    }
    return this
}

/**
 * Repeatedly run the same function over a collection until all calls return false
 */

fun<T> Iterable<T>.mapWhile(fn: (T)->Boolean) {
    var more = true
    while (more) {
        more = map(fn).any{it}
    }
}