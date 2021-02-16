class Reader (
    var prompt: String,
    var initial_line: String = ""
    ) {
    var cur_prompt = prompt
    var parser: Parser? = null
}