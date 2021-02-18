class Metadata (
    do_load: Boolean = true
) {
    private var classes: MutableMap<String, ClassMetadata> = mutableMapOf()

    fun load() {
        val json = Rest.get("rest/top/metadata/", mapOf("level" to "full"))
        val classMd = json?.get("metadata")?.get("collection")
        if (classMd != null) {
            for (c in classMd.asArray()) {
                val className = c["name"]?.asString() ?: ""
                classes[className] = ClassMetadata(className, c)
            }
        }
    }

    fun getClass(className: String) = classes[className]
    fun getAttribute(className: String, aname: String) = getClass(className)?.getAttribute(aname)

    init {
        if (do_load) {
            load()
        }
    }
    companion object {
        private var theMetadata: Metadata? = null
        fun load() = Metadata().also { theMetadata = it }
        fun getClass(className: String) = theMetadata!!.getClass(className)
        fun getAttribute(className: String, aname: String) = theMetadata!!.getAttribute(className, aname)
        fun getConfigMd() = getClass("configuration")!!
        fun getPolicyManagerMd() = getClass("policy_manager")!!
    }
}