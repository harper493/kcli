class StyledText (
    private var text: String = "",
    private var color: String? = null,
    private var background: String? = null,
    private var style: String? = null
        ) {
    constructor(input: Iterable<StyledText>): this() {
        input.map{ append(it) }
    }
    private val nestedText = mutableListOf<StyledText>()
    private val isNested get() = nestedText.isNotEmpty()
    val length get() = text.length
    fun getColor() = color
    fun getText() = text

    fun render() = renderer(this)

    private fun append(st: StyledText) {
        if (st.isNested) {
            st.nestedText.map{ nestedText.add(it) }
        } else {
            nestedText.add(st.clone())
        }
    }

    fun isEmpty() = text.isEmpty() && nestedText.isEmpty()
    fun isNotEmpty() = text.isNotEmpty() || nestedText.isNotEmpty()

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

    fun justify(width: Int): StyledText {
        text = text.justify(width)
        return this
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

    private fun renderPlain(): String =
        if (nestedText.isEmpty()) {
            text
        } else {
            nestedText.map{ it.renderPlain() }.joinToString("")
        }

    private fun renderISO6429(): String =
        if (nestedText.isEmpty()) {
            "${renderStyle()}${renderColor(fgOp, color)}${renderColor(bgOp, background)}$text"
        } else {
            nestedText.map{ it.renderISO6429() }.joinToString("")
        }

    companion object {
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

        private const val escape = "\u001b"
        private const val fgOp = 38
        private const val bgOp = 48

        private lateinit var renderer: (StyledText) -> String
        fun addStyle(oldStyle: String?, newStyle: String) =
            addToTextList(oldStyle, newStyle)
        fun setRenderer(style: String) {
            renderer = when (style) {
                "ISO6429" -> { text -> text.renderISO6429() }
                else      -> { text -> text.renderPlain() }
            }
        }
        init { setRenderer("plain") }
    }
}

fun Iterable<StyledText>.join(separator: StyledText) =
    StyledText(joinWith(separator))

fun Iterable<StyledText>.join(separator: String) =
    join(StyledText(separator))
