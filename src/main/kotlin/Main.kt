fun main(args: Array<String>) {
    Datatype.load()
    Properties.load("/etc/kcli/objects.properties")
              .load("/etc/kcli/cli.properties")
    Rest.connect(server="192.168.1.70", user="kcli", password="KcliPw#1", trace=true)
    Metadata.load()
    while (true) {
        print("kcli# ")
        val command = "show flows" //readLine() ?: ""
        try {
            println(command)
            Cli().oneLine(command)
            break //@@@
        } catch (exc: CliExitException) {
            break
        }
    }
}
