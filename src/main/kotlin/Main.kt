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
    Properties.load("/etc/kcli/objects.properties")
              .load("/etc/kcli/cli.properties")
    val rest = Rest(server="192.168.1.70", user="kcli", password="FuckYou1!", trace=true)
    Metadata.load(rest)
    while (true) {
        print("kcli# ")
        var command = "show interfaces" //readLine() ?: ""
        try {
            println(command)
            Cli(rest).oneLine(command)
            break //@@@
        } catch (exc: CliExitException) {
            break
        }
    }
}
