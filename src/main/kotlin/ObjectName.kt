class UrlException(why: String) : Exception(why)

class ObjectName(val newUrl: String="") {
    class Element(
        val attrMd: AttributeMetadata,
        var name: String
    ) {
        val urlName get() = if(name.isEmpty()) "*" else name
        val url: String get() = "${attrMd.relativeUrl}${urlName}"
        val isWild get() = name.isEmpty() || name=="*"
        val isQuiteWild get() = '*' in name && name != "*"
        fun copy() = Element(attrMd, name)
        init {
            if (name=="*") name = ""
        }
    }
    val elements: MutableList<Element> = mutableListOf()
    val url get() = "rest/top/" + elements.map(Element::url).joinToString("/").dropLastWhile{ it=='*' }
    val leafClass get() = elements.lastOrNull()?.attrMd?.containedClass
        ?: Metadata.getClass("policy_manager")
    val leafAttribute get() = elements.lastOrNull()?.attrMd
    val leafName get() = elements.lastOrNull()?.name ?: ""
    val isWild get() = elements.fold(false) { acc, e -> acc || e.isQuiteWild || e.isWild }
    val wildDepth get() = elements.fold(0) { result, elem -> result + elem.isWild.ifElse(1, 0) }
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

    fun addLeafName(name: String): ObjectName =
        also { elements.lastOrNull()?.name = (name) }

    fun copy() = ObjectName().also{ oname -> elements.map{oname.elements.add(it.copy()) } }

    fun dropLast(n: Int=1) =
        ObjectName()
            .also{ newOn -> elements
                .filterIndexed{ i, _ -> i < elements.size-n }
                .map { newOn.append(it.attrMd, it.name)}}

    fun wipeLeafName() =
        if (isEmpty) ObjectName() else dropLast().append(leafAttribute!!, "")

    fun convertWild() =
        elements.mapNotNull{ if (it.isQuiteWild)
            it.let { e -> val n=e.name
                e.name="*"
                "${e.attrMd.typeName}.name=${n}"}
        else null
        }

    fun parse(url: String) {
        val split = url.split("/").toMutableList()
        if (split[0].isEmpty()) {
            split.removeFirst()
        }
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