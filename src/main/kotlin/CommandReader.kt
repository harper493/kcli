import java.io.File
// Date and time
// Timers functionality
// Tmux functionality

// jline-builtins
// jline-keymap
// jline-reader
import org.jline.reader.*
import org.jline.reader.impl.DefaultParser
// jline-terminal
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
// jline-utils

class CommandCompleter : Completer {
    override fun complete(reader: LineReader, line: ParsedLine, candidates: MutableList<Candidate>) {
        try {
            CliCommand("${line.line()}${Parser.completerCh}")
        } catch (exc: CompletionException) {
            exc.completions.map{ candidates.add(Candidate(it))}
        } catch (exc: Exception) {
            // do nothing
        }
    }
}

class CompletionException(val completions: List<String>): Exception("")

object CommandReader {
    //val width = "tput cols".runCommand(File(".")) ?: "0".toInt()
    private val builder: TerminalBuilder = TerminalBuilder.builder()
    private val terminal: Terminal = builder.build()
    private val completer = CommandCompleter()
    private var prompt: String = ""; private set

    private val reader: LineReader = LineReaderBuilder.builder()
        .terminal(terminal)
        .completer(completer)
        .parser(DefaultParser())
        .build()

    fun setPrompt(p: String) { prompt = p }

    fun read(myPrompt: String? = null): String =
        try {
            reader.readLine(myPrompt ?: prompt)
        } catch (exc: UserInterruptException) {
            throw CliException("")
        } catch (exc: EndOfFileException) {
            throw CliException("")
        }

    fun readPassword(myPrompt: String? = null): String {
        reader.option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
        val result = reader.readLine (myPrompt ?: prompt, '*')
        reader.option(LineReader.Option.DISABLE_EVENT_EXPANSION, false)
        return result
    }
}





