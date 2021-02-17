class StyledText (
    val text: String,
    val color: String? = null,
    val background: String? = null,
    var style: String? = null
        )
{
    val colors = mapOf(
        "black" to 232,
        "red" to 9,
        "even_red" to 124,
        "green" to 40,
        "even_green" to 28,
        "yellow" to 11,
        "blue" to 20,
        "magenta" to 90,
        "cyan" to 14,
        "white" to 15,
        "grey" to 244,
        "even_grey" to 239,
        "orange" to 208,
        "pink" to 201,
        "brown" to 1,
        "odd" to 52,
        "even" to 22,
        "label" to 124,
        "even_label" to 28,
        "value" to 52,
        "even_value" to 22,
        "heading" to 232
    )

    val styles = mapOf(
        "none" to 0,
        "bold" to 1,
        "italic" to 3,
        "blink" to 5,
        "underline" to 4,
        "crossed" to 9,
        "inverted" to 7,
        )

    val escape = "\u001b"
    val fgOp = 38
    val bgOp = 48

    val length get() = text.length

    fun render() = "${renderStyle()}${renderColor(fgOp, color)}${renderColor(bgOp, background)}$text"

    fun clone (
        newText: String? = null,
        newColor: String? = null,
        newBackground: String? = null,
        newStyle: String? = null
    ) = StyledText(newText ?: text,
        newColor ?: color,
        newBackground ?: background,
        newStyle ?: style)

    fun addStyle(newStyle: String) {
        style = StyledText.addStyle(style, newStyle)
    }

    private fun renderColor(op: Int, color: String?) : String {
        val code = colors[color?:""]
        return if (code==null) "${escape}[${op+1}m"
               else "${escape}[${op}:5:${code!!}m"
    }

    private fun renderStyle() : String {
        return when (style) {
            null, "" -> "${escape}[0m"
            else -> return style!!.split(",")
                                .joinToString("", transform = { "${escape}[${styles[it] ?: 0}m" })
        }
    }

    companion object {
        fun addStyle(oldStyle: String?, newStyle: String) =
            (oldStyle ?: "").split(",").toMutableList().also { it.add(newStyle) }.joinToString(",")
    }
}