data class Server (
    var host: String = "",
    var port: Int = 5000,
    var username: String = "",
    var password: String = "",
) {
    private val defaultPort = 5000
    constructor(text: String) : this() {
        val rx = Regex("""^(?:([^:@]+)(?::([^@]+))?@)?(.*)$""")
        port = -1
        val m = rx.find(text)
        if (m!=null) {
            val (u, p, s) = m.groupValues.let { Triple(it[1], it[2], it[3]) }
            this.username = u
            this.password = p
            when (s.filter { it == ':' }.count()) {
                0 -> { this.host = s; this.port = defaultPort }
                1 -> s.split(":")
                    .let { this.host = it[0]; this.port = it[1].toIntOrNull() ?: -1 }
                2 -> s.split("@")
                    .let { this.host = it[0]; this.port = it[1].toIntOrNull() ?: -1 }
            }
        }
        CliException.throwIf("Invalid server string '${text}'"){ port<0 }
    }
    override fun toString() =
        (if (username.isNotEmpty()) "$username@" else "") + host +
        (if (port!=defaultPort) ":$port" else "")

    fun toFullString() =
        (if (username.isNotEmpty()) "$username" +
                (if (password.isNotEmpty()) ":$password" else "") + "@" else "") +
                host +
                (if (port!=defaultPort) ":$port" else "")

    fun saveAs(name: String) =
        also {
            servers[name] = this
            saveAll()
        }

    companion object {
        private val servers = mutableMapOf<String,Server>()
        private val filename get() = "${Cli.kcliDir}/targets"
        const val lastName = "_last"

        fun saveAll() {
            servers.map{ "${it.key} = ${it.value.toFullString()}" }
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

        fun remove(name: String) {
            ignoreException { servers.remove(name) }
            saveAll()
        }

        fun getLast() = get(lastName)

        operator fun iterator() = servers.iterator()

        fun isEmpty() = servers.isEmpty()
    }
}