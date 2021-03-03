import org.jline.builtins.Commands
import org.jline.builtins.Completers
import org.jline.builtins.Completers.TreeCompleter
import org.jline.builtins.Options.HelpException
import org.jline.builtins.TTop
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder

fun main(args: Array<String>) {
    test()
    /*
    val terminal = TerminalBuilder.builder().build()
    println("${terminal.getName()}: ${terminal.getType()} ${terminal.getWidth()}")
    println("\nhelp: list available commands")

     */

    //val terminalWidth = org.jline.terminal.TerminalBuilder. get().getWidth()
    //println("terminal width = $terminalWidth")
    Datatype.load()
    Properties.load("/etc/kcli/objects.properties")
              .load("/etc/kcli/cli.properties")
    Rest.connect(server="192.168.1.70", user="kcli", password="KcliPw#1", trace=true)
    Metadata.load()
    while (true) {
        print("kcli# ")
        //val command = "interface stm2 primary_address 10.1.1.5/32 rate 9700" //readLine() ?: ""
        //val command = "show flow 100354 detail"
        //val command = "show flows select dest_mac with byte_count>10000 and packet_count>100"
        val command = "application youtube desc foo priority 3021"


        try {
            println(command)
            Cli().oneLine(command)
            break //@@@
        } catch (exc: CliException) {
            if (exc.message ?: "" != "") {
                println(exc.message)
            }
            break
        }
    }
    print(StyledText("").renderISO6429())
}

fun test() {
/*
    val x4 = listOf(1,2,3,10,3,2,1).runningReduceLimit(3)
    val x2 = listOf(1,2,3,10,3,2,1).runningReduceLimit(17)
    val x0 = listOf(1,2,3,10,3,2,1).runningReduceLimit(8)
    val x1 = listOf(1,1,2,3,10,3,2,1).runningReduceLimit(2)
    val w0 = listOf(1,3,5,7,9).windowed(2)
    val a0 = listOf(1,3,5,7,9).makeAscending()
    val a1 = listOf(1,3,3,5,0,9).makeAscending()
    val a2 = listOf<Int>().makeAscending()
    val a3 = listOf(1).makeAscending()
    val a4 = listOf(1,1).makeAscending()
    val a5 = listOf(10,6,8).makeAscending()
    val s1 = "abcdefghijklmnopqrstuvwyxz"
    val p1 = s1.splitAt(listOf(3,6,10,26,10))
    val p2 = s1.splitAt(listOf())
    val p3 = s1.splitAt(listOf(1))
    val p4 = s1.splitAt(listOf(0,3,3))
    val q0 = wrap("abcdefg-hijkl", 8, true)

*/
}
