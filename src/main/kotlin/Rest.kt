import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.result.Result


enum class HttpStatus( val status: Int, val text: String) {
    success(201, "success"),
    notFound(404, "not found");

    operator fun invoke(s: Int) = s==status
}

class RestException (
    val status : Int,
    val text : String
) : Exception(text) {
    override fun toString() = "$status $text"
}

data class ServerInfo(
    var server: String = "",
    var port: Int = 5000,
    var username: String = "",
    var password: String = "",
) {
    val defaultPort = 5000
    constructor(text: String) : this() {
        val rx = Regex("""^(?:([^:@]+)(?::([^@]+))?@)?(.*)$""")
        val m = rx.find(text)
        if (m!=null) {
            val (u, p, s) = m.groupValues.let { Triple(it[1], it[2], it[3]) }
            this.username = u
            this.password = p
            val colons = s.filter { it == ':' }.count()
            when (colons) {
                0 -> { this.server = s; this.port = defaultPort }
                1 -> s.split(":").let { this.server = it[0]; this.port = it[1].toIntOrNull() ?: -1 }
                2 -> s.split("@").let { this.server = it[0]; this.port = it[1].toIntOrNull() ?: -1 }
            }
        }
        if (this.port < 0) {
            throw Exception("Invalid server string '${text}'")
        }
    }
    private fun strIf(text: String, pred: String) = if (pred.isEmpty()) "" else text
    override fun toString() = "$username${strIf(":", password)}$password${strIf("@", username)}$server:${port.toString()}"
}

class Rest(
    var server: String = "localhost",
    val config: String = "running",
    val trace: Boolean = false
) {
    val serverInfo = ServerInfo(server)

    fun getRaw(url: String, options: Map<String,String>?=null) : JsonObject {
        val u = makeUrl(url, options)
        if (trace) {
            println(u)
        }
        val (request, response, result) = Fuel.get(u)
                    .authentication().basic(serverInfo.username, serverInfo.password)
                    .response()
        when (result) {
            is Result.Failure -> {
                val rx = Regex(".*Body.*:.*?\"message\".*?:.*?\"(.*?)\".*")
                val resp = response.toString().replace("\n", " ")
                val m = rx.find(resp)
                val msg = if (m != null) m.groupValues[1] else response.responseMessage
                throw RestException(response.statusCode, msg)
            }
            is Result.Success -> {
                try {
                    return JsonObject.load(String(result.get()))
                } catch (exc: JsonException) {
                    throw RestException(999, "error in Json text: $exc")
                }
            }
        }
    }

    fun getCollection(url: String, options: Map<String,String>?=null) : JsonObject? {
        return getRaw(url, options)["collection"]
    }

    fun getCollection(oname: ObjectName, options: Map<String,String>?=null) : CollectionData {
        val response = getRaw(oname.url, options)
        val result = CollectionData(oname.leafClass!!)
            .load(response["collection"] ?: JsonObject.make())
        return result
    }

    fun getObject(url: String, options: Map<String,String>?=null) =
        getCollection(url, options)?.get(0)

    fun getObject(oname: ObjectName, options: Map<String,String>?=null) =
        getCollection(oname, options).first()

    fun put(url: String, body: String) {
        val (_, response, result) = Fuel.put(makeUrl(url))
            .jsonBody(body)
            .authentication().basic(serverInfo.username, serverInfo.password)
            .response()
        when (result) {
            is Result.Failure -> {
                val rx = Regex(".*Body.*:.*?\"message\".*?:.*?\"(.*?)\".*")
                val resp = response.toString().replace("\n", " ")
                val m = rx.find(resp)
                val msg = if (m != null) m.groupValues[1] else response.responseMessage
                throw RestException(response.statusCode, msg)
            }
            else -> return
        }
    }

    private fun makeUrl(url: String, options: Map<String,String>?=null) : String {
        val elements = url.split("/").toMutableList()
        if (elements[0]!="rest") {
            if (elements[0]!="configurations") {
                elements.add(0, "configurations")
                elements.add(1, config)
            }
            elements.add(0, "rest")
            elements.add(1, "top")
        }
        val p = if (serverInfo.port!=0) ":${serverInfo.port}" else ""
        val e = elements.joinToString("/")
        val opts = if (options!=null) "?" + options.keys.joinToString("&")
                    { "${it}=${options[it]}"} else ""
        return "http://${serverInfo.server}${p}/${e}${opts}"
    }
    constructor(serverInfo: ServerInfo): this() {
        server = serverInfo.toString()
    }
    companion object {
        private var theRest: Rest? = null
        fun connect(
            server: String = "localhost:5000",
            config: String = "running",
            trace: Boolean = false) = Rest(server=server, config=config, trace=trace)
                                      .also{ theRest = it }

        fun getRaw(url: String, options: Map<String,String>?=null) =
            theRest!!.getRaw(url, options)
        fun put(url: String, body: String) = theRest?.put(url, body)
        fun getCollection(url: String, options: Map<String,String>?=null) =
            theRest!!.getCollection(url, options)
        fun getCollection(oname: ObjectName, options: Map<String,String>?=null) =
            theRest!!.getCollection(oname, options)
        fun getObject(url: String, options: Map<String,String>?=null) =
            theRest!!.getObject(url, options)
    }
}