import java.text.SimpleDateFormat
import java.util.Date

/**
 * Split a string into two at the given [index]
 */

fun String.splitAt(index: Int) =
    if (index < length) Pair(take(index), drop(index)) else Pair(this, "")

/**
 * Split a string into multiple pieces at the given locations. Any index
 * which is out of range or out of order is ignored.
 */

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

/**
 * Given a [splitter] function that will divide a string in two, apply it repeatedly
 * to break the string into multiple pieces wherever the splitter applies.
 */

fun String.splitBy(splitter: (String)->Pair<String,String>): List<String> =
    if (isNotEmpty()) {
        splitter(this).let {
            listOf(it.first).append(if (it.first.isNotEmpty()) it.second.splitBy(splitter) else listOf())
        }
    } else listOf()

/**
 * Given a splitter function and a target size, split the string into pieces no
 * larger than the given size, according to the splitter function, if possible.
 */

fun String.splitUsing(splitter: (String)->Pair<String,String>, size: Int): List<String> {
    val substrs = splitBy { splitter(it) }
    return splitAt(substrs.map{it.length}.runningReduceLimit(size).runningReduce{ a,b -> a+b})
}

/**
 * Hyphenate string, choosing the longest fragment that will fit in [size],
 * returning a pair corresponding to the split. If there is no suitable hyphenation
 * the result is an empty string followed by the full word.
 *
 * If [size] is -1, pick the shortest fragment.
 */

fun String.hyphenate(size: Int): Pair<String,String> =
    if ("-" in this) Pair("", this)
    else splitAt(
        (Properties.get("hyphenate", this.toLowerCase()) ?: this)
            .split("-")
            .map{ it.length }
            .let{
                when {
                    size < 0 && it.first() < length -> it.first()
                    size < 0 && it.first() >= length -> 0
                    else ->  it.chooseSplit(size, takeFirst = false)
                }
            })

/**
 * Default splitter function for wrap. Split the string at the first non-alphanumeric
 * character, ignoring any such at the beginning. If the character is in ',;-' split
 * immediately after it. Otherwise, splt before it.
 */

fun baseSplitter(text: String): Pair<String, String> =
    Regex("^([^a-zA_Z0-9]*)([a-zA-Z0-9]*)(.*)$").find(text)?.groupValues!!
        .let{ match ->
            val (prefix, body, remnant) = Triple(match[1], match[2], match[3])
            when {
                remnant.isEmpty() -> Pair(text, "")
                remnant[0] in ",;-" -> Pair("$prefix$body${remnant[0]}", remnant.drop(1))
                else -> Pair("$prefix$body", remnant)
            }
        }

/**
 * Wrap a string to fit within a given width, breaking the text at spaces as
 * necessary. If [force] is true, lines which are still too long are just
 * split at the required length. Otherwise, it does its best but the
 * longest line  may be longer than [width].
 *
 * [splitter] should be a regex which will split a string in an
 * an intelligent way (see baseSplitter).
 */

fun String.wrap(width: Int, force: Boolean = false,
                splitter: (String)->Pair<String,String> = { baseSplitter(it) }): Iterable<String> =
    with (split("\n")) {
        if (width==0) this
        else map { it.wrapLine(width, force, splitter) }.flatten()
    }

fun String.wrapLine(width: Int,
         force: Boolean = false,
         splitter: (String)->Pair<String,String>): Iterable<String> {
    var newWidth = width
    var line = ""
    return if (width==0) {
        listOf(this)
    } else {
        this.trim().split(" ").map { it.trim() }
            .map { word ->
                val thisResult = mutableListOf<String>()
                val space = if (line.isEmpty()) "" else " "
                if (line.length + word.length + space.length <= newWidth) {
                    line = "$line$space$word"
                } else {
                    if (!force) {
                        newWidth = maxOf(newWidth, line.length)
                    }
                    var (prefix, residue) = word.hyphenate(maxOf(0, newWidth - line.length - space.length - 1))
                    if (prefix.isNotEmpty()) {
                        if (line.length + prefix.length + space.length <= newWidth - 1) {
                            line = "$line$space${prefix}-"
                        } else {
                            residue = prefix + residue
                        }
                    }
                    if (line.isNotEmpty()) {
                        thisResult.add(line)
                        newWidth = maxOf(newWidth, line.length)
                        line = ""
                    }
                    if (residue.length > newWidth) {
                        if (force) {
                            residue.hyphenate(-1)
                                .also {
                                    prefix = it.first
                                    residue = it.second
                                }
                            if (prefix.length > newWidth - 1) {
                                val chunks =
                                    prefix.splitUsing(splitter, newWidth).map { it.chunked(newWidth) }.flatten()
                                thisResult.addAll(chunks.dropLast(1))
                                residue = chunks.last()
                            } else if (prefix.isNotEmpty()) {
                                thisResult.add("${prefix}-")
                            }
                            if (residue.length > newWidth) {
                                val chunks =
                                    residue.splitUsing(splitter, newWidth).map { it.chunked(newWidth) }.flatten()
                                thisResult.addAll(chunks.dropLast(1))
                                residue = chunks.last()
                            }
                        } else {
                            val h3 = residue.hyphenate(-1)
                            if (h3.first.isNotEmpty()) {
                                thisResult.add("${h3.first}-")
                                residue = h3.second
                            }
                        }
                    }
                    line = residue
                }
                thisResult
            }.flatten()
            .appendIf(line) { line.isNotEmpty() }
    }
}

