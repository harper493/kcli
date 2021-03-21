class StyledText (
    val text: String,
    private var color: String? = null,
    private var background: String? = null,
    private var style: String? = null
        ) {
    private val colors = mapOf(
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
        "yucky_green" to 52,
        "yucky_brown" to 22,
        "label" to 124,
        "even_label" to 28,
        "value" to 52,
        "even_value" to 22,
        "heading" to 232
    )

    private val styles = mapOf(
        "none" to 0,
        "bold" to 1,
        "italic" to 3,
        "blink" to 5,
        "underline" to 4,
        "crossed" to 9,
        "inverted" to 7,
    )

    private val escape = "\u001b"
    private val fgOp = 38
    private val bgOp = 48

    val length get() = text.length
    fun getColor() = color

    fun render(width: Int = 0) = renderer(this, width)

    fun clone(
        newText: String? = null,
        newColor: String? = null,
        newBackground: String? = null,
        newStyle: String? = null
    ) = StyledText(
        newText ?: text,
        newColor ?: color,
        newBackground ?: background,
        newStyle ?: style
    )

    fun underride(
        newColor: String? = null,
        newBackground: String? = null,
        newStyle: String? = null
    ): StyledText {
        color = color ?: newColor
        background = background ?: newBackground
        style = style ?: newStyle
        return this
    }

    fun override(
        newColor: String? = null,
        newBackground: String? = null,
        newStyle: String? = null
    ): StyledText {
        color = newColor ?: color
        background = newBackground ?: background
        style = newStyle ?: style
        return this
    }

    fun addStyle(newStyle: String) {
        style = addStyle(style, newStyle)
    }

    private fun renderColor(op: Int, color: String?): String {
        val code = colors[color ?: ""]
        return if (code == null) "${escape}[${op + 1}m"
        else "${escape}[${op}:5:${code}m"
    }

    private fun renderStyle(): String {
        return when (style) {
            null, "" -> "${escape}[0m"
            else -> return style!!.split(",")
                .joinToString("", transform = { "${escape}[${styles[it] ?: 0}m" })
        }
    }

    private fun justify(width: Int) = when {
        width < 0 -> text.padStart(-width)
        width > 0 -> text.padEnd(width)
        else -> text // ==0
    }

    private fun renderISO6429(width: Int = 0) =
        "${renderStyle()}${renderColor(fgOp, color)}${renderColor(bgOp, background)}${justify(width)}"

    companion object {
        private lateinit var renderer: (StyledText, Int) -> String
        fun addStyle(oldStyle: String?, newStyle: String) = addToTextList(oldStyle, newStyle)
        fun setRenderer(style: String) {
            renderer = when (style) {
                "ISO6429" -> { text, width -> text.renderISO6429(width) }
                else      -> { text, width -> text.justify(width) }
            }
        }
        init { setRenderer("plain") }
    }
}