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
 * split at the required length. Otherwise, it does its best but the
 * longest line  may be longer than [width].
 *
 * [splitter] should be a function which will split a string in an
 * an intelligent way. By default a string will be chunked at non-word
 * boundaries.
 */

fun wrap(text: String, width: Int, force: Boolean = false): List<String> {
    var newWidth = width
    var line = ""
    return text.trim().split(" ").map { it.trim() }
        .map { word ->
            var thisResult = mutableListOf<String>()
            val space = if (line.isEmpty()) "" else " "
            if (line.length + word.length + space.length < newWidth) {
                line = "$line$space$word"
            } else {
                if (!force) {
                    newWidth = maxOf(newWidth, line.length)
                }
                var (w, r) = hyphenate(word, maxOf(0, newWidth - line.length - space.length - 1))
                if (w.isNotEmpty()) line = "$line$space${w}-"
                if (line.isNotEmpty()) {
                    thisResult.add(line)
                    newWidth = maxOf(newWidth, line.length)
                    line = ""
                }
                if (r.length > newWidth) {
                    val h2 = hyphenate(r, -1)
                    if (h2.first.isNotEmpty()) {
                        thisResult.add("${h2.first}-")
                    }
                    r = h2.second
                    if (force) {
                        val chunks = r.chunked(newWidth)
                        thisResult.addAll(chunks.dropLast(1))
                        r = chunks.last()
                    }
                }
                line = r
            }
            thisResult
        }.flatten()
        .append(if (line.isNotEmpty()) listOf(line) else listOf())
}

/**
 * Hyphenate a [word], choosing the longest fragment that will fit in [size],
 * returning a pair corresponding to the split. If there is no suitable hyphenation
 * the result is an empty string followed by the full word.
 *
 * If [size] is -1, pick the shortest fragment.
 *
 * This doesn't look obvious. The steps are:
 *
 * 1. Find the possible hyphenation of this word, in the form e.g. "app-li-ca-tion"
 * 2. Turn that into all possible prefixes: app, appli, applica, application
 * 3. If [size] is non-negative, pick the largest fragment that fits, else pick the first one
 * 4. Split the word at the length of the largest fragment.
 */

fun hyphenate(word: String, size: Int) =
    word.splitAt((Properties.get("hyphenate", word.toLowerCase())?.split("-") ?: listOf(word))
        .runningReduce { pfx, _ -> pfx }
        .let { pfxs ->
            if (size < 0) pfxs.firstOrNull()
               else pfxs.lastOrNull { it.length < size }}
        ?.length ?: 0)

fun String.splitAt(index: Int) =
    if (index < length) Pair(take(index), drop(index)) else Pair(this, "")

fun String.splitBy(fn: (String)->Pair<String,String>): List<String> =
    if (isNotEmpty()) {
        fn(this).let {
            listOf(it.first).append(it.second.splitBy(fn))
        }
    } else listOf()


/**
 * Take a string of the form abc_def_ghi and turn it into "Abc Def Ghi"
 */

fun makeNameHuman(name: String) = name.split("_").joinToString(" ") { it.capitalize() }

