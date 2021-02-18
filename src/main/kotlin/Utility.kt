/**
 * Returns a comma (or other [delimiter]) separated list with the addition of one or more
 * new items. The result is null if it would otherwise be empty.
 */

fun addToTextList(old: String?, new: String, delimiter: String = ",") =
    (old ?: "")
        .split(delimiter)
        .toMutableList()
        .also { it.addAll(new.split(delimiter)) }
        .joinToString(delimiter)
        .ifBlank { null }

/**
 * Wrap a string to fit within a given width, breaking the text at spaces as
 * necessary. If [force] is true, lines which are still too long are just
 * split at the required length.
 */

fun wrap(text: String, width: Int, force: Boolean = false): List<String> {
    val result: MutableList<String> = mutableListOf()
    var words = text.trim().split(" ").map { it.trim() }
    var line = ""
    while (words.isNotEmpty()) {
        val word = words[0]
        if (line.length + word.length < width) {
            line = "$line${if (line.isEmpty()) "" else " "}${word}"
        } else {
            if (line.isNotEmpty()) {
                result += line
            }
            if (force && word.length > width) {
                result += word.chunked(width)
                line = ""
            } else {
                line = word
            }
        }
        words = words.drop(1)
    }
    if (line.isNotEmpty()) {
        result += line
    }
    return result
}

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
 * Take a string of the form abc_def_ghi and turn it into "Abc Def Ghi"
 */

fun makeNameHuman(name: String) = name.split("_").joinToString(" ") { it.capitalize() }

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