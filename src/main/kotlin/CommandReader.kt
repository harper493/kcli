import java.io.File
// Date and time
// Timers functionality
// Tmux functionality

// jline-builtins
// jline-keymap
// jline-reader
import org.jline.reader.*
import org.jline.reader.impl.DefaultParser
import org.jline.reader.impl.history.DefaultHistory
// jline-terminal
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
// jline-utils

class CommandCompleter : Completer {
    override fun complete(reader: LineReader, line: ParsedLine, candidates: MutableList<Candidate>) {
        try {
            CliCommand("${line.line()}${Parser.completerCh}")
        } catch (exc: CompletionException) {
            exc.completions.map { candidates.add(Candidate(it)) }
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
    private var prompt: String = ""
    private var history = DefaultHistory()
    var lastPrompt: String = ""; private set

    private val reader: LineReader = LineReaderBuilder.builder()
        .terminal(terminal)
        .completer(completer)
        .history(history)
        .variable(LineReader.HISTORY_FILE, "${Cli.kcliDir}/command_history.txt")
        .also{ history.load() }
        .parser(DefaultParser())
        .build()


    fun setPrompt(p: String) { prompt = p }

    fun removeLastHstory() = also {
        history.previous()
        history.removeAll { it.index() == history.last() }
    }

    fun read(myPrompt: String? = null, makeHistory: Boolean=true): String =
        try {
            lastPrompt = myPrompt ?: prompt
            reader.readLine(lastPrompt).also {
                if (!makeHistory) {
                    removeLastHstory()
                }
            }
        } catch (exc: UserInterruptException) {
            throw CliException("")
        } catch (exc: EndOfFileException) {
            throw CommandException(terminate=true)
        }

    fun readPassword(myPrompt: String? = null): String = let {
        reader.option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
        reader.readLine(myPrompt ?: prompt, '*').also {
            reader.option(LineReader.Option.DISABLE_EVENT_EXPANSION, false)
            removeLastHstory()
        }
    }

    fun saveHistory() {
        history.save()
    }

    fun getHistory(limit: Int = history.last()+1) =
        (maxOf(history.first(), history.last()-limit+1)..history.last())
            .map { it+1 to history.get(it) }
}