/**
 * Take a string of the form abc_def_ghi and turn it into "Abc Def Ghi"
 */

fun makeNameHuman(name: String): String =
    name.split("_").joinToString(" ") { it.capitalize() }

/**
 * Returns a comma (or other [delimiter]) separated list with the addition of one or more
 * new items. The result is null if it would otherwise be empty.
 */

fun addToTextList(old: String?, new: String, delimiter: String = ",") =
    (old ?: "")
        .split(delimiter)
        .filter{ it.isNotEmpty() }
        .toMutableList()
        .also { it.addAll(new.split(delimiter)) }
        .joinToString(delimiter)
        .ifBlank { null }

/**
 * Return time/date in format yyyy-mm-dd hh:mm:ss
 */

fun getDateTime() =
    SimpleDateFormat("yyyy-MM-dd hh:mm:ss").format(Date())

/*
* Convert just the first character of a string to uppercase
 */

fun String.uppercaseFirst() = take(1).toUpperCase() + drop(1)

/**
 * Get a line of input from the user, with prompt
 */

fun getUserInput(prompt: String): String {
    print(prompt)
    return readLine() ?: ""
}

/**
 * Return true iff any of the given characters are present in the string
 */

fun String.containsAnyOf(chars: String) =
    chars.fold(false) { b, c -> b || c in this }

/**
 * Pad a sting to fit the given [width]. If [width] is negative, left jusify,
 * otherwise right justify. If [width] is zero, do nothing.
 */

fun String.justify(width: Int) = when {
    width < 0 -> padStart(-width)
    width > 0 -> padEnd(width)
    else      -> this // ==0
}

/**
 * Get a yes/no answer from the user, return true iff yes. Allow qut for backward compatibility as
 * synonym for "no".
 */

fun readYesNo(prompt: String, defaultNo: Boolean=true, allowQuit: Boolean=false): Boolean {
    val keywords = KeywordList("yes", "no")
    if (allowQuit) {
        keywords.addKeys("quit")
    }
    while (true) {
        val answer = CommandReader.read("$prompt (${defaultNo.ifElse("y/N", "Y/n")})? ",
            makeHistory=false)
        if (answer.isEmpty()) {
            return !defaultNo
        } else {
            val k = keywords.match(answer)
            if (k.isNotEmpty()) {
                return k.first()?.asString()=="yes"
            }
        }
    }
}

/**
 * Remove from a list of strings any which are a prefix of another
 */

fun Iterable<String>.removePrefixes() =
    removeDuplicates{ a,b -> b.startsWith(a) }

/**
 * Write a string to a file
 */

fun String.writeToFile(filename: String) {
    java.io.PrintWriter(filename).let { file ->
        file.append(this)
        file.flush()
        file.close()
    }
}

/*

 */

fun String.orBlankIf(pred: ()->Boolean) = if(pred()) "" else this

/**
 * Read the content of a file
 */

fun readFile(filename: String) =
    java.io.File(filename).readText()

fun readFileOrEmpty(filename: String) =
    try {
        readFile(filename)
    } catch(exc: Exception) {
        ""
    }

/*
Get a unique id for something
 */

fun<T> T.adr(): String = Integer.toHexString(System.identityHashCode(this))

/**
 * Replace regex according to given pattern
 */

fun String.regexReplace(rxstr: String,
                        transform: (MatchResult)->String,
                        option: RegexOption?=null): String =
    let {
        (if (option==null) Regex(rxstr) else Regex(rxstr, option))
            .replace(it, transform)
    }

