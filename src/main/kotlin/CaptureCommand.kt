fun CliCommand.doCapture() {
    val k = parser.findKeyword(KeywordList("start", "stop"))!!
    println(k.asString())
    when (k.asString()) {
        "start" -> doCaptureStart()
        else -> doCaptureStop()
    }
}

fun CliCommand.doCaptureStart() {
    val classMd = Metadata["packet_captor"]!!
    val attrMd = Metadata.getAttribute("configuration", "packet_captors")!!
    val namedObjectAttrs = Metadata["named_object_base"]!!.settableAttributes.map { it.name }
    val values = readAttributes(classMd,
                                exists=false,
                                exclude = { it !in namedObjectAttrs && it !in listOf("capture")})
    val captorName = values["name"] ?: "default"
    val captorObjName = ObjectName().append(attrMd, captorName)
    val captorObj = Rest.getObject(captorObjName.url, mapOf("select" to "capture"))
    CliException.throwIf("packet captor '$captorName' already in use")
        { captorObj?.getValue("capture") ?: "F" == "T"}
    values["capture"] = "T"
    Rest.put(captorObjName.url, values)
    val filename = Rest.getAttribute(captorObjName, "filename")
    if (filename!=null) {
        Cli.outputln("Packets being captured to file '$filename'")
    } else {
        Cli.outputError("No interfaces enabled, packet capture failed")
    }
}

fun CliCommand.doCaptureStop() {
    val attrMd = Metadata.getAttribute("configuration", "packet_captors")!!
    val captorObjName = ObjectName().append(attrMd, "")
    val captorName = parser.nextToken(completer=ObjectCompleter(captorObjName), endOk=true) ?: "default"
    captorObjName.addLeafName(captorName)
    CliException.throwIf("No capture in progress for '$captorName'")
        { Rest.getAttribute(captorObjName, "capture")=="F"}
    Rest.put(captorObjName.url, mapOf("capture" to "F"))
    val captorObj = Rest.getObject(captorObjName.url, mapOf("select" to "packets_captured,filename"))
    if (captorObj!=null) {
        Cli.outputln("${captorObj.getInt("packets_captured")} " +
                "packets captured to '${captorObj.getValue("filename")}'")
    }
}
