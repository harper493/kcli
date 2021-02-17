import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.result.Result

class RestException (
    val status : Int,
    val text : String
) : Exception(text) {
    override fun toString() = "$status $text"
}

class Rest(
    val server: String = "localhost",
    val port: Int = 5000,
    val user:String = "",
    val password: String = "",
    val config: String = "running",
    val trace: Boolean = false
) {
    fun get(url: String, options: Map<String,String>?=null) : JsonObject {
        val u = _makeUrl(url, options)
        if (trace) {
            println(u)
        }
        val (request, response, result) = Fuel.get(u)
                    .authentication().basic(user, password)
                    .response()
        when (result) {
            is Result.Failure -> {   // seems crazy but this is the only way to get the detailed message
                val rx = Regex(".*Body.*:.*?\"message\".*?:.*?\"(.*?)\".*")
                val resp = response.toString().replace("\n", " ")
                val m = rx.find(resp)
                val msg = if (m!=null) m.groupValues[1] else response.responseMessage
                throw RestException(response.statusCode, msg.toString())
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
        return get(url, options)["collection"]
    }

    fun getObject(url: String, options: Map<String,String>?=null) : JsonObject? {
        return getCollection(url, options)?.get(0)
    }

    private fun _makeUrl(url: String, options: Map<String,String>?=null) : String {
        val elements = url.split("/").toMutableList()
        if (elements[0]!="rest") {
            if (elements[0]!="configurations") {
                elements.add(0, "configurations")
                elements.add(1, config)
            }
            elements.add(0, "rest")
            elements.add(1, "top")
        }
        val p = if (port!=0) ":${port}" else ""
        val e = elements.joinToString("/")
        val opts = if (options!=null) "?" + options?.keys.joinToString("&")
                    { "${it}=${options!![it]}"} else ""
        return "http://${server}${p}/${e}${opts}"
    }
}