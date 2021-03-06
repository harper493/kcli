class Metadata (
) {
    val classMap: Map<String, ClassMetadata> = Rest.get("rest/top/metadata/",
                                                         mapOf("level" to "full"))
            ?.get("metadata")
            ?.get("collection")
            ?.asArray()
            ?.map{
                val name = it["name"]!!.asString()
                Pair(name, ClassMetadata(name, it))
            }
            ?.toMap() ?: mapOf()

    init {
        theMetadata = this
        getPolicyManagerMd().setContainer(null)
        classes.mapWhile{it.completeClassData()}
        classes.map{it.finalizeClassData()}
    }

    val classes get() = classMap.values
    fun getClass(className: String) = classMap[className]
    fun getAttribute(className: String, aname: String) = getClass(className)?.getAttribute(aname)

    companion object {
        private var theMetadata: Metadata? = null
        val classes get() = theMetadata!!.classes
        fun load() = Metadata().also { theMetadata = it }
        fun getClass(className: String) = theMetadata!!.getClass(className)
        fun getAttribute(className: String, aname: String) = theMetadata!!.getAttribute(className, aname)
        fun getConfigMd() = getClass("configuration")!!
        fun getPolicyManagerMd() = getClass("policy_manager")!!
    }
}