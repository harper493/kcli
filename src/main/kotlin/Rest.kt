import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.result.Result


enum class HttpStatus( val status: Int, val text: String) {
    success(201, "success"),
    forbidden(401, "forbidden"),
    notFound(404, "not found"),
    timeout(-1, "request timed out");

    operator fun invoke(s: Int) = s==status
}

class RestException (
    val status : Int,
    val text : String
) : Exception(text) {
    override fun toString() = "$status $text"
}

class Rest(
    private val server: Server,
    private var config: String = "running",
    private var trace: Boolean = false
) {
    fun setTrace(t: Boolean) { trace = t }

    fun setConfig(newConfig: String) { config = newConfig }

    fun getRaw(url: String, options: Map<String,String>?=null): String {
        val u = makeUrl(url, options)
        if (trace) {
            println("GET $u")
        }
        val (request, response, result) = Fuel.get(u)
            .authentication().basic(server.username, server.password)
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
                    return String(result.get())
                } catch (exc: JsonException) {
                    throw RestException(999, "error in Json text: $exc")
                }
            }
        }

    }

    fun getJson(url: String, options: Map<String,String>?=null) : JsonObject {
        try {
            return JsonObject.load(getRaw(url, options))
        } catch (exc: JsonException) {
            throw RestException(999, "error in Json text: $exc")
        }
    }

    fun get(oname: ObjectName, options: Map<String,String>?=null):
            Pair<Map<String,String>,CollectionData> =
        getJson(oname.url, options).let{Pair(it.toMap(), makeCollection(oname, it))}

    fun getCollection(oname: ObjectName, options: Map<String,String>?=null) =
        makeCollection(oname, getJson(oname.url, options))

    private fun makeCollection(oname: ObjectName, json: JsonObject): CollectionData =
        CollectionData(oname.leafClass!!).load(json["collection"] ?: JsonObject.make())

    fun getObject(url: String, options: Map<String,String>?=null) =
        try {
            getCollection(url, options).first()
        } catch(exc: RestException) {
            if (HttpStatus.notFound(exc.status)) {
                null
            } else {
                throw (exc)
            }
        }

    fun getObject(oname: ObjectName, options: Map<String,String>?=null) =
        getObject(oname.url, options)

    fun getAttribute(oname: ObjectName, aname: String, options: Map<String,String>?=null) =
        getObject(oname,
            if (options==null) mapOf("select" to aname) else options + ("select" to aname))
            ?.get(aname)?.value

    fun getTotals(oname: ObjectName, options: Map<String,String>?=null) =
        getJson(oname.url, options).let{ it.asDict()?.get("total")?.toMap() ?: mapOf() }

    fun getSystemName() =
        getJson("rest/top/", mapOf("link" to "full"))["collection"]
            ?.asArray()
            ?.get(0)
            ?.asDict()
            ?.get("link")
            ?.asDict()
            ?.get("href")
            ?.asString()
            ?.split("/")
            ?.get(2)

    fun put(url: String, body: Map<String,String>, options: Map<String,String> = mapOf()): JsonObject {
        val u = makeUrl(url, options)
        if (trace) {
            println("PUT $u ${body.toJson()}")
        }
        val (_, response, result) = Fuel.put(u)
            .jsonBody(body.toJson())
            .authentication().basic(server.username, server.password)
            .response()
        when (result) {
            is Result.Failure -> {
                val rx = Regex(".*Body.*:.*?\"message\".*?:.*?\"(.*?)\".*")
                val resp = response.toString().replace("\n", " ")
                val m = rx.find(resp)
                val msg = if (m != null) m.groupValues[1] else response.responseMessage
                throw RestException(response.statusCode, msg)
            }
            is Result.Success -> try {
                return JsonObject.load(String(result.get()))
            } catch (exc: JsonException) {
                throw RestException(999, "error in Json text: $exc")
            }
        }
    }

    fun post(url: String, body: Map<String,String>): JsonObject {
        val u = makeUrl(url)
        if (trace) {
            println("POST $u ${body.toJson()}")
        }
        val (_, response, result) = Fuel.post(u)
            .jsonBody(body.toJson())
            .authentication().basic(server.username, server.password)
            .response()
        when (result) {
            is Result.Failure -> {
                val rx = Regex(".*Body.*:.*?\"message\".*?:.*?\"(.*?)\".*")
                val resp = response.toString().replace("\n", " ")
                val m = rx.find(resp)
                val msg = if (m != null) m.groupValues[1] else response.responseMessage
                throw RestException(response.statusCode, msg)
            }
            is Result.Success -> try {
                return JsonObject.load(String(result.get()))
            } catch (exc: JsonException) {
                throw RestException(999, "error in Json text: $exc")
            }
        }
    }

    fun delete(url: String): JsonObject {
        val u = makeUrl(url)
        if (trace) {
            println("DELETE $u")
        }
        val (_, response, result) = Fuel.delete(u)
            .authentication().basic(server.username, server.password)
            .response()
        when (result) {
            is Result.Failure -> {
                val rx = Regex(".*Body.*:.*?\"message\".*?:.*?\"(.*?)\".*")
                val resp = response.toString().replace("\n", " ")
                val m = rx.find(resp)
                val msg = if (m != null) m.groupValues[1] else response.responseMessage
                throw RestException(response.statusCode, msg)
            }
            is Result.Success -> try {
                return JsonObject.load(String(result.get()))
            } catch (exc: JsonException) {
                throw RestException(999, "error in Json text: $exc")
            }
        }
    }

    fun setPassword(p: String) { server.password = p }

    private fun makeUrl(url: String, options: Map<String,String>?=null) : String {
        val trueUrl = when (url.split("/")[0]) {
            "rest"            -> url
            "files"           -> url
            "configurations"  -> "rest/top/$url"
            else              -> "rest/top/configurations/$config/$url"
        }
        val p = if (server.port!=0) ":${server.port}" else ""
        val opts = if (options!=null) "?" + options.keys.joinToString("&")
                    { "${it}=${options[it]}"} else ""
        return "http://${server.host}${p}/${trueUrl}${opts}"
    }
    companion object {
        private lateinit var theRest: Rest
        fun connect(
            server: Server,
            config: String = "running",
            trace: Boolean = false) = Rest(server=server, config=config, trace=trace)
                                      .also{ theRest = it }

        fun getRaw(url: String, options: Map<String,String>?=null) =
            theRest.getRaw(url, options)
        fun getJson(url: String, options: Map<String,String>?=null) =
            theRest.getJson(url, options)
        fun put(url: String, body: Map<String,String>, options: Map<String,String> = mapOf()) =
            theRest.put(url, body, options)
        fun post(url: String, body: Map<String,String>) = theRest.post(url, body)
        fun delete(url: String) = theRest.delete(url)
        fun get(oname: ObjectName, options: Map<String,String>?=null) =
            theRest.get(oname, options)
        fun getCollection(url: String, options: Map<String,String>?=null) =
            theRest.getCollection(ObjectName(url), options)
        fun getCollection(oname: ObjectName, options: Map<String,String>?=null) =
            theRest.getCollection(oname, options)
        fun getObject(url: String, options: Map<String,String>?=null) =
            theRest.getObject(ObjectName(url), options)
        fun getAttribute(oname: ObjectName, aname: String, options: Map<String,String>?=null) =
            theRest.getAttribute(oname, aname, options)
        fun getAttribute(url: String, aname: String, options: Map<String,String>?=null) =
            theRest.getAttribute(ObjectName(url), aname, options)
        fun getTotals(oname: ObjectName, options: Map<String,String>?=null) =
            theRest.getTotals(oname, options)
        fun setTrace(t: Boolean) =
            theRest.setTrace(t)
        fun setConfig(newConfig: String) =
            theRest.setConfig(newConfig)
        fun getSystemName() =
            theRest.getSystemName()
        fun setPassword(p: String) =
            theRest.setPassword(p)
    }
}