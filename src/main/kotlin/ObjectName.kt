class UrlException(why: String) : Exception(why)

class ObjectName {
    class Element(
        val attrMd: AttributeMetadata,
        var name: String
    ) {
        val urlName get() = if(name.isEmpty()) "*" else name
        val url: String get() = "${attrMd.relativeUrl}${urlName}"
        val isWild get() = name.isEmpty()
        init { if (name=="*") name = "" }
    }
    private val elements: MutableList<Element> = mutableListOf()
    val url get() = "rest/top/" + elements.map(Element::url).joinToString("/").dropLastWhile{ it=='*' }
    val leafClass get() = elements.lastOrNull()?.attrMd?.getContainedClass()
    val leafName get() = elements.lastOrNull()?.name ?: ""
    val isWild get() = elements.fold(false) { acc, e -> acc || e.isWild }
    val isEmpty get() = elements.isEmpty()

    fun append(attrMd: AttributeMetadata, name: String) {
        if (isEmpty && attrMd.myClass != Metadata.getPolicyManagerMd()) {
            elements.add(Element(Metadata.getAttribute("policy_manager", "configurations")!!,
                "running"))
        }
        elements.add(Element(attrMd, name))
    }

    fun parse(url: String) {
        val split = url.split("/").toMutableList()
        if (split.size %2 != 0) {
            split += ""
        }
        if (split[0]=="rest") {
            split.removeFirst()
            split.removeFirst()
        } else if (split[0]!="configurations") {
            split.addAll(0, listOf("configurations", "running"))
        }
        val attributes = split.filterIndexed{ index, _ -> index % 2 == 1 }
        val names = split.filterIndexed{ index, _ -> index % 2 == 0 }
        elements.clear()
        var curMd = Metadata.getPolicyManagerMd()
        for ((a,n) in attributes.zip(names)) {
            val collMd = curMd.getAttribute(a)
            if (collMd==null || collMd.getContainedClass()==null) {
                throw UrlException("no collection '$a' in class '${curMd.name}")
            }
            elements.add(Element(Metadata.getAttribute("configuration", a)!!, n))
            curMd = collMd.getContainedClass()!!
        }

    }
}