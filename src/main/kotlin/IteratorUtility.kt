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

fun<T> Iterable<T>.chain(last: T) = sequence<T> {
    for (elem in this@chain) {
        yield(elem)
    }
    yield(last)
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

fun<T> Iterable<T>.append(other: Iterable<T>) = listOf(this, other).flatten()

fun<T> Iterable<T>.appendIf(other: Iterable<T>, fn: ()->Boolean) =
    if (fn()) append(other) else this

fun<T> Iterable<T>.append(other: T) = append(listOf(other))

fun<T> Iterable<T>.appendIf(other: T, fn: ()->Boolean) =
    if (fn()) append(other) else this

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

/**
 * Given  function which will split a sequence into two parts, apply it
 * repeatedly to the second part of the split, e.g. if the split function
 * splits at a number greater than 10 then applying it to the sequence
 * 1 2 11 3 4 12 13 5 => (1,2) (11,2,3) (12) (13,5)
 */

fun<T> Iterable<T>.splitBy(fn: (Iterable<T>)->Pair<Iterable<T>, Iterable<T>>): List<Iterable<T>> =
     if (iterator().hasNext()) {
         fn(this).let {
             listOf(it.first).append(it.second.splitBy(fn))
         }
     } else listOf<List<T>>()

/**
 * Swap the two elements of a pair
 */

fun<T,U> Pair<T,U>.swap() = Pair(second, first)

/**
 * Swap the two elements of a pair iff the predicate is satisfied
 */

fun<T> Pair<T,T>.swapIf(pred: (Pair<T,T>)->Boolean) = if (pred(this)) swap() else this

/**
 * Given a sequence of integers and a limit, return the highest partial
 * sum starting at the beginning which is less than or equal to the limit.
 * If the first value is already too large, return either it or 0 depending
 * on whether takeFirst is true.
 */

fun Iterable<Int>.chooseSplit(limit: Int, takeFirst: Boolean=false): Int =
    runningReduce { pfx, sz -> pfx + sz }
        .let { cumSizes ->
            when {
                cumSizes.isEmpty() -> 0
                takeFirst && cumSizes.first() >= limit -> cumSizes.first()
                else -> cumSizes.lastOrNull { it <= limit } ?: 0
            }
        }

/**
 * Given a sequence of integers, return only those which are greater
 * than all previous values.
 */

fun Iterable<Int>.makeAscending() =
    take(1)
        .append(zip(this.runningReduce{a, b -> maxOf(a, b)}.dropLast(1),
            this.drop(1))
            .filter{ it.first < it.second }
            .map{ it.second })

