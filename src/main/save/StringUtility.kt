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

fun String.splitAt(index: Int) =
    if (index < length) Pair(take(index), drop(index)) else Pair(this, "")

fun hyphenate(word: String, size: Int): Pair<String,String> {
    val x99 = Properties.get("hyphenate", word.toLowerCase()) ?: word
    val x97 = x99?.split("-")
    val x0a = x97.map{ it.length }
    val x1 = x0a.chooseSplit(size, takeFirst=false)

    return word.splitAt(x1)
}


fun Iterable<Int>.chooseSplit(size: Int, takeFirst: Boolean=false): Int =
    runningReduce { pfx, sz -> pfx + sz }
        .let { cumSizes ->
            when {
                cumSizes.isEmpty() -> 0
                takeFirst && cumSizes.first() >= size -> cumSizes.first()
                else -> cumSizes.lastOrNull { it <= size }!!
            }
        }

fun String.splitBy(fn: (String)->Pair<String,String>): List<String> =
    if (isNotEmpty()) {
        fn(this).let {
            listOf(it.first).append(it.second.splitBy(fn))
        }
    } else listOf()
/*
fun String.divideUsing(rx: String, size: Int): Pair<String, String> {
    val x0 = this.splitAt(this
        .splitBy { s1 ->
            Regex("(.*?)(${rx}.*)?").find(s1)?.groupValues
                ?.let { match -> Pair(match[1], match[2]) } ?: Pair("", "")
        }.let { chooseSplit(it, size) })
    val x1 = x0.swap()
    return x1
}

 */

fun String.splitUsing(rx: String, size: Int): List<String> {
    val substrs = splitBy { s1 ->
        val p0 = Regex("^([a-zA-Z0-9]*?)([^a-zA-Z0-9].*)?$")
        val xx0 = p0.find(s1)?.groupValues
        val xx1 = xx0?.let { match -> Pair(match[1], match[2]) } ?: Pair("", "")
        xx1.let { it.swapIf { it.first.isEmpty() } }
    }
    return splitBy { splitAt(substrs.map { it.length }.chooseSplit(size, takeFirst = true)) }
}


/**
 * Wrap a string to fit within a given width, breaking the text at spaces as
 * necessary. If [force] is true, lines which are still too long are just
 * split at the required length. Otherwise, it does its best but the
 * longest line  may be longer than [width].
 *
 * [splitter] should be a regex which will split a string in an
 * an intelligent way. By default a string will be chunked at non-word
 * boundaries.
 */

fun wrap(text: String, width: Int, force: Boolean = false, splitter: String=""":"""): Iterable<String> {
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
                if (w.isNotEmpty()
                    && line.length + word.length + space.length < newWidth - 1) {
                    line = "$line$space${w}-"
                }
                if (line.isNotEmpty()) {
                    thisResult.add(line)
                    newWidth = maxOf(newWidth, line.length)
                    line = ""
                }
                if (force && r.length > newWidth) {
                    val h2 = hyphenate(r, -1)
                    if (h2.first.length > newWidth - 1) {
                        val chunks = h2.first.splitUsing(splitter, newWidth)
                        //val chunks = h2.first.chunked(newWidth)
                        thisResult.addAll(chunks.dropLast(1).map{it.chunked(newWidth)}.flatten())
                        r = chunks.last()
                    } else {
                        thisResult.add("${h2.first}-")
                        r = h2.second
                    }
                }
                line = r
            }
            thisResult
        }.flatten()
        .appendIf(line){line.isNotEmpty()}
}

fun foo() = 1

//splitBy { it.divideUsing(rx, size) }

            /**
 * Take a string of the form abc_def_ghi and turn it into "Abc Def Ghi"
 */

fun makeNameHuman(name: String): String =
    name.split("_").map { it.capitalize() }.joinToString(" ")

