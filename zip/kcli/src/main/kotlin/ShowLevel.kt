enum class ShowLevel{
    list, brief, full, detail, expert, debug;

    companion object {
        fun parse(value: String, missOk:Boolean = false) =
            values().find{ it.name==value }
                ?: if (missOk) null else throw CliException("invalid show level '$value'")
    }
}