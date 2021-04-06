import java.io.File as File

object ResourceCache {
    var currentEtag: String? = null
    val etagFile = "${Cli.kcliDir}/etag"
    val resourceDir = "${Cli.kcliDir}/resources"
    const val etagPrefix = "etag_"

    private fun validateEtag() {
        val sysEtag = currentEtag ?: Rest.getJson("rest/top", mapOf("level" to "full"))
            .asDict()["collection"]?.asArray()?.get(0)?.asDict()!!["etag"]?.asString()
            .also { currentEtag = it }
        val etags = File(resourceDir).list()
            ?.filter { it.startsWith(etagPrefix) }
        etags?.filter { it != "$etagPrefix$sysEtag" }
            ?.forEach {
                ignoreException { File("$resourceDir/$it").deleteRecursively() }
            }
        File("$resourceDir/$etagPrefix$currentEtag").mkdirs()
    }

    private fun doGet(resourceFile: String, getter: () -> String) =
        ignoreException { readFile(resourceFile) } ?: (getter()
            .also { content ->
                ignoreException { File(resourceDir).mkdirs() }
                ignoreException { content.writeToFile(resourceFile) }
            })

    fun get(name: String, getter: () -> String): String {
        validateEtag()
        return doGet("$resourceDir/$etagPrefix$currentEtag/$name", getter)
    }

    fun getStable(name: String, getter: () -> String) =
        doGet("$resourceDir/$name", getter)
}

