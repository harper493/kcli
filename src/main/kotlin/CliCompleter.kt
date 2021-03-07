open class CliCompleter {
    open fun complete(line: String, token: String): List<String> {
        return listOf()
    }
}

class KeywordCompleter(
    private val keywords: KeywordList
): CliCompleter() {
    override fun complete(line: String, token: String): List<String> =
        keywords.toStrings().filter{ it.startsWith(token) }.map{ "$line$it" }
}

class ObjectCompleter(
    private val objName: ObjectName,
    private val extras: KeywordList
): CliCompleter() {
    override fun complete(line: String, token: String): List<String> {
        return (try {
            val envelope = Rest.get(
                objName.wipeLeafName().url,
                options = mapOf("completion" to token, "limit" to "0")
            )?.asDict()
            if (envelope?.get("size")?.asInt() ?: 0 ==1)
                envelope?.get("completion")?.asString()
                    ?.let{ listOf(it) }
                    ?.append(extras.keywords.map{it.key}.filter{it.startsWith(token)})
            else listOf()
        } catch (exc: RestException) { null })
            ?.map{ "$line$it" } ?: listOf()
    }
}

/*
    def complete(self, text) :
        while True :
            options = { 'completion' : text,
                        'limit' : str(0),
                    }
            obj = self.obj_name.copy().wipe_leaf_name()
            try :
                envelope = saisei_api.get_rest(). \
                           get_collection_envelope(obj, options=options)
            except NotFoundException :
                return []
            try :
                collection = [ envelope['completion'] ]
            except KeyError :
                collection = []
            try :
                sz = int(envelope['size'])
            except :
                sz = 2
            if sz > 1 :
                collection.append('') # hack to show completion is not unique
            return collection + [n for n in self.extra.keys() if n.startswith(text)]


 */