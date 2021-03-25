class UrlException(why: String) : Exception(why)

class ObjectName(val newUrl: String="") {
    class Element(
        val attrMd: AttributeMetadata,
        var name: String
    ) {
        val urlName get() = if(name.isEmpty()) "*" else name
        val url: String get() = "${attrMd.relativeUrl}${urlName}"
        val isWild get() = name.isEmpty()
        init {
            if (name=="*") name = ""
        }
    }
    private val elements: MutableList<Element> = mutableListOf()
    val url get() = "rest/top/" + elements.map(Element::url).joinToString("/").dropLastWhile{ it=='*' }
    val leafClass get() = elements.lastOrNull()?.attrMd?.containedClass
        ?: Metadata.getClass("policy_manager")
    val leafAttribute get() = elements.lastOrNull()?.attrMd
    val leafName get() = elements.lastOrNull()?.name ?: ""
    val isWild get() = elements.fold(false) { acc, e -> acc || e.isWild }
    val isEmpty get() = elements.isEmpty()

    init {
        if (newUrl.isNotEmpty()) parse(newUrl)
    }

    fun append(attrMd: AttributeMetadata, name: String): ObjectName {
        if (isEmpty && attrMd.myClass != Metadata.getPolicyManagerMd()) {
            elements.add(Element(Metadata.getAttribute("policy_manager", "configurations")!!,
                "running"))
        }
        elements.add(Element(attrMd, name))
        return this
    }

    fun copy() = ObjectName().also{ oname -> elements.map{oname.elements.append(it) } }

    fun dropLast(n: Int=1) =
        ObjectName()
            .also{ newOn -> elements
                .filterIndexed{ i, _ -> i < elements.size-n }
                .map { newOn.append(it.attrMd, it.name)}}

    fun wipeLeafName() =
        if (isEmpty) ObjectName() else dropLast().append(leafAttribute!!, "")

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
        val attributes = split.filterIndexed{ index, _ -> index % 2 == 0 }
        val names = split.filterIndexed{ index, _ -> index % 2 == 1 }
        elements.clear()
        var curMd = Metadata.getPolicyManagerMd()
        for ((a,n) in attributes.zip(names)) {
            val collMd = curMd.getAttribute(a)
            if (collMd?.containedClass == null) {
                throw UrlException("no collection '$a' in class '${curMd.name}")
            }
            elements.add(Element(curMd.getAttribute(a)!!, n))
            curMd = collMd.containedClass!!
        }
    }
}