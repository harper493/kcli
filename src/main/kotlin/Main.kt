//import khttp.get
//import khttp.structures.authorization.Authorization
//import khttp.structures.authorization.BasicAuthorization

import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication

import JsonObject
import Rest
import Properties
import Datatype

fun fn(s: String) {
    println("Hello $s!")
}

fun main(args: Array<String>) {
    Datatype.load()
    Properties.load("/home/john/stm/files/props/objects.properties")
    val rest = Rest(server="192.168.1.70", user="kcli", password="FuckYou1!", trace=true)
    Metadata.load(rest)
    val a = Metadata.getAttribute("interface", "bytes_received")
    println("${a?.name}: ${a?.displayName} ${a?.typeName}")
}


/*
fun main(args: Array<String>) {
    val js = "[[\"foo\",\"bah\",],[\"baz\"],\"whizz\"]"
    val j = JsonObject.load(js)
    val js2 =
        """
            {"k1": "v1",
             "k2":  "v2",
             "k3": [ "a1", "a2\"\\", +999e3, -42, true, false, null,
              { "k44" : "v44" }]
             }
        """
    val j2 = JsonObject.load(js2)
    println(j2.toString())
//    val a = BasicAuthorization("admin", "korp")
    var username = "admin"
    var password = "FlowCommand#1"
    val url = "http://192.168.1.70:5000/rest/top/configurations/running/applications/youtube?link=name"
    var (_, _, result) = Fuel.get(url).authentication().basic(username, password).responseString()
    when (result) {
        is Result.Failure -> {
            val ex = result.getException()
            println(ex)
        }
        is Result.Success -> {
            val data = result.get()
            println(data)
            val j3 = JsonObject.load(data)
            println(j3.toString())
            println(j3.getDict()!!["timestamp"]?.getString())
            println(j3["timestamp"])
            println(j3["collection"]?.get(0)?.get("name"))
        }
    }
//    val free = get(url+url2, auth=a)
//    println(free.text)
}
*/
