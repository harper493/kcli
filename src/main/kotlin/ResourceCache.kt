object ResourceCache {
    var currentEtag: String? = null
    val etagFile = "${Cli.kcliDir}/etag"
    val resourceDir = "${Cli.kcliDir}/resources"

    private fun etagGood(): Boolean {
        val sysEtag = currentEtag ?:
            Rest.getJson("rest/top", mapOf("level" to "full"))
                .asDict()["collection"]?.asArray()?.get(0)?.asDict()!!["etag"]?.asString()
                .also{ currentEtag = it }
        val curEtag = ifException("") { java.io.File(etagFile).readText().trim() }
        return (curEtag==sysEtag).also {
            if (!it) sysEtag!!.writeToFile(etagFile)
        }
    }

    fun get(name: String, getter: () -> String): String {
        val resourceFile = "$resourceDir/$name"
        val cached = ignoreException { java.io.File(resourceFile).readText() }
        return if (etagGood() && cached != null) {
            cached
        } else {
            getter().also { content ->
                ignoreException {
                    java.io.File(resourceDir).mkdirs()
                    content.writeToFile(resourceFile)
                }
            }
        }
    }
}