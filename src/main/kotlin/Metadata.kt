class Metadata (
) {
    val classes: Map<String, ClassMetadata> =
        Rest.get("rest/top/metadata/", mapOf("level" to "full"))
            ?.get("metadata")
            ?.get("collection")
            ?.asArray()
            ?.map{
                Pair(it["name"]!!.asString(),
                    ClassMetadata(it["name"]!!.asString(), it))
            }
            ?.toMap() ?: mapOf()

    fun getClass(className: String) = classes[className]
    fun getAttribute(className: String, aname: String) = getClass(className)?.getAttribute(aname)

    companion object {
        private var theMetadata: Metadata? = null
        fun load() = Metadata().also { theMetadata = it }
        fun getClass(className: String) = theMetadata!!.getClass(className)
        fun getAttribute(className: String, aname: String) = theMetadata!!.getAttribute(className, aname)
        fun getConfigMd() = getClass("configuration")!!
        fun getPolicyManagerMd() = getClass("policy_manager")!!
    }
}