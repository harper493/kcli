class Cli(val args: Array<String>) {

}

fun main(args: Array<String>) {
    Datatype.load()
    Properties.load("/etc/kcli/objects.properties")
        .load("/etc/kcli/cli.properties")
    Rest.connect(server = "kcli:KcliPw#1@192.168.1.70", trace = false)
    Metadata.load()
    StyledText.setRenderer("ISO6429")
    if (true) {
        val commandReader = CommandReader("stm# ")
        while (true) {
            var error = ""
            try {
                CliCommand(commandReader.read())
            } catch (exc: CliException) {
                if (exc.message ?: "" != "") {
                    error = exc.message ?: ""
                } else {
                    break
                }
            } catch (exc: RestException) {
                error = exc.message ?: ""
            }
            if (error.isNotEmpty()) {
                println(StyledText(error, color=Properties.get("color", "error")).render())
            }
            print(StyledText("").render())
        }
    } else {
        val command = "show flow with port=443 top 10 by byte_count select rtt_s"
        //val command = "application youtube no desc priority 3021"
        try {
            println(command)
            CliCommand(command)
        } catch (exc: CliException) {
            if (exc.message ?: "" != "") {
                println(exc.message)
            }
        }
    }
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