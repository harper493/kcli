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

fun String.splitAt(indices: Iterable<Int>): List<String> {
    var prevSplit = 0
    return indices
        .filter{it in 1..(this.length)}
        .makeAscending()
        .map{ index ->
            this.drop(prevSplit)
                .take(index - prevSplit)
                .also{ prevSplit = index }
        }
        .append(this.drop(prevSplit))
}

fun hyphenate(word: String, size: Int): Pair<String,String> =
    word.splitAt(
        (Properties.get("hyphenate", word.toLowerCase()) ?: word)
            .split("-")
            .map{ it.length }
            .let{
                when {
                    size < 0 && it.first() < word.length -> it.first()
                    size < 0 && it.first() >= word.length -> 0
                    else ->  it.chooseSplit(size, takeFirst = false)
                }
            })

fun String.splitBy(fn: (String)->Pair<String,String>): List<String> =
    if (isNotEmpty()) {
        fn(this).let {
            listOf(it.first).append(if (it.first.length>0) it.second.splitBy(fn) else listOf())
        }
    } else listOf()

fun String.splitUsing(splitter: (String)->Pair<String,String>, size: Int): List<String> {
    val substrs = splitBy { splitter(it) }
    return splitAt(substrs.map{it.length}.runningReduceLimit(size).runningReduce{ a,b -> a+b})
}


fun baseSplitter(text: String): Pair<String, String> {
    val m1 = Regex("^([^a-zA_Z0-9]*)([a-zA-Z0-9]*)(.*)$").find(text)?.groupValues!!
    return when {
        m1[3].isEmpty() -> Pair(text, "")
        m1[3][0] in ",;-" -> Pair(m1[1] + m1[2] + m1[3][0], m1[3].drop(1))
        else -> Pair(m1[1] + m1[2], m1[3])
    }
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

fun wrap(text: String, width: Int,
         force: Boolean = false,
         splitter: (String)->Pair<String,String> = { baseSplitter(it) }): Iterable<String> {
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
                if (w.isNotEmpty()) {
                    if (line.length + w.length + space.length < newWidth - 1) {
                        line = "$line$space${w}-"
                    } else {
                        r = w + r
                    }
                }
                if (line.isNotEmpty()) {
                    thisResult.add(line)
                    newWidth = maxOf(newWidth, line.length)
                    line = ""
                }
                if (r.length > newWidth) {
                    if (force) {
                        val h2 = hyphenate(r, -1)
                        if (h2.first.length > newWidth - 1) {
                            val chunks = h2.first.splitUsing(splitter, newWidth).map{ it.chunked(newWidth) }.flatten()
                            thisResult.addAll(chunks.dropLast(1))
                            r = chunks.last()
                        } else if (h2.first.length > 0) {
                            thisResult.add("${h2.first}-")
                            r = h2.second
                        } else {
                            r = h2.second
                        }
                        if (r.length > newWidth) {
                            val chunks = r.splitUsing(splitter, newWidth).map{ it.chunked(newWidth) }.flatten()
                            thisResult.addAll(chunks.dropLast(1))
                            r = chunks.last()
                        }
                    } else {
                        val h3 = hyphenate(r, -1)
                        if (h3.first.length > 0 ) {
                            thisResult.add("${h3.first}-")
                            r = h3.second
                        }
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

