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
 * Take a string of the form abc_def_ghi and turn it into "Abc Def Ghi"
 */

fun makeNameHuman(name: String) = name.split("_").joinToString(" ") { it.capitalize() }

