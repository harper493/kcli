data class Server (
    var host: String = "",
    var port: Int = 5000,
    var username: String = "",
    var password: String = "",
) {
    private val defaultPort = 5000
    constructor(text: String) : this() {
        val rx = Regex("""^(?:([^:@]+)(?::([^@]+))?@)?(.*)$""")
        val m = rx.find(text)
        if (m!=null) {
            val (u, p, s) = m.groupValues.let { Triple(it[1], it[2], it[3]) }
            this.username = u
            this.password = p
            when (s.filter { it == ':' }.count()) {
                0 -> { this.host = s; this.port = defaultPort }
                1 -> s.split(":").let { this.host = it[0]; this.port = it[1].toIntOrNull() ?: -1 }
                2 -> s.split("@").let { this.host = it[0]; this.port = it[1].toIntOrNull() ?: -1 }
            }
        }
        if (this.port < 0) {
            throw Exception("Invalid server string '${text}'")
        }
    }
    private fun strIf(text: String, pred: String) = if (pred.isEmpty()) "" else text
    override fun toString() = "$username${strIf(":", password)}$password" +
            "${strIf("@", username)}$host:$port"
    fun saveAs(name: String) =
        also {
            servers[name] = this
            saveAll()
        }

    companion object {
        private val servers = mutableMapOf<String,Server>()
        private val filename get() = "${Cli.kcliDir}/targets"
        private const val lastName = "_last"

        fun saveAll() {
            servers.map{ "${it.key} = ${it.value}" }
                .joinToString("\n")
                .writeToFile(filename)
        }

        fun restore() {
            (ignoreException{ readFile(filename) } ?: "")
                .split("\n")
                .filter{ it.isNotBlank() }
                .map{ Regex("""\s*(\S+)\s*=\s*(\S+)\s*""")
                    .matchEntire(it)
                    ?.groupValues }
                .forEach{ servers[it!![1]] = Server(it[2])}
        }

        fun make(target: String): Server? =
            when {
                target.containsAnyOf(":.@") ->
                    Server(target).saveAs(lastName)
                target.isBlank() ->
                    servers[lastName]
                else ->
                    servers[target]
            }

        operator fun get(name: String) =
            servers[name]
    }
}