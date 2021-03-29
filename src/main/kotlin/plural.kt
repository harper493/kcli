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
    "samurai" to "samurai",
    "radius" to "radii",
    "fungus" to "fungi",
    "millennium" to "millennia",
)

val pluralRules = listOf(
    (Regex("(.*(?:s|z|ch|sh|x))$") to "es"),
    (Regex("(.*)quy$") to "quies"),
    (Regex("(.*[^aeiou])y$") to "ies"),
    (Regex("(.*[aeiloru])f$") to "ves"),
    (Regex("(.*i)fe$") to "ves"),
    (Regex("(.*)man$") to "men"),
    (Regex("(.*)") to "s"),
)

val pluralCache = mutableMapOf<String,String>()

fun String.makePlural(quantity:Int = 2): String =
    if (quantity==1) this
    else makePluralOrSingular(pluralIrregulars, pluralRules, pluralCache)

val singularIrregulars = pluralIrregulars.map{ it.value to it.key }.toMap()

val singularRules = listOf(
    (Regex("(.*)ies$") to "y"),
    (Regex("(.*[aeloru])ves$") to "f"),
    (Regex("(.*i)ves$") to "fe"),
    (Regex("(.*)ses$") to "s"),
    (Regex("(.*)ches$") to "ch"),
    (Regex("(.*)shes$") to "sh"),
    (Regex("(.*)xes$") to "x"),
    (Regex("(.*)men$") to "man"),
    (Regex("(.*)s$") to ""),
)

val singularCache = mutableMapOf<String,String>()

fun String.makeSingular(): String =
    makePluralOrSingular(singularIrregulars, singularRules, singularCache)

fun String.makePluralOrSingular(irregulars: Map<String,String>,
                                rules: List<Pair<Regex,String>>,
                                cache: MutableMap<String,String>) =
    lazily(this, cache, {
        irregulars[this]
            ?: (rules.map { pattern ->
                pattern.first.replace(this)
                { it.groupValues[1] + pattern.second }}
                .firstOrNull { it != this })
            ?: this })

val articleRules = listOf(
    (Regex("(hour|honor|honour|honest)\\w*") to true),
    (Regex("ewe|ewer") to false),
    (Regex("uni[cfltv][aeiouy]\\w*") to false),
    (Regex("un\\w*") to true),
    (Regex("u[^aeiou][aeious]\\w*") to false),
    (Regex("[aeiou]\\w*") to true),
    (Regex("\\w*") to false),
)

val articleCache = mutableMapOf<String,Boolean>()

fun String.indefiniteArticle() =
    lazily(this, articleCache, {(articleRules
        .map{ pattern -> pattern.first
            .matchEntire(this)
            .ifNotNull(pattern.second) }
        .filterNotNull()
        .firstOrNull() ?: false)})
        .ifElse("an", "a")

