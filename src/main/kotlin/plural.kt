val pluralIrregulars = mapOf(
    "ox" to "oxen",
    "vax" to "vaxen",
    "roof" to "roofs",
    "turf" to "turfs",
    "sheep" to "sheep",
    "salmon" to "salmon",
    "trout" to "trout",
    "child" to "children",
    "person" to "people",
    "louse" to "lice",
    "foot" to "feet",
    "mouse" to "mice",
    "goose" to "geese",
    "tooth" to "teeth",
    "aircraft" to "aircraft",
    "hovercraft" to "hovercraft",
    "potato" to "potatoes",
    "tomato" to "tomatoes",
    "phenomenon" to "phenomena",
    "index" to "indices",
    "matrix" to "matrices",
    "vertex" to "vertices",
    "crisis" to "crises",
    "axis" to "axes",
    "crisis" to "crises",
    "samurai" to "samurai",
    "radius" to "radii",
    "fungus" to "fungi",
    "millennium" to "millennia",
)

val pluralTransforms = listOf(
    (Regex("(.*(?:s|z|ch|sh|x))$") to "es"),
    (Regex("(.*)quy$") to "quies"),
    (Regex("(.*[^aeiou])y$") to "ies"),
    (Regex("(.*[aeiloru])f$") to "ves"),
    (Regex("(.*i)fe$") to "ves"),
    (Regex("(.*)man$") to "men"),
    (Regex("(.*)") to "s"),
)

val pluralCache = mutableMapOf<String,String>()

fun String.makePlural(quantity:Int = 2) =
    if (quantity==1) this
    else pluralCache[this]
        ?: pluralIrregulars[this]
        ?: (pluralTransforms.map{ pattern ->
            pattern.first.replace(this)
                { it.groupValues[1] + pattern.second }
            }.firstOrNull())?.also{ pluralCache[this] = it }
        ?: this
