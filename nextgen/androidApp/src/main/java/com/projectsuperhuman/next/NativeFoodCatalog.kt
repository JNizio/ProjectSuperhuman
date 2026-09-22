package com.projectsuperhuman.next

import android.content.Context
import android.database.sqlite.SQLiteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.LinkedHashMap

internal enum class PlantFoodKind {
    NONE,
    FRUIT,
    VEGETABLE,
    LEGUME,
    GRAIN,
    NUT,
    SEED,
    HERB_SPICE,
    OTHER
}

internal data class PlantFoodIdentity(
    val isPlantFood: Boolean,
    val kind: PlantFoodKind = PlantFoodKind.NONE,
    val diversityKey: String = "",
    val diversityEligible: Boolean = isPlantFood && diversityKey.isNotBlank()
)

internal object PlantFoodClassifier {
    const val SCHEMA_VERSION = 3

    private val animalOrAmbiguousTerms = listOf(
        "beef", "pork", "chicken", "turkey", "lamb", "veal", "venison", "duck",
        "fish", "salmon", "tuna", "cod", "shrimp", "prawn", "crab", "lobster",
        "egg", "milk", "cheese", "yogurt", "yoghurt", "butter", "cream", "whey",
        "gelatin", "sausage", "ham", "bacon", "meatball"
    )

    private val identities: List<Triple<PlantFoodKind, String, List<String>>> = listOf(
        Triple(PlantFoodKind.FRUIT, "apple", listOf("apple")),
        Triple(PlantFoodKind.FRUIT, "banana", listOf("banana")),
        Triple(PlantFoodKind.FRUIT, "orange", listOf("orange")),
        Triple(PlantFoodKind.FRUIT, "lemon", listOf("lemon")),
        Triple(PlantFoodKind.FRUIT, "lime", listOf("lime")),
        Triple(PlantFoodKind.FRUIT, "grapefruit", listOf("grapefruit")),
        Triple(PlantFoodKind.FRUIT, "pear", listOf("pear")),
        Triple(PlantFoodKind.FRUIT, "peach", listOf("peach")),
        Triple(PlantFoodKind.FRUIT, "plum", listOf("plum")),
        Triple(PlantFoodKind.FRUIT, "apricot", listOf("apricot")),
        Triple(PlantFoodKind.FRUIT, "cherry", listOf("cherry", "cherries")),
        Triple(PlantFoodKind.FRUIT, "strawberry", listOf("strawberry", "strawberries")),
        Triple(PlantFoodKind.FRUIT, "blueberry", listOf("blueberry", "blueberries")),
        Triple(PlantFoodKind.FRUIT, "raspberry", listOf("raspberry", "raspberries")),
        Triple(PlantFoodKind.FRUIT, "blackberry", listOf("blackberry", "blackberries")),
        Triple(PlantFoodKind.FRUIT, "cranberry", listOf("cranberry", "cranberries")),
        Triple(PlantFoodKind.FRUIT, "grape", listOf("grape", "grapes")),
        Triple(PlantFoodKind.FRUIT, "kiwi", listOf("kiwi", "kiwifruit")),
        Triple(PlantFoodKind.FRUIT, "pineapple", listOf("pineapple")),
        Triple(PlantFoodKind.FRUIT, "mango", listOf("mango")),
        Triple(PlantFoodKind.FRUIT, "papaya", listOf("papaya")),
        Triple(PlantFoodKind.FRUIT, "watermelon", listOf("watermelon")),
        Triple(PlantFoodKind.FRUIT, "melon", listOf("cantaloupe", "honeydew", "melon")),
        Triple(PlantFoodKind.FRUIT, "pomegranate", listOf("pomegranate")),
        Triple(PlantFoodKind.FRUIT, "avocado", listOf("avocado")),
        Triple(PlantFoodKind.FRUIT, "olive", listOf("olive")),
        Triple(PlantFoodKind.FRUIT, "date", listOf("date", "dates")),
        Triple(PlantFoodKind.FRUIT, "fig", listOf("fig", "figs")),
        Triple(PlantFoodKind.VEGETABLE, "tomato", listOf("tomato", "tomatoes")),
        Triple(PlantFoodKind.VEGETABLE, "potato", listOf("potato", "potatoes")),
        Triple(PlantFoodKind.VEGETABLE, "sweet_potato", listOf("sweet potato")),
        Triple(PlantFoodKind.VEGETABLE, "onion", listOf("onion")),
        Triple(PlantFoodKind.VEGETABLE, "garlic", listOf("garlic")),
        Triple(PlantFoodKind.VEGETABLE, "carrot", listOf("carrot")),
        Triple(PlantFoodKind.VEGETABLE, "broccoli", listOf("broccoli")),
        Triple(PlantFoodKind.VEGETABLE, "cauliflower", listOf("cauliflower")),
        Triple(PlantFoodKind.VEGETABLE, "spinach", listOf("spinach")),
        Triple(PlantFoodKind.VEGETABLE, "kale", listOf("kale")),
        Triple(PlantFoodKind.VEGETABLE, "cabbage", listOf("cabbage")),
        Triple(PlantFoodKind.VEGETABLE, "lettuce", listOf("lettuce")),
        Triple(PlantFoodKind.VEGETABLE, "cucumber", listOf("cucumber")),
        Triple(PlantFoodKind.VEGETABLE, "pepper", listOf("bell pepper", "sweet pepper", "red pepper", "green pepper")),
        Triple(PlantFoodKind.VEGETABLE, "chilli", listOf("chili pepper", "chilli pepper", "jalapeno")),
        Triple(PlantFoodKind.VEGETABLE, "courgette", listOf("zucchini", "courgette")),
        Triple(PlantFoodKind.VEGETABLE, "aubergine", listOf("eggplant", "aubergine")),
        Triple(PlantFoodKind.VEGETABLE, "celery", listOf("celery")),
        Triple(PlantFoodKind.VEGETABLE, "beetroot", listOf("beet", "beetroot")),
        Triple(PlantFoodKind.VEGETABLE, "radish", listOf("radish")),
        Triple(PlantFoodKind.VEGETABLE, "turnip", listOf("turnip")),
        Triple(PlantFoodKind.VEGETABLE, "asparagus", listOf("asparagus")),
        Triple(PlantFoodKind.VEGETABLE, "leek", listOf("leek")),
        Triple(PlantFoodKind.VEGETABLE, "artichoke", listOf("artichoke")),
        Triple(PlantFoodKind.VEGETABLE, "pumpkin", listOf("pumpkin")),
        Triple(PlantFoodKind.VEGETABLE, "squash", listOf("squash")),
        Triple(PlantFoodKind.LEGUME, "lentil", listOf("lentil")),
        Triple(PlantFoodKind.LEGUME, "chickpea", listOf("chickpea", "garbanzo")),
        Triple(PlantFoodKind.LEGUME, "pea", listOf("green pea", "split pea", "peas")),
        Triple(PlantFoodKind.LEGUME, "bean", listOf("black bean", "kidney bean", "navy bean", "pinto bean", "white bean", "beans")),
        Triple(PlantFoodKind.LEGUME, "soy", listOf("soybean", "soy bean", "tofu", "tempeh", "edamame", "soy milk")),
        Triple(PlantFoodKind.GRAIN, "oat", listOf("oat", "oats", "oatmeal")),
        Triple(PlantFoodKind.GRAIN, "rice", listOf("rice")),
        Triple(PlantFoodKind.GRAIN, "wheat", listOf("wheat", "whole wheat", "bulgur", "semolina", "durum")),
        Triple(PlantFoodKind.GRAIN, "barley", listOf("barley")),
        Triple(PlantFoodKind.GRAIN, "rye", listOf("rye")),
        Triple(PlantFoodKind.GRAIN, "corn", listOf("corn", "maize", "polenta")),
        Triple(PlantFoodKind.GRAIN, "quinoa", listOf("quinoa")),
        Triple(PlantFoodKind.GRAIN, "buckwheat", listOf("buckwheat")),
        Triple(PlantFoodKind.GRAIN, "millet", listOf("millet")),
        Triple(PlantFoodKind.NUT, "almond", listOf("almond")),
        Triple(PlantFoodKind.NUT, "walnut", listOf("walnut")),
        Triple(PlantFoodKind.NUT, "peanut", listOf("peanut")),
        Triple(PlantFoodKind.NUT, "cashew", listOf("cashew")),
        Triple(PlantFoodKind.NUT, "pistachio", listOf("pistachio")),
        Triple(PlantFoodKind.NUT, "hazelnut", listOf("hazelnut")),
        Triple(PlantFoodKind.NUT, "pecan", listOf("pecan")),
        Triple(PlantFoodKind.NUT, "brazil_nut", listOf("brazil nut")),
        Triple(PlantFoodKind.SEED, "chia", listOf("chia")),
        Triple(PlantFoodKind.SEED, "flax", listOf("flax", "linseed")),
        Triple(PlantFoodKind.SEED, "sesame", listOf("sesame", "tahini")),
        Triple(PlantFoodKind.SEED, "sunflower_seed", listOf("sunflower seed")),
        Triple(PlantFoodKind.SEED, "pumpkin_seed", listOf("pumpkin seed")),
        Triple(PlantFoodKind.SEED, "hemp", listOf("hemp seed")),
        Triple(PlantFoodKind.HERB_SPICE, "parsley", listOf("parsley")),
        Triple(PlantFoodKind.HERB_SPICE, "basil", listOf("basil")),
        Triple(PlantFoodKind.HERB_SPICE, "mint", listOf("mint")),
        Triple(PlantFoodKind.HERB_SPICE, "coriander", listOf("coriander", "cilantro")),
        Triple(PlantFoodKind.HERB_SPICE, "dill", listOf("dill")),
        Triple(PlantFoodKind.HERB_SPICE, "rosemary", listOf("rosemary")),
        Triple(PlantFoodKind.HERB_SPICE, "thyme", listOf("thyme")),
        Triple(PlantFoodKind.HERB_SPICE, "turmeric", listOf("turmeric")),
        Triple(PlantFoodKind.HERB_SPICE, "ginger", listOf("ginger")),
        Triple(PlantFoodKind.HERB_SPICE, "cinnamon", listOf("cinnamon")),
        Triple(PlantFoodKind.HERB_SPICE, "cumin", listOf("cumin")),
        Triple(PlantFoodKind.HERB_SPICE, "paprika", listOf("paprika")),
        Triple(PlantFoodKind.HERB_SPICE, "black_pepper", listOf("black pepper")),
        Triple(PlantFoodKind.OTHER, "cocoa", listOf("cocoa", "cacao")),
        Triple(PlantFoodKind.OTHER, "coffee", listOf("coffee")),
        Triple(PlantFoodKind.OTHER, "tea", listOf("tea leaves", "green tea", "black tea"))
    ) + listOf(
        // Wider USDA/local-food coverage. Keep aliases canonical so preparation variants collapse
        // to the same weekly diversity key.
        Triple(PlantFoodKind.FRUIT, "mandarin", listOf("mandarin", "tangerine", "clementine", "satsuma")),
        Triple(PlantFoodKind.FRUIT, "nectarine", listOf("nectarine")),
        Triple(PlantFoodKind.FRUIT, "persimmon", listOf("persimmon")),
        Triple(PlantFoodKind.FRUIT, "guava", listOf("guava")),
        Triple(PlantFoodKind.FRUIT, "passion_fruit", listOf("passion fruit")),
        Triple(PlantFoodKind.FRUIT, "dragon_fruit", listOf("dragon fruit", "pitaya")),
        Triple(PlantFoodKind.FRUIT, "lychee", listOf("lychee", "litchi")),
        Triple(PlantFoodKind.FRUIT, "rambutan", listOf("rambutan")),
        Triple(PlantFoodKind.FRUIT, "jackfruit", listOf("jackfruit")),
        Triple(PlantFoodKind.FRUIT, "durian", listOf("durian")),
        Triple(PlantFoodKind.FRUIT, "coconut", listOf("coconut")),
        Triple(PlantFoodKind.FRUIT, "currant", listOf("currant", "currants")),
        Triple(PlantFoodKind.FRUIT, "gooseberry", listOf("gooseberry", "gooseberries")),
        Triple(PlantFoodKind.FRUIT, "elderberry", listOf("elderberry", "elderberries")),
        Triple(PlantFoodKind.FRUIT, "mulberry", listOf("mulberry", "mulberries")),
        Triple(PlantFoodKind.FRUIT, "boysenberry", listOf("boysenberry", "boysenberries")),
        Triple(PlantFoodKind.FRUIT, "plantain", listOf("plantain")),
        Triple(PlantFoodKind.FRUIT, "quince", listOf("quince")),
        Triple(PlantFoodKind.FRUIT, "kumquat", listOf("kumquat")),
        Triple(PlantFoodKind.FRUIT, "loquat", listOf("loquat")),
        Triple(PlantFoodKind.FRUIT, "acerola", listOf("acerola")),
        Triple(PlantFoodKind.FRUIT, "breadfruit", listOf("breadfruit")),
        Triple(PlantFoodKind.FRUIT, "carambola", listOf("carambola", "star fruit")),
        Triple(PlantFoodKind.FRUIT, "cherimoya", listOf("cherimoya")),
        Triple(PlantFoodKind.FRUIT, "sapodilla", listOf("sapodilla")),
        Triple(PlantFoodKind.FRUIT, "soursop", listOf("soursop")),
        Triple(PlantFoodKind.FRUIT, "tamarind", listOf("tamarind")),
        Triple(PlantFoodKind.FRUIT, "prickly_pear", listOf("prickly pear")),
        Triple(PlantFoodKind.FRUIT, "rose_hip", listOf("rose hip", "rose hips")),
        Triple(PlantFoodKind.VEGETABLE, "parsnip", listOf("parsnip")),
        Triple(PlantFoodKind.VEGETABLE, "swede", listOf("rutabaga", "swede")),
        Triple(PlantFoodKind.VEGETABLE, "yam", listOf("yam", "yams")),
        Triple(PlantFoodKind.VEGETABLE, "cassava", listOf("cassava", "yuca")),
        Triple(PlantFoodKind.VEGETABLE, "taro", listOf("taro")),
        Triple(PlantFoodKind.VEGETABLE, "jicama", listOf("jicama")),
        Triple(PlantFoodKind.VEGETABLE, "celeriac", listOf("celeriac", "celery root")),
        Triple(PlantFoodKind.VEGETABLE, "fennel", listOf("fennel bulb", "fennel")),
        Triple(PlantFoodKind.VEGETABLE, "okra", listOf("okra")),
        Triple(PlantFoodKind.VEGETABLE, "collard", listOf("collard greens", "collards")),
        Triple(PlantFoodKind.VEGETABLE, "chard", listOf("swiss chard", "chard")),
        Triple(PlantFoodKind.VEGETABLE, "watercress", listOf("watercress")),
        Triple(PlantFoodKind.VEGETABLE, "rocket", listOf("arugula", "rocket")),
        Triple(PlantFoodKind.VEGETABLE, "bok_choy", listOf("bok choy", "pak choi", "pak choy")),
        Triple(PlantFoodKind.VEGETABLE, "brussels_sprout", listOf("brussels sprout", "brussels sprouts")),
        Triple(PlantFoodKind.VEGETABLE, "endive", listOf("endive")),
        Triple(PlantFoodKind.VEGETABLE, "chicory", listOf("chicory", "radicchio")),
        Triple(PlantFoodKind.VEGETABLE, "mustard_green", listOf("mustard greens")),
        Triple(PlantFoodKind.VEGETABLE, "dandelion_green", listOf("dandelion greens")),
        Triple(PlantFoodKind.VEGETABLE, "kohlrabi", listOf("kohlrabi")),
        Triple(PlantFoodKind.VEGETABLE, "bamboo_shoot", listOf("bamboo shoot", "bamboo shoots")),
        Triple(PlantFoodKind.VEGETABLE, "heart_of_palm", listOf("heart of palm", "hearts of palm")),
        Triple(PlantFoodKind.VEGETABLE, "chayote", listOf("chayote")),
        Triple(PlantFoodKind.VEGETABLE, "burdock", listOf("burdock")),
        Triple(PlantFoodKind.VEGETABLE, "lotus_root", listOf("lotus root")),
        Triple(PlantFoodKind.VEGETABLE, "fiddlehead", listOf("fiddlehead", "fiddleheads")),
        Triple(PlantFoodKind.VEGETABLE, "nopales", listOf("nopales", "nopal")),
        Triple(PlantFoodKind.LEGUME, "fava_bean", listOf("fava bean", "broad bean")),
        Triple(PlantFoodKind.LEGUME, "mung_bean", listOf("mung bean")),
        Triple(PlantFoodKind.LEGUME, "lima_bean", listOf("lima bean")),
        Triple(PlantFoodKind.LEGUME, "adzuki_bean", listOf("adzuki", "aduki")),
        Triple(PlantFoodKind.LEGUME, "lupin", listOf("lupin", "lupine")),
        Triple(PlantFoodKind.LEGUME, "pigeon_pea", listOf("pigeon pea")),
        Triple(PlantFoodKind.LEGUME, "cowpea", listOf("cowpea", "black eyed pea", "black-eyed pea")),
        Triple(PlantFoodKind.GRAIN, "spelt", listOf("spelt")),
        Triple(PlantFoodKind.GRAIN, "sorghum", listOf("sorghum")),
        Triple(PlantFoodKind.GRAIN, "amaranth", listOf("amaranth")),
        Triple(PlantFoodKind.GRAIN, "teff", listOf("teff")),
        Triple(PlantFoodKind.GRAIN, "triticale", listOf("triticale")),
        Triple(PlantFoodKind.GRAIN, "farro", listOf("farro", "emmer")),
        Triple(PlantFoodKind.GRAIN, "kamut", listOf("kamut", "khorasan")),
        Triple(PlantFoodKind.NUT, "macadamia", listOf("macadamia")),
        Triple(PlantFoodKind.NUT, "chestnut", listOf("chestnut")),
        Triple(PlantFoodKind.NUT, "pine_nut", listOf("pine nut", "pine nuts")),
        Triple(PlantFoodKind.SEED, "poppy", listOf("poppy seed")),
        Triple(PlantFoodKind.SEED, "mustard_seed", listOf("mustard seed")),
        Triple(PlantFoodKind.SEED, "psyllium", listOf("psyllium")),
        Triple(PlantFoodKind.HERB_SPICE, "oregano", listOf("oregano")),
        Triple(PlantFoodKind.HERB_SPICE, "sage", listOf("sage")),
        Triple(PlantFoodKind.HERB_SPICE, "tarragon", listOf("tarragon")),
        Triple(PlantFoodKind.HERB_SPICE, "chive", listOf("chive", "chives")),
        Triple(PlantFoodKind.HERB_SPICE, "cardamom", listOf("cardamom")),
        Triple(PlantFoodKind.HERB_SPICE, "clove", listOf("clove", "cloves")),
        Triple(PlantFoodKind.HERB_SPICE, "nutmeg", listOf("nutmeg")),
        Triple(PlantFoodKind.HERB_SPICE, "anise", listOf("anise", "aniseed")),
        Triple(PlantFoodKind.HERB_SPICE, "saffron", listOf("saffron")),
        Triple(PlantFoodKind.HERB_SPICE, "allspice", listOf("allspice")),
        Triple(PlantFoodKind.HERB_SPICE, "fenugreek", listOf("fenugreek")),
        Triple(PlantFoodKind.HERB_SPICE, "sumac", listOf("sumac")),
        Triple(PlantFoodKind.HERB_SPICE, "lemongrass", listOf("lemongrass")),
        Triple(PlantFoodKind.OTHER, "seaweed", listOf("seaweed", "nori", "kelp", "wakame", "kombu"))
    )

    private val fallbackStopWords = setOf(
        "raw", "cooked", "boiled", "baked", "roasted", "fried", "grilled", "steamed",
        "canned", "frozen", "dried", "dehydrated", "fresh", "prepared", "drained",
        "solids", "liquid", "juice", "nectar", "unsweetened", "sweetened", "without",
        "with", "added", "salt", "sodium", "sugar", "oil", "fat", "mature", "ripe"
    )

    private fun normalizePlantText(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun fallbackDiversityKey(name: String): String {
        val tokens = normalizePlantText(name)
            .split(' ')
            .filter { it.isNotBlank() && it !in fallbackStopWords && it.length > 1 }
        return tokens.take(2).joinToString("_")
    }

    fun classify(name: String, searchText: String = "", ingredientsText: String = ""): PlantFoodIdentity {
        val normalizedName = normalizePlantText(name)
        val normalizedSearch = normalizePlantText(searchText)
        val normalizedIngredients = normalizePlantText(ingredientsText)
        val text = listOf(normalizedName, normalizedSearch, normalizedIngredients)
            .filter(String::isNotBlank)
            .joinToString(" ")
        if (text.isBlank()) return PlantFoodIdentity(false)

        val explicitPlantSubstitute = listOf(
            "soy milk", "almond milk", "oat milk", "rice milk", "cashew milk", "coconut milk"
        ).any { phrase -> text == phrase || text.startsWith("$phrase ") || text.contains(" $phrase ") }

        val hasAnimalSignal = animalOrAmbiguousTerms.any { term ->
            if (term == "milk" && explicitPlantSubstitute) return@any false
            if (term == "butter" && listOf("butter bean", "butter beans").any { contains ->
                    text == contains || text.startsWith("$contains ") || text.contains(" $contains ")
                }) return@any false
            text == term || text.startsWith("$term ") || text.contains(" $term ")
        }

        val compositePlantIdentityUnsafe = listOf(
            "pie", "cake", "cookie", "cookies", "biscuit", "biscuits", "muffin", "pastry",
            "pizza", "sandwich", "burger", "casserole", "lasagna", "soup", "stew", "sauce",
            "dressing", "ready meal", "entree"
        ).any { phrase ->
            normalizedName == phrase ||
                normalizedName.startsWith("$phrase ") ||
                normalizedName.endsWith(" $phrase") ||
                normalizedName.contains(" $phrase ")
        }
        if (compositePlantIdentityUnsafe) return PlantFoodIdentity(false)

        val diversityIneligible = explicitPlantSubstitute ||
            listOf(" oil", "oil ", "extract", "syrup", "sweetener").any { marker ->
                normalizedName == marker.trim() ||
                    normalizedName.startsWith(marker.trim() + " ") ||
                    normalizedName.endsWith(marker) ||
                    normalizedName.contains(marker)
            }

        val match = identities.mapNotNull { identity ->
            val longestMatchedTerm = identity.third
                .map(::normalizePlantText)
                .filter { normalized ->
                    text == normalized || text.startsWith("$normalized ") || text.contains(" $normalized ")
                }
                .maxByOrNull { it.length }
            longestMatchedTerm?.let { identity to it.length }
        }.maxByOrNull { it.second }?.first

        if (match != null) {
            if (hasAnimalSignal && match.first !in setOf(PlantFoodKind.HERB_SPICE, PlantFoodKind.OTHER)) {
                return PlantFoodIdentity(false)
            }
            return PlantFoodIdentity(
                isPlantFood = true,
                kind = match.first,
                diversityKey = match.second,
                diversityEligible = !diversityIneligible
            )
        }

        // USDA imports now carry their source food-category text into searchText. This fallback
        // keeps uncommon botanical foods classified even when they are not yet in the alias table.
        if (hasAnimalSignal) return PlantFoodIdentity(false)
        val fallbackKind = when {
            "fruits and fruit juices" in normalizedSearch ||
                normalizedSearch.contains("wweia fruit") -> PlantFoodKind.FRUIT
            "vegetables and vegetable products" in normalizedSearch ||
                normalizedSearch.contains("wweia vegetable") -> PlantFoodKind.VEGETABLE
            "legumes and legume products" in normalizedSearch ||
                normalizedSearch.contains("dry beans peas lentils") -> PlantFoodKind.LEGUME
            "cereal grains and pasta" in normalizedSearch ||
                normalizedSearch.contains("whole grains") -> PlantFoodKind.GRAIN
            "nut and seed products" in normalizedSearch ||
                normalizedSearch.contains("nuts and seeds") -> PlantFoodKind.NUT
            "spices and herbs" in normalizedSearch -> PlantFoodKind.HERB_SPICE
            else -> PlantFoodKind.NONE
        }
        if (fallbackKind == PlantFoodKind.NONE) return PlantFoodIdentity(false)

        val key = fallbackDiversityKey(normalizedName)
        return if (key.isBlank()) PlantFoodIdentity(false)
        else PlantFoodIdentity(true, fallbackKind, key, diversityEligible = !diversityIneligible)
    }
}


internal enum class FoodTag {
    PLANT,
    ANIMAL,
    ANIMAL_DERIVED,
    FRUIT,
    VEGETABLE,
    LEAFY_GREEN,
    LEGUME,
    GRAIN,
    WHOLE_GRAIN,
    NUT,
    SEED,
    HERB_SPICE,
    HERB,
    SPICE,
    SEAWEED,
    MUSHROOM,
    MEAT,
    POULTRY,
    CHICKEN,
    CHICKEN_BREAST,
    CHICKEN_LEG,
    CHICKEN_WING,
    TURKEY,
    DUCK,
    BEEF,
    PORK,
    LAMB,
    GAME_MEAT,
    FISH,
    SEAFOOD,
    SHELLFISH,
    EGG,
    DAIRY,
    MILK,
    YOGURT,
    CHEESE,
    CREAM,
    BUTTER,
    BREAD,
    BAKERY,
    BAKED_GOOD,
    BAKING_INGREDIENT,
    FLOUR,
    CEREAL,
    PASTA,
    RICE,
    POTATO,
    OIL_FAT,
    OIL,
    FAT,
    SWEETENER,
    CONFECTIONERY,
    DESSERT,
    BEVERAGE,
    SAUCE_CONDIMENT,
    SAUCE,
    CONDIMENT,
    SOUP_STEW,
    MIXED_DISH,
    PREPARED_FOOD,
    PROCESSED_FOOD,
    PROCESSED_MEAT,
    FERMENTED,
    RAW,
    BOILED,
    STEAMED,
    BAKED,
    ROASTED,
    GRILLED,
    FRIED,
    CANNED,
    FROZEN,
    DRIED,
    OTHER
}

/**
 * Multi-label food taxonomy used by nutrition scores, filtering and analytics.
 *
 * Tags are derived from source-backed names/categories rather than hand-entered per database row.
 * This makes the taxonomy repeatable for the 10k local catalogue and for every future food import.
 */
internal object FoodTaxonomyClassifier {
    const val SCHEMA_VERSION = 2

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun containsPhrase(text: String, phrase: String): Boolean {
        val p = normalize(phrase)
        return text == p || text.startsWith("$p ") || text.endsWith(" $p") || text.contains(" $p ")
    }

    fun classify(
        name: String,
        searchText: String = "",
        ingredientsText: String = "",
        plantIdentity: PlantFoodIdentity = PlantFoodClassifier.classify(name, searchText, ingredientsText)
    ): Set<FoodTag> {
        val n = normalize(name)
        val s = normalize(searchText)
        val i = normalize(ingredientsText)
        val all = listOf(n, s, i).filter(String::isNotBlank).joinToString(" ")
        fun nHas(vararg terms: String) = terms.any { containsPhrase(n, it) }
        fun allHas(vararg terms: String) = terms.any { containsPhrase(all, it) }
        fun categoryHas(vararg terms: String) = terms.any { containsPhrase(s, it) }

        val tags = linkedSetOf<FoodTag>()

        if (plantIdentity.isPlantFood) {
            tags += FoodTag.PLANT
            when (plantIdentity.kind) {
                PlantFoodKind.FRUIT -> tags += FoodTag.FRUIT
                PlantFoodKind.VEGETABLE -> tags += FoodTag.VEGETABLE
                PlantFoodKind.LEGUME -> tags += FoodTag.LEGUME
                PlantFoodKind.GRAIN -> tags += FoodTag.GRAIN
                PlantFoodKind.NUT -> tags += FoodTag.NUT
                PlantFoodKind.SEED -> tags += FoodTag.SEED
                PlantFoodKind.HERB_SPICE -> tags += FoodTag.HERB_SPICE
                PlantFoodKind.OTHER -> Unit
                PlantFoodKind.NONE -> Unit
            }
        }

        if (nHas("mushroom", "mushrooms", "shiitake", "portabella", "portobello", "oyster mushroom")) {
            tags += FoodTag.MUSHROOM
        }
        if (nHas("seaweed", "nori", "kelp", "wakame", "kombu", "dulse")) {
            tags += FoodTag.SEAWEED
        }

        if (FoodTag.VEGETABLE in tags && nHas(
                "spinach", "kale", "lettuce", "arugula", "rocket", "watercress",
                "swiss chard", "chard", "collard", "bok choy", "pak choi",
                "mustard greens", "dandelion greens", "endive", "chicory", "radicchio"
            )
        ) {
            tags += FoodTag.LEAFY_GREEN
        }

        val chicken = nHas("chicken", "broiler", "broilers", "fryer", "fryers", "capons")
        val turkey = nHas("turkey")
        val duck = nHas("duck")
        val poultry = chicken || turkey || duck || nHas("goose", "quail", "pheasant")
        if (poultry) {
            tags += FoodTag.ANIMAL_DERIVED
            tags += FoodTag.MEAT
            tags += FoodTag.POULTRY
        }
        if (chicken) {
            tags += FoodTag.CHICKEN
            when {
                nHas("breast") -> tags += FoodTag.CHICKEN_BREAST
                nHas("leg", "thigh", "drumstick") -> tags += FoodTag.CHICKEN_LEG
                nHas("wing", "wings") -> tags += FoodTag.CHICKEN_WING
            }
        }
        if (turkey) tags += FoodTag.TURKEY
        if (duck) tags += FoodTag.DUCK

        if (nHas("beef", "veal")) {
            tags += FoodTag.ANIMAL_DERIVED
            tags += FoodTag.MEAT
            tags += FoodTag.BEEF
        }
        if (nHas("pork", "ham", "bacon")) {
            tags += FoodTag.ANIMAL_DERIVED
            tags += FoodTag.MEAT
            tags += FoodTag.PORK
        }
        if (nHas("lamb", "mutton")) {
            tags += FoodTag.ANIMAL_DERIVED
            tags += FoodTag.MEAT
            tags += FoodTag.LAMB
        }
        if (nHas("venison", "rabbit", "bison", "buffalo", "boar", "elk", "moose")) {
            tags += FoodTag.ANIMAL_DERIVED
            tags += FoodTag.MEAT
            tags += FoodTag.GAME_MEAT
        }
        if (nHas("sausage", "salami", "pepperoni", "hot dog", "frankfurter", "luncheon meat", "corned beef")) {
            tags += FoodTag.ANIMAL_DERIVED
            tags += FoodTag.MEAT
            tags += FoodTag.PROCESSED_MEAT
        }

        val fish = nHas(
            "fish", "salmon", "tuna", "cod", "haddock", "mackerel", "sardine", "sardines",
            "trout", "herring", "anchovy", "anchovies", "halibut", "tilapia", "pollock",
            "sole", "flounder", "carp", "bass", "snapper", "swordfish"
        )
        val shellfish = nHas(
            "shrimp", "prawn", "prawns", "crab", "lobster", "oyster", "oysters", "mussel",
            "mussels", "clam", "clams", "scallop", "scallops", "squid", "octopus", "crayfish"
        )
        if (fish) {
            tags += FoodTag.ANIMAL_DERIVED
            tags += FoodTag.FISH
            tags += FoodTag.SEAFOOD
        }
        if (shellfish) {
            tags += FoodTag.ANIMAL_DERIVED
            tags += FoodTag.SEAFOOD
            tags += FoodTag.SHELLFISH
        }

        if (nHas("egg", "eggs") && !nHas("eggplant")) {
            tags += FoodTag.ANIMAL_DERIVED
            tags += FoodTag.EGG
        }

        val yogurt = nHas("yogurt", "yoghurt", "skyr", "kefir")
        val cheese = nHas(
            "cheese", "cheddar", "mozzarella", "parmesan", "ricotta", "cottage cheese",
            "feta", "brie", "camembert", "gouda", "edam", "halloumi", "mascarpone"
        )
        val milk = nHas("milk", "buttermilk") &&
            !nHas("soy milk", "almond milk", "oat milk", "rice milk", "cashew milk", "coconut milk")
        val cream = nHas("cream", "creme fraiche", "sour cream", "half and half") &&
            !nHas("cream of", "ice cream", "cream cheese")
        val butter = nHas("butter") && !nHas("butter bean", "butter beans", "peanut butter", "almond butter")
        val otherDairy = nHas("whey", "casein")
        if (yogurt || cheese || milk || cream || butter || otherDairy) {
            tags += FoodTag.ANIMAL_DERIVED
            tags += FoodTag.DAIRY
        }
        if (milk) tags += FoodTag.MILK
        if (yogurt) tags += FoodTag.YOGURT
        if (cheese) tags += FoodTag.CHEESE
        if (cream) tags += FoodTag.CREAM
        if (butter) tags += FoodTag.BUTTER

        if (nHas("bread", "roll", "bun", "bagel", "pita", "naan", "tortilla", "sourdough")) {
            tags += FoodTag.BREAD
            tags += FoodTag.BAKED_GOOD
            if (!nHas("egg", "cheese", "milk", "butter", "lard", "meat", "chicken", "beef", "pork")) {
                tags += FoodTag.PLANT
                tags += FoodTag.GRAIN
            }
        }
        if (nHas(
                "cake", "cupcake", "muffin", "pastry", "croissant", "biscuit", "cookie", "cookies",
                "cracker", "doughnut", "donut", "scone", "brownie", "waffle", "pancake",
                "pie", "tart", "danish"
            ) || categoryHas("baked products")
        ) {
            tags += FoodTag.BAKED_GOOD
        }
        if (nHas("flour")) {
            tags += FoodTag.BAKING_INGREDIENT
            tags += FoodTag.FLOUR
        }
        if (nHas("baking powder", "baking soda", "yeast", "cornstarch", "corn starch", "cocoa powder")) {
            tags += FoodTag.BAKING_INGREDIENT
        }
        if (nHas("cereal", "granola", "muesli", "porridge", "oatmeal")) tags += FoodTag.CEREAL
        if (nHas("pasta", "spaghetti", "macaroni", "noodle", "noodles", "lasagna noodles")) tags += FoodTag.PASTA
        if (plantIdentity.diversityKey == "rice" || nHas("rice")) tags += FoodTag.RICE
        if (plantIdentity.diversityKey in setOf("potato", "sweet_potato") || nHas("potato", "potatoes")) tags += FoodTag.POTATO

        if (nHas(
                "whole wheat", "wholemeal", "whole grain", "wholegrain", "brown rice",
                "rolled oats", "steel cut oats", "oatmeal", "whole oats",
                "hulled barley", "whole barley", "whole rye", "quinoa", "buckwheat"
            )) {
            if (FoodTag.GRAIN in tags || FoodTag.CEREAL in tags || FoodTag.BREAD in tags) tags += FoodTag.WHOLE_GRAIN
        }

        if (nHas("olive oil", "vegetable oil", "canola oil", "rapeseed oil", "sunflower oil", "coconut oil", "oil", "lard", "shortening", "margarine")) {
            tags += FoodTag.OIL_FAT
        }
        if (nHas("olive oil", "vegetable oil", "canola oil", "rapeseed oil", "sunflower oil", "coconut oil", "avocado oil", "sesame oil")) {
            tags += FoodTag.OIL
            tags += FoodTag.PLANT
        }
        if (nHas("lard", "shortening", "margarine", "butter")) tags += FoodTag.FAT
        if (nHas("sugar", "honey", "syrup", "molasses", "agave")) tags += FoodTag.SWEETENER
        if (nHas("candy", "chocolate", "toffee", "caramel", "gumdrop", "marshmallow") || categoryHas("sweets")) {
            tags += FoodTag.CONFECTIONERY
        }
        if (nHas("ice cream", "pudding", "custard", "dessert", "cheesecake", "brownie")) tags += FoodTag.DESSERT

        if (nHas("juice", "smoothie", "coffee", "tea", "beverage", "drink", "soda", "water")) tags += FoodTag.BEVERAGE
        if (nHas("sauce", "ketchup", "mustard", "mayonnaise", "dressing", "relish", "chutney", "salsa", "vinegar")) {
            tags += FoodTag.SAUCE_CONDIMENT
        }
        if (nHas("sauce", "ketchup", "dressing", "salsa")) tags += FoodTag.SAUCE
        if (nHas("mustard", "mayonnaise", "relish", "chutney", "vinegar", "ketchup")) tags += FoodTag.CONDIMENT
        if (nHas("soup", "stew", "chowder", "broth")) tags += FoodTag.SOUP_STEW
        if (nHas("pizza", "sandwich", "burger", "burrito", "taco", "casserole", "curry", "lasagna", "meal", "entree")) {
            tags += FoodTag.MIXED_DISH
        }

        if (nHas("yogurt", "yoghurt", "kefir", "tempeh", "miso", "kimchi", "sauerkraut", "sourdough", "fermented")) {
            tags += FoodTag.FERMENTED
        }

        if (FoodTag.HERB_SPICE in tags) {
            if (nHas("cinnamon", "cumin", "paprika", "black pepper", "turmeric", "cardamom", "clove", "nutmeg", "anise", "saffron", "allspice", "fenugreek", "sumac")) {
                tags += FoodTag.SPICE
            } else {
                tags += FoodTag.HERB
            }
        }
        if (FoodTag.BREAD in tags || FoodTag.BAKED_GOOD in tags) tags += FoodTag.BAKERY
        if (FoodTag.ANIMAL_DERIVED in tags) tags += FoodTag.ANIMAL
        if (tags.any { it in setOf(FoodTag.MIXED_DISH, FoodTag.SOUP_STEW, FoodTag.SAUCE_CONDIMENT, FoodTag.BAKED_GOOD) }) {
            tags += FoodTag.PREPARED_FOOD
        }
        if (tags.any { it in setOf(FoodTag.PROCESSED_MEAT, FoodTag.CONFECTIONERY, FoodTag.DESSERT) }) {
            tags += FoodTag.PROCESSED_FOOD
        }

        if (nHas("raw")) tags += FoodTag.RAW
        if (nHas("boiled", "hard boiled")) tags += FoodTag.BOILED
        if (nHas("steamed")) tags += FoodTag.STEAMED
        if (nHas("baked")) tags += FoodTag.BAKED
        if (nHas("roasted", "roast")) tags += FoodTag.ROASTED
        if (nHas("grilled", "broiled")) tags += FoodTag.GRILLED
        if (nHas("fried", "deep fried", "stir fried")) tags += FoodTag.FRIED
        if (nHas("canned")) tags += FoodTag.CANNED
        if (nHas("frozen")) tags += FoodTag.FROZEN
        if (nHas("dried", "dehydrated")) tags += FoodTag.DRIED

        // USDA category fallback keeps every local record usable even when the food has an unusual name.
        when {
            categoryHas("fruits and fruit juices") -> tags += setOf(FoodTag.PLANT, FoodTag.FRUIT)
            categoryHas("vegetables and vegetable products") -> tags += setOf(FoodTag.PLANT, FoodTag.VEGETABLE)
            categoryHas("legumes and legume products") -> tags += setOf(FoodTag.PLANT, FoodTag.LEGUME)
            categoryHas("cereal grains and pasta") -> tags += setOf(FoodTag.PLANT, FoodTag.GRAIN)
            categoryHas("nut and seed products") -> tags += setOf(FoodTag.PLANT, FoodTag.NUT)
            categoryHas("spices and herbs") -> tags += setOf(FoodTag.PLANT, FoodTag.HERB_SPICE)
            categoryHas("poultry products") -> tags += setOf(FoodTag.ANIMAL_DERIVED, FoodTag.MEAT, FoodTag.POULTRY)
            categoryHas("beef products") -> tags += setOf(FoodTag.ANIMAL_DERIVED, FoodTag.MEAT, FoodTag.BEEF)
            categoryHas("pork products") -> tags += setOf(FoodTag.ANIMAL_DERIVED, FoodTag.MEAT, FoodTag.PORK)
            categoryHas("lamb veal and game products") -> tags += setOf(FoodTag.ANIMAL_DERIVED, FoodTag.MEAT)
            categoryHas("finfish and shellfish products") -> tags += setOf(FoodTag.ANIMAL_DERIVED, FoodTag.SEAFOOD)
            categoryHas("fats and oils") -> tags += FoodTag.OIL_FAT
            categoryHas("soups sauces and gravies") -> tags += FoodTag.SOUP_STEW
        }

        if (tags.isEmpty()) tags += FoodTag.OTHER
        return tags
    }
}

internal enum class CarbohydrateDefinition {
    /** EU/Open Food Facts carbohydrate: available carbohydrate, excluding fibre. */
    AVAILABLE_EXCLUDING_FIBRE,
    /** USDA carbohydrate by difference: includes dietary fibre. */
    TOTAL_INCLUDING_FIBRE,
    /** Source does not establish a compatible carbohydrate definition. */
    UNKNOWN
}

internal data class NativeNutrient(
    val id: String,
    val label: String,
    val valuePer100: Double,
    val unit: String,
    val evidenceKind: NutrientEvidenceKind = NutrientEvidenceKind.UNSPECIFIED,
    val source: String = "",
    val sourceRecordId: String = "",
    val derivedFrom: String = ""
)

internal data class NativeFood(
    val id: String,
    val name: String,
    val country: String,
    val kcal: Double,
    val kcalKnown: Boolean = true,
    val protein: Double,
    val carbs: Double,
    val carbohydrateDefinition: CarbohydrateDefinition = CarbohydrateDefinition.UNKNOWN,
    val fat: Double,
    val fibre: Double,
    val sugar: Double,
    val unit: String,
    val source: String,
    val barcode: String? = null,
    val searchText: String = "",
    val brand: String = "",
    val quantity: String = "",
    val servingSize: String = "",
    val micronutrients: Map<String, NativeNutrient> = emptyMap(),
    val proteinKnown: Boolean = true,
    val carbsKnown: Boolean = true,
    val fatKnown: Boolean = true,
    val fibreKnown: Boolean = true,
    val sugarKnown: Boolean = true,
    val saturatedFat: Double = 0.0,
    val saturatedFatKnown: Boolean = false,
    val salt: Double = 0.0,
    val saltKnown: Boolean = false,
    val sodiumMg: Double = 0.0,
    val sodiumKnown: Boolean = false,
    val nutritionIntegrityWarning: String? = null,
    val nutritionApproximate: Boolean = false,
    val originalName: String = "",
    val displayLanguage: String = "",
    val hasVerifiedEnglishName: Boolean = false,
    val basisAmount: Double = 100.0,
    val basisUnit: FoodUnit = FoodUnit.G,
    val densityGPerMl: Double? = null,
    val densityApproximate: Boolean = false,
    val densitySource: DensityEvidenceSource = DensityEvidenceSource.UNKNOWN,
    val productQuantity: Double? = null,
    val productQuantityUnit: FoodUnit? = null,
    val servingQuantity: Double? = null,
    val servingQuantityUnit: FoodUnit? = null,
    val servingLabel: String = "",
    val identityKind: FoodIdentityKind = FoodIdentityKind.UNKNOWN,
    val sourceType: FoodDataSourceType = FoodDataSourceType.UNKNOWN,
    val sourceRecordId: String = "",
    val sourceRevision: String = "",
    val lastRetrievedEpochMs: Long? = null,
    val verificationState: FoodVerificationState = FoodVerificationState.UNVERIFIED,
    val confidence: FoodDataConfidence = FoodDataConfidence.UNASSESSED,
    val preparationState: FoodPreparationState = FoodPreparationState.UNSPECIFIED,
    val energyEvidence: EnergyEvidenceKind = EnergyEvidenceKind.UNKNOWN,
    val nutrientEvidence: Map<String, NutrientEvidenceKind> = emptyMap(),
    val ingredientsText: String = "",
    val allergens: List<String> = emptyList(),
    val additives: List<String> = emptyList(),
    val novaGroup: Int? = null,
    val imageReferences: Map<String, String> = emptyMap(),
    val correctedFields: Set<String> = emptySet(),
    val sourceWarnings: List<String> = emptyList(),
    val isPlantFood: Boolean = false,
    val plantFoodKind: PlantFoodKind = PlantFoodKind.NONE,
    val plantDiversityKey: String = "",
    val plantDiversityEligible: Boolean = isPlantFood && plantDiversityKey.isNotBlank(),
    val foodTags: Set<FoodTag> = emptySet(),
    val foodTaxonomyVersion: Int = FoodTaxonomyClassifier.SCHEMA_VERSION,
    val canonicalSchemaVersion: Int = NUTRITION_CANONICAL_SCHEMA_VERSION
)

internal data class NativeFoodSearchResult(
    val foods: List<NativeFood>,
    val remoteAvailable: Boolean,
    val remoteCount: Int
)

internal object NativeFoodCatalog {
    private const val USER_AGENT = "ProjectSuperhuman/11.4 (Android; https://github.com/JNizio/ProjectSuperhuman)"
    private const val OFF_FIELDS = "code,lang,languages_tags,product_name,product_name_en,generic_name,generic_name_en,brands,countries_tags,categories,quantity,product_quantity,product_quantity_unit,serving_size,serving_quantity,serving_quantity_unit,nutrition_data_per,data_quality_errors_tags,data_quality_warnings_tags,nutriments,ingredients_text,allergens_tags,additives_tags,nova_group,image_url,image_front_url,image_nutrition_url,image_ingredients_url,last_modified_t,last_modified_datetime"
    private const val FAST_RESULT_COUNT = 6
    private const val MAX_RESULT_COUNT = 8

    @Volatile private var cached: List<NativeFood>? = null

    private val remoteSearchCache = object : LinkedHashMap<String, List<NativeFood>>(12, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<NativeFood>>?): Boolean = size > 12
    }
    private val remoteCacheLock = Any()

    private data class NutrientSpec(
        val offId: String,
        val id: String,
        val label: String,
        val unit: String,
        val multiplierFromGrams: Double
    )

    /**
     * Open Food Facts normalises weight-based `<nutrient>_100g` values to grams.
     * Convert common vitamins/minerals into human-friendly units before they enter the diary.
     */
    private val micronutrientSpecs = listOf(
        NutrientSpec("calcium", "calcium", "Calcium", "mg", 1_000.0),
        NutrientSpec("chloride", "chloride", "Chloride", "mg", 1_000.0),
        NutrientSpec("copper", "copper", "Copper", "mg", 1_000.0),
        NutrientSpec("iron", "iron", "Iron", "mg", 1_000.0),
        NutrientSpec("iodine", "iodine", "Iodine", "µg", 1_000_000.0),
        NutrientSpec("magnesium", "magnesium", "Magnesium", "mg", 1_000.0),
        NutrientSpec("manganese", "manganese", "Manganese", "mg", 1_000.0),
        NutrientSpec("phosphorus", "phosphorus", "Phosphorus", "mg", 1_000.0),
        NutrientSpec("potassium", "potassium", "Potassium", "mg", 1_000.0),
        NutrientSpec("selenium", "selenium", "Selenium", "µg", 1_000_000.0),
        NutrientSpec("sodium", "sodium", "Sodium", "mg", 1_000.0),
        NutrientSpec("zinc", "zinc", "Zinc", "mg", 1_000.0),
        NutrientSpec("vitamin-a", "vitamin_a", "Vitamin A", "µg", 1_000_000.0),
        NutrientSpec("vitamin-b1", "vitamin_b1", "Vitamin B1", "mg", 1_000.0),
        NutrientSpec("vitamin-b2", "vitamin_b2", "Vitamin B2", "mg", 1_000.0),
        NutrientSpec("vitamin-pp", "niacin", "Niacin (B3)", "mg", 1_000.0),
        NutrientSpec("pantothenic-acid", "pantothenic_acid", "Pantothenic acid (B5)", "mg", 1_000.0),
        NutrientSpec("vitamin-b6", "vitamin_b6", "Vitamin B6", "mg", 1_000.0),
        NutrientSpec("vitamin-b9", "folate", "Folate (B9)", "µg", 1_000_000.0),
        NutrientSpec("folates", "folate", "Folate (B9)", "µg", 1_000_000.0),
        NutrientSpec("vitamin-b12", "vitamin_b12", "Vitamin B12", "µg", 1_000_000.0),
        NutrientSpec("biotin", "biotin", "Biotin (B7)", "µg", 1_000_000.0),
        NutrientSpec("vitamin-c", "vitamin_c", "Vitamin C", "mg", 1_000.0),
        NutrientSpec("vitamin-d", "vitamin_d", "Vitamin D", "µg", 1_000_000.0),
        NutrientSpec("vitamin-e", "vitamin_e", "Vitamin E", "mg", 1_000.0),
        NutrientSpec("vitamin-k", "vitamin_k", "Vitamin K", "µg", 1_000_000.0),
        NutrientSpec("choline", "choline", "Choline", "mg", 1_000.0)
    )

    /**
     * The large USDA importer writes in the background. On some Android SQLite builds, opening
     * another helper while that transaction is active can throw SQLITE_BUSY while applying
     * PRAGMA journal_mode. Food search must never crash because the optional expanded catalogue is
     * busy: bundled foods and Open Food Facts remain usable while the importer finishes.
     */
    private fun startLargeLocalSafely(context: Context) {
        try {
            LargeLocalFoodDatabase.ensureStarted(context)
        } catch (_: SQLiteException) {
            // The background importer already owns the DB. Search can continue with other sources.
        }
    }

    suspend fun all(context: Context): List<NativeFood> = withContext(Dispatchers.IO) {
        FoodNutritionOverrideStore.attach(context)
        startLargeLocalSafely(context)
        val base = cached ?: loadLocal(context).also { cached = it }
        FoodNutritionOverrideStore.applyAll(context, base.map(FoodEvidenceEngine::enrich))
    }

    /** Number of local reference foods available without depending on an Open Food Facts search. */
    suspend fun localReferenceCount(context: Context): Int {
        val bundled = all(context).size
        val expanded = try {
            LargeLocalFoodDatabase.count(context)
        } catch (_: SQLiteException) {
            0
        }
        return bundled + expanded
    }

    /**
     * Fast progressive search.
     *
     * A broad query should not build and render dozens of cards or wait on the network when the
     * local database already has strong matches. We rank a small local candidate set first and only
     * call Open Food Facts when local coverage is thin or the user typed a more specific multi-word
     * product query. The UI therefore gets the most useful 8-10 results quickly; typing a more
     * specific query is the cheap way to drill further into the catalogue.
     */
    suspend fun search(context: Context, query: String, limit: Int = MAX_RESULT_COUNT): NativeFoodSearchResult =
        withContext(Dispatchers.IO) {
        FoodNutritionOverrideStore.attach(context)
        startLargeLocalSafely(context)
        val q = query.trim()
        if (q.length < 2) return@withContext NativeFoodSearchResult(emptyList(), remoteAvailable = true, remoteCount = 0)

        // A pasted/scanned GTIN is an exact identity query and must outrank fuzzy text search.
        val typedDigits = q.filter(Char::isDigit)
        val barcodeLike = q.all { it.isDigit() || it.isWhitespace() || it == '-' }
        if (barcodeLike && NutritionMath.isValidBarcode(typedDigits)) {
            lookupBarcode(context, typedDigits)?.let { exact ->
                return@withContext NativeFoodSearchResult(
                    foods = listOf(exact),
                    remoteAvailable = true,
                    remoteCount = if (exact.sourceType == FoodDataSourceType.OPEN_FOOD_FACTS) 1 else 0
                )
            }
        }

        val requested = limit.coerceIn(1, MAX_RESULT_COUNT)
        val bundledLocal = searchLocal(context, q, limit = 10)
        val expandedLocal = try {
            LargeLocalFoodDatabase.search(context, q, limit = 16)
        } catch (_: SQLiteException) {
            emptyList()
        }

        val nutrientRichCore = expandedLocal.filter { food ->
            food.id.startsWith("core:") && food.micronutrients.isNotEmpty()
        }
        val bundledVisible = if (nutrientRichCore.isEmpty()) {
            bundledLocal
        } else {
            val coreTokens = nutrientRichCore.flatMap { normalizeFoodTokens(it.name) }.toSet()
            bundledLocal.filterNot { legacy ->
                legacy.micronutrients.isEmpty() &&
                    normalizeFoodTokens(legacy.name).any { it in coreTokens } &&
                    foodSearchRank(legacy, q.lowercase()) <= 1
            }
        }

        val localCandidates = (nutrientRichCore + expandedLocal + bundledVisible)
            .map(FoodEvidenceEngine::enrich)
            .sortedWith(foodComparator(q))
            .distinctBy(FoodEvidenceEngine::dedupKey)

        // Stay local when we already have enough strong matches. Network search is the fallback,
        // not a tax paid on every multi-word query.
        val strongLocalMatch = localCandidates.any { foodSearchRank(it, q.lowercase()) <= 1 }
        val queryTokens = q.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
        val looksLikeGenericIngredient = queryTokens.isNotEmpty() &&
            queryTokens.size <= 4 &&
            queryTokens.none { token -> token.any(Char::isDigit) } &&
            localCandidates.count {
                it.sourceType == FoodDataSourceType.USDA_FOUNDATION ||
                    it.sourceType == FoodDataSourceType.USDA_FNDDS ||
                    it.sourceType == FoodDataSourceType.USDA_SR_LEGACY ||
                    it.sourceType == FoodDataSourceType.PROJECT_SUPERHUMAN_REFERENCE
            } >= 4
        val shouldQueryRemote = !looksLikeGenericIngredient &&
            (localCandidates.size < FAST_RESULT_COUNT || !strongLocalMatch)
        val remoteResult = if (shouldQueryRemote) {
            searchOpenFoodFacts(context, q, limit = MAX_RESULT_COUNT)
        } else {
            emptyList<NativeFood>() to true
        }

        val merged = FoodNutritionOverrideStore.applyAll(
            context,
            (localCandidates + remoteResult.first)
                .map(FoodEvidenceEngine::enrich)
                .sortedWith(foodComparator(q))
                .distinctBy(FoodEvidenceEngine::dedupKey)
        ).take(requested)

        NativeFoodSearchResult(
            foods = merged,
            remoteAvailable = remoteResult.second,
            remoteCount = remoteResult.first.size
        )
        }
    suspend fun lookupBarcode(context: Context, code: String): NativeFood? = withContext(Dispatchers.IO) {
        FoodNutritionOverrideStore.attach(context)
        val digits = code.filter(Char::isDigit)
        if (!NutritionMath.isValidBarcode(digits)) return@withContext null

        val url = "https://world.openfoodfacts.org/api/v2/product/" + digits + ".json?fields=" + OFF_FIELDS
        val conn = openConnection(url)
        try {
            if (conn.responseCode in 200..299) {
                val root = conn.inputStream.bufferedReader().use { it.readText() }.let(::JSONObject)
                if (root.optInt("status", 0) == 1) {
                    val product = root.optJSONObject("product")
                    if (product != null) {
                        val retrieved = System.currentTimeMillis()
                        FoodProductCache.put(context, digits, product.toString(), retrieved)
                        return@withContext parseOpenFoodFactsProduct(product, digits, retrieved)
                            ?.let(FoodEvidenceEngine::enrich)
                            ?.let(FoodNutritionOverrideStore::applyIfAttached)
                    }
                }
            }
        } catch (_: Exception) {
            // Fall through to cached source evidence.
        } finally {
            conn.disconnect()
        }

        val cachedProduct = FoodProductCache.get(context, digits) ?: return@withContext null
        runCatching { JSONObject(cachedProduct.productJson) }
            .getOrNull()
            ?.let { parseOpenFoodFactsProduct(it, digits, cachedProduct.retrievedEpochMs) }
            ?.let(FoodEvidenceEngine::enrich)
            ?.let(FoodNutritionOverrideStore::applyIfAttached)
    }

    /**
     * Search only the tiny bundled list here. Do not call [all]: that would apply overrides to the
     * whole bundled catalogue and touch extra SQLite state before we even know which rows matched.
     * Overrides are applied once, to the small merged result set, at the end of [search].
     */
    private fun searchLocal(context: Context, query: String, limit: Int): List<NativeFood> {
        val q = query.trim().lowercase()
        val base = cached ?: loadLocal(context).also { cached = it }
        return base.asSequence()
            .map { food -> foodSearchRank(food, q) to food }
            .filter { it.first < 99 }
            .sortedWith(compareBy<Pair<Int, NativeFood>> { it.first }.thenBy { sourcePriority(it.second) }.thenBy { it.second.name })
            .take(limit)
            .map { it.second }
            .toList()
    }

    private suspend fun searchOpenFoodFacts(context: Context, query: String, limit: Int): Pair<List<NativeFood>, Boolean> = withContext(Dispatchers.IO) {
        val key = query.trim().lowercase()
        synchronized(remoteCacheLock) { remoteSearchCache[key] }?.let { return@withContext it.take(limit) to true }

        // Open Food Facts currently keeps full-text search on the v1 CGI endpoint.
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "https://world.openfoodfacts.org/cgi/search.pl?search_terms=" + encoded +
            "&search_simple=1&action=process&json=1&page_size=" + limit + "&fields=" + OFF_FIELDS
        val conn = openConnection(url)
        try {
            if (conn.responseCode !in 200..299) return@withContext emptyList<NativeFood>() to false
            val root = conn.inputStream.bufferedReader().use { it.readText() }.let(::JSONObject)
            val products = root.optJSONArray("products") ?: JSONArray()
            val parsed = buildList {
                for (i in 0 until products.length()) {
                    val p = products.optJSONObject(i) ?: continue
                    val code = p.optString("code").filter(Char::isDigit)
                    val retrieved = System.currentTimeMillis()
                    if (code.isNotBlank()) FoodProductCache.put(context, code, p.toString(), retrieved)
                    val food = parseOpenFoodFactsProduct(p, code, retrieved)
                        ?.let(FoodEvidenceEngine::enrich) ?: continue
                    if (food.name.isNotBlank()) add(food)
                }
            }
            synchronized(remoteCacheLock) { remoteSearchCache[key] = parsed }
            parsed.take(limit) to true
        } catch (_: Exception) {
            emptyList<NativeFood>() to false
        } finally {
            conn.disconnect()
        }
    }

    private fun parseOpenFoodFactsProduct(
        p: JSONObject,
        fallbackCode: String,
        retrievedEpochMs: Long = System.currentTimeMillis()
    ): NativeFood? {
        val code = p.optString("code").ifBlank { fallbackCode }.filter(Char::isDigit)
        val nutriments = p.optJSONObject("nutriments") ?: JSONObject()
        val localizedName = resolveOpenFoodFactsDisplayName(p, code)
        val name = localizedName.displayName
        if (name.isBlank()) return null

        val kcalDirectKnown = nutriments.hasNonNegativeNumber("energy-kcal_100g")
        val kjKnown = nutriments.hasNonNegativeNumber("energy-kj_100g")
        val kcal = when {
            kcalDirectKnown -> nutriments.optDoubleSafe("energy-kcal_100g")
            kjKnown -> nutriments.optDoubleSafe("energy-kj_100g") / 4.184
            else -> 0.0
        }.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val kcalKnown = kcalDirectKnown || kjKnown

        val parsedProductQuantity = FoodUnitSystem.parseBasis(p.optString("quantity"))
        val parsedServingQuantity = FoodUnitSystem.parseBasis(p.optString("serving_size"))
        val productQuantityUnit = FoodUnit.fromSymbol(p.optString("product_quantity_unit"))
            ?: parsedProductQuantity?.second
        val servingQuantityUnit = FoodUnit.fromSymbol(p.optString("serving_quantity_unit"))
            ?: parsedServingQuantity?.second
        val productQuantity = p.optNullableDouble("product_quantity")
            ?: parsedProductQuantity?.first
        val servingQuantity = p.optNullableDouble("serving_quantity")
            ?: parsedServingQuantity?.first
        val basisUnit = when {
            productQuantityUnit?.dimension == FoodMeasureDimension.VOLUME -> FoodUnit.ML
            productQuantityUnit?.dimension == FoodMeasureDimension.MASS -> FoodUnit.G
            servingQuantityUnit?.dimension == FoodMeasureDimension.VOLUME -> FoodUnit.ML
            else -> FoodUnit.G
        }
        val basis = if (basisUnit == FoodUnit.ML) "100 ml" else "100 g"
        val brand = p.optString("brands").trim()
        val country = p.optStringList("countries_tags").take(80)
        val categories = p.optString("categories").take(180)

        val proteinRaw = nutriments.optDoubleSafe("proteins_100g")
        val carbsRaw = nutriments.optDoubleSafe("carbohydrates_100g")
        val fatRaw = nutriments.optDoubleSafe("fat_100g")
        val proteinKnown = nutriments.hasNonNegativeNumber("proteins_100g")
        val carbsKnown = nutriments.hasNonNegativeNumber("carbohydrates_100g")
        val fatKnown = nutriments.hasNonNegativeNumber("fat_100g")
        val saturatedFatRaw = nutriments.optDoubleSafe("saturated-fat_100g")
        val saturatedFatKnown = nutriments.hasNonNegativeNumber("saturated-fat_100g")
        val fibreRaw = nutriments.optDoubleSafe("fiber_100g")
        val fibreKnown = nutriments.hasNonNegativeNumber("fiber_100g")
        val sugarRaw = nutriments.optDoubleSafe("sugars_100g")
        val sugarKnown = nutriments.hasNonNegativeNumber("sugars_100g")
        val saltRaw = nutriments.optDoubleSafe("salt_100g")
        val saltKnown = nutriments.hasNonNegativeNumber("salt_100g")
        val sodiumRawMg = nutriments.optDoubleSafe("sodium_100g") * 1_000.0
        val sodiumKnown = nutriments.hasNonNegativeNumber("sodium_100g")

        val macroIntegrity = NutritionIntegrity.sanitizeMacros(
            kcal = kcal,
            protein = proteinRaw,
            carbs = carbsRaw,
            fat = fatRaw,
            proteinKnown = proteinKnown,
            carbsKnown = carbsKnown,
            fatKnown = fatKnown,
            kcalKnown = kcalKnown
        )

        val integrity = NutritionIntegrity.validateFoodValues(
            basisAmount = 100.0,
            basisUnit = basisUnit,
            kcal = kcal,
            kcalKnown = kcalKnown,
            protein = macroIntegrity.protein,
            proteinKnown = macroIntegrity.proteinKnown,
            carbs = macroIntegrity.carbs,
            carbsKnown = macroIntegrity.carbsKnown,
            carbohydrateDefinition = CarbohydrateDefinition.AVAILABLE_EXCLUDING_FIBRE,
            fat = macroIntegrity.fat,
            fatKnown = macroIntegrity.fatKnown,
            saturatedFat = saturatedFatRaw,
            saturatedFatKnown = saturatedFatKnown,
            fibre = fibreRaw,
            fibreKnown = fibreKnown,
            sugar = sugarRaw,
            sugarKnown = sugarKnown,
            saltG = saltRaw,
            saltKnown = saltKnown,
            sodiumMg = sodiumRawMg,
            sodiumKnown = sodiumKnown,
            servingQuantity = servingQuantity,
            productQuantity = productQuantity
        )

        val finalSodiumMg = when {
            sodiumKnown -> sodiumRawMg
            integrity.derivedSodiumMg != null -> integrity.derivedSodiumMg
            else -> 0.0
        }
        val finalSodiumKnown = sodiumKnown || integrity.derivedSodiumMg != null
        val finalSalt = when {
            saltKnown -> saltRaw
            integrity.derivedSaltG != null -> integrity.derivedSaltG
            else -> 0.0
        }
        val finalSaltKnown = saltKnown || integrity.derivedSaltG != null

        val offWarnings = listOf(
            p.optStringList("data_quality_errors_tags"),
            p.optStringList("data_quality_warnings_tags")
        )
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { warning -> warning.isNotBlank() && isNutritionQualityWarning(warning) }
            .distinct()

        val servingWarnings = validateOffServingConsistency(
            nutriments = nutriments,
            servingQuantity = servingQuantity,
            servingUnit = servingQuantityUnit,
            productQuantity = productQuantity,
            productUnit = productQuantityUnit,
            basisUnit = basisUnit
        )

        val sourceWarnings = (offWarnings + integrity.warnings + servingWarnings + listOfNotNull(macroIntegrity.warning))
            .filter(String::isNotBlank)
            .distinct()

        val sourceRecordId = code.ifBlank { "off-name-" + name.lowercase().hashCode() }
        val nutrientEvidence = buildMap<String, NutrientEvidenceKind> {
            if (kcalKnown) put("energy_kcal", if (kcalDirectKnown) NutrientEvidenceKind.SOURCE_REPORTED else NutrientEvidenceKind.DERIVED)
            if (macroIntegrity.proteinKnown) put("protein", NutrientEvidenceKind.SOURCE_REPORTED)
            if (macroIntegrity.carbsKnown) put("carbohydrate", NutrientEvidenceKind.SOURCE_REPORTED)
            if (macroIntegrity.fatKnown) put("fat", NutrientEvidenceKind.SOURCE_REPORTED)
            if (saturatedFatKnown) put("saturated_fat", NutrientEvidenceKind.SOURCE_REPORTED)
            if (fibreKnown) put("fibre", NutrientEvidenceKind.SOURCE_REPORTED)
            if (sugarKnown) put("sugars", NutrientEvidenceKind.SOURCE_REPORTED)
            if (finalSaltKnown) put("salt", if (saltKnown) NutrientEvidenceKind.SOURCE_REPORTED else NutrientEvidenceKind.DERIVED)
            if (finalSodiumKnown) put("sodium", if (sodiumKnown) NutrientEvidenceKind.SOURCE_REPORTED else NutrientEvidenceKind.DERIVED)
        }

        val plantIdentity = PlantFoodClassifier.classify(name, categories, p.optString("ingredients_text"))

        return NativeFood(
            id = if (code.isNotBlank()) "off:$code" else "off:" + name.lowercase().hashCode(),
            name = name,
            country = country,
            kcal = kcal,
            kcalKnown = kcalKnown,
            protein = macroIntegrity.protein,
            carbs = macroIntegrity.carbs,
            carbohydrateDefinition = CarbohydrateDefinition.AVAILABLE_EXCLUDING_FIBRE,
            fat = macroIntegrity.fat,
            fibre = fibreRaw,
            sugar = sugarRaw,
            unit = basis,
            source = "Open Food Facts",
            barcode = code.ifBlank { null },
            searchText = brand + " " + categories,
            brand = brand,
            quantity = p.optString("quantity"),
            servingSize = p.optString("serving_size"),
            micronutrients = extractMicronutrients(nutriments, sourceRecordId),
            proteinKnown = macroIntegrity.proteinKnown,
            carbsKnown = macroIntegrity.carbsKnown,
            fatKnown = macroIntegrity.fatKnown,
            fibreKnown = fibreKnown,
            sugarKnown = sugarKnown,
            saturatedFat = saturatedFatRaw,
            saturatedFatKnown = saturatedFatKnown,
            salt = finalSalt,
            saltKnown = finalSaltKnown,
            sodiumMg = finalSodiumMg,
            sodiumKnown = finalSodiumKnown,
            nutritionIntegrityWarning = sourceWarnings.joinToString(" · ").ifBlank { null },
            originalName = localizedName.originalName,
            displayLanguage = localizedName.displayLanguage,
            hasVerifiedEnglishName = localizedName.hasVerifiedEnglishName,
            basisAmount = 100.0,
            basisUnit = basisUnit,
            productQuantity = productQuantity,
            productQuantityUnit = productQuantityUnit,
            servingQuantity = servingQuantity,
            servingQuantityUnit = servingQuantityUnit,
            servingLabel = p.optString("serving_size"),
            identityKind = FoodIdentityKind.BRANDED_PRODUCT,
            sourceType = FoodDataSourceType.OPEN_FOOD_FACTS,
            sourceRecordId = sourceRecordId,
            sourceRevision = p.optString("last_modified_datetime").ifBlank { p.optString("last_modified_t") },
            lastRetrievedEpochMs = retrievedEpochMs,
            preparationState = FoodEvidenceEngine.inferPreparationState(name + " " + categories),
            energyEvidence = when {
                kcalDirectKnown -> EnergyEvidenceKind.REPORTED_KCAL
                kjKnown -> EnergyEvidenceKind.CONVERTED_KJ
                else -> EnergyEvidenceKind.UNKNOWN
            },
            nutrientEvidence = nutrientEvidence,
            ingredientsText = p.optString("ingredients_text"),
            allergens = p.optTagList("allergens_tags"),
            additives = p.optTagList("additives_tags"),
            novaGroup = p.optInt("nova_group", 0).takeIf { it in 1..4 },
            imageReferences = buildMap {
                p.optString("image_url").takeIf(String::isNotBlank)?.let { put("product", it) }
                p.optString("image_front_url").takeIf(String::isNotBlank)?.let { put("front", it) }
                p.optString("image_nutrition_url").takeIf(String::isNotBlank)?.let { put("nutrition", it) }
                p.optString("image_ingredients_url").takeIf(String::isNotBlank)?.let { put("ingredients", it) }
            },
            sourceWarnings = sourceWarnings,
            isPlantFood = plantIdentity.isPlantFood,
            plantFoodKind = plantIdentity.kind,
            plantDiversityKey = plantIdentity.diversityKey
        )
    }

    private data class LocalizedProductName(
        val displayName: String,
        val originalName: String,
        val displayLanguage: String,
        val hasVerifiedEnglishName: Boolean
    )

    /**
     * Prefer Open Food Facts' own language-specific English fields instead of machine translating.
     * Product/brand names with no verified English label remain in the packaging language so we
     * never invent awkward or misleading translations.
     */
    private fun resolveOpenFoodFactsDisplayName(p: JSONObject, code: String): LocalizedProductName {
        val brand = p.optString("brands").trim()
        val mainLanguage = p.optString("lang").trim().lowercase()
        val original = p.optString("product_name").trim()
            .ifBlank { p.optString("generic_name").trim() }
            .ifBlank { brand }
            .ifBlank { if (code.isNotBlank()) "Product $code" else "" }

        val english = p.optString("product_name_en").trim()
            .ifBlank { p.optString("generic_name_en").trim() }

        if (english.isNotBlank()) {
            return LocalizedProductName(
                displayName = english,
                originalName = original,
                displayLanguage = "en",
                hasVerifiedEnglishName = true
            )
        }

        // For any non-English source language, keep the authentic packaging name when OFF does
        // not provide an explicit English field. This applies equally to Polish, French, German,
        // Spanish, Italian, Japanese, Korean and every other supported source language.
        // Correct product identity beats a guessed machine translation.
        return LocalizedProductName(
            displayName = original,
            originalName = original,
            displayLanguage = mainLanguage.ifBlank { "source" },
            hasVerifiedEnglishName = false
        )
    }

    private fun extractMicronutrients(n: JSONObject, sourceRecordId: String = ""): Map<String, NativeNutrient> {
        val out = linkedMapOf<String, NativeNutrient>()
        micronutrientSpecs.forEach { spec ->
            // Several OFF taxonomy ids can map to one canonical nutrient (e.g. folate).
            if (out.containsKey(spec.id)) return@forEach
            val key = "${spec.offId}_100g"
            if (!n.hasFiniteNumber(key)) return@forEach
            val grams = n.optDoubleSafe(key)
            if (grams < 0.0 || !grams.isFinite()) return@forEach
            val converted = grams * spec.multiplierFromGrams
            if (converted < 0.0 || !converted.isFinite()) return@forEach
            out[spec.id] = NativeNutrient(
                id = spec.id,
                label = spec.label,
                valuePer100 = converted,
                unit = spec.unit,
                evidenceKind = NutrientEvidenceKind.SOURCE_REPORTED,
                source = "Open Food Facts",
                sourceRecordId = sourceRecordId
            )
        }
        if (!out.containsKey("sodium")) {
            val saltKey = "salt_100g"
            if (n.hasFiniteNumber(saltKey)) {
                val saltGrams = n.optDoubleSafe(saltKey)
                if (saltGrams.isFinite() && saltGrams >= 0.0) {
                    out["sodium"] = NativeNutrient(
                        id = "sodium",
                        label = "Sodium",
                        valuePer100 = NutritionIntegrity.saltGToSodiumMg(saltGrams),
                        unit = "mg",
                        evidenceKind = NutrientEvidenceKind.DERIVED,
                        source = "Open Food Facts",
                        sourceRecordId = sourceRecordId,
                        derivedFrom = "salt"
                    )
                }
            }
        }
        return out
    }

    private fun validateOffServingConsistency(
        nutriments: JSONObject,
        servingQuantity: Double?,
        servingUnit: FoodUnit?,
        productQuantity: Double?,
        productUnit: FoodUnit?,
        basisUnit: FoodUnit
    ): List<String> {
        val q = servingQuantity?.takeIf { it.isFinite() && it > 0.0 } ?: return emptyList()
        val unit = servingUnit ?: return emptyList()
        if (unit.dimension != basisUnit.dimension || unit.dimension == FoodMeasureDimension.DERIVED) {
            return emptyList()
        }

        val basisQuantity = q * unit.toBase / basisUnit.toBase
        val factor = basisQuantity / 100.0
        val warnings = mutableListOf<String>()
        val pairs = listOf(
            Triple("energy-kcal_100g", "energy-kcal_serving", 2.0),
            Triple("proteins_100g", "proteins_serving", 0.2),
            Triple("carbohydrates_100g", "carbohydrates_serving", 0.2),
            Triple("fat_100g", "fat_serving", 0.2),
            Triple("sugars_100g", "sugars_serving", 0.2)
        )
        pairs.forEach { (per100Key, servingKey, absoluteTolerance) ->
            if (!nutriments.hasNonNegativeNumber(per100Key) || !nutriments.hasNonNegativeNumber(servingKey)) {
                return@forEach
            }
            val expected = nutriments.optDoubleSafe(per100Key) * factor
            val reported = nutriments.optDoubleSafe(servingKey)
            val tolerance = maxOf(absoluteTolerance, expected * 0.15)
            if (kotlin.math.abs(reported - expected) > tolerance) {
                warnings += "Serving nutrition disagrees with per-100 basis"
            }
        }

        if (productQuantity != null && productUnit != null &&
            productUnit.dimension == unit.dimension &&
            productUnit.dimension != FoodMeasureDimension.DERIVED
        ) {
            val packageBase = productQuantity * productUnit.toBase
            val servingBase = q * unit.toBase
            if (servingBase > packageBase * 1.05) {
                warnings += "Serving quantity exceeds package quantity"
            }
        }
        return warnings.distinct()
    }

    private fun isNutritionQualityWarning(raw: String): Boolean {
        val warning = raw.lowercase()
        return listOf(
            "nutrition", "nutrient", "energy", "kcal", "kj", "calorie",
            "protein", "carbohydrate", "sugar", "fat", "fiber", "fibre",
            "salt", "sodium", "serving"
        ).any { it in warning }
    }

    private fun normalizeFoodTokens(value: String): List<String> =
        value.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .split(' ')
            .filter { it.length > 2 }

    private fun foodComparator(query: String): Comparator<NativeFood> =
        compareBy<NativeFood> { foodSearchRank(it, query) }
            .thenBy { if (it.id.startsWith("core:") && it.micronutrients.isNotEmpty()) 0 else 1 }
            .thenBy { if (it.nutritionIntegrityWarning.isNullOrBlank()) 0 else 1 }
            .thenBy { if (it.nutritionApproximate) 1 else 0 }
            .thenBy(::sourcePriority)
            .thenByDescending { it.micronutrients.size }
            .thenBy { it.name.length }
            .thenBy { it.name.lowercase() }

    /** Evidence quality breaks ties after exact identity and integrity state. */
    private fun sourcePriority(food: NativeFood): Int = FoodEvidenceEngine.sourcePriority(food)

    private fun foodSearchRank(food: NativeFood, query: String): Int {
        val q = query.trim().lowercase()
        if (q.length < 2) return 99

        val normalizeTokens: (String) -> List<String> = { value ->
            value.lowercase()
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()
                .split(' ')
                .filter { it.isNotBlank() }
        }
        val queryTokens = normalizeTokens(q)
        val name = food.name.lowercase()
        val nameTokens = normalizeTokens(food.name)
        val haystack = (
            food.name + " " + food.originalName + " " + food.searchText + " " +
                food.country + " " + food.brand
            ).lowercase()
        val haystackTokens = normalizeTokens(haystack)

        return when {
            name == q -> 0
            food.id.startsWith("alias:") && queryTokens.all { token -> token in nameTokens } -> 0
            name.startsWith(q) -> 1
            queryTokens.isNotEmpty() && queryTokens.all { token -> nameTokens.any { it.startsWith(token) } } -> 1
            queryTokens.isNotEmpty() && queryTokens.all { token -> token in haystackTokens } -> 2
            nameTokens.any { token -> token.startsWith(q) } -> 2
            haystack.contains(q) -> 3
            else -> 99
        }
    }

    private fun loadLocal(context: Context): List<NativeFood> {
        val raw = context.assets.open("food_db.js").bufferedReader().use { it.readText() }
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val arr = JSONArray(raw.substring(start, end + 1))
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val j = arr.optJSONObject(i) ?: continue
                val localName = j.optString("name", "Food")
                val localSearchText = j.optString("salt") + " " + j.optString("aliases") + " " + j.optString("brand")
                val plantIdentity = PlantFoodClassifier.classify(localName, localSearchText)
                add(
                    NativeFood(
                        id = j.optString("id", "local:$i"),
                        name = localName,
                        country = j.optString("country", ""),
                        kcal = j.optDoubleSafe("kcal"),
                        kcalKnown = j.hasNonNegativeNumber("kcal"),
                        protein = j.optDoubleSafe("protein"),
                        carbs = j.optDoubleSafe("carbs"),
                        fat = j.optDoubleSafe("fat"),
                        fibre = j.optDoubleSafe("fibre"),
                        sugar = j.optDoubleSafe("sugar"),
                        proteinKnown = j.hasNonNegativeNumber("protein"),
                        carbsKnown = j.hasNonNegativeNumber("carbs"),
                        fatKnown = j.hasNonNegativeNumber("fat"),
                        fibreKnown = j.hasNonNegativeNumber("fibre"),
                        sugarKnown = j.hasNonNegativeNumber("sugar"),
                        unit = j.optString("unit", "100 g"),
                        source = j.optString("source", "Project Superhuman reference"),
                        searchText = localSearchText,
                        brand = j.optString("brand", ""),
                        basisAmount = FoodUnitSystem.parseBasis(j.optString("unit", "100 g"))?.first ?: 100.0,
                        basisUnit = FoodUnitSystem.parseBasis(j.optString("unit", "100 g"))?.second ?: FoodUnit.G,
                        densityGPerMl = j.optNullableDouble("density_g_ml"),
                        densityApproximate = j.optBoolean("density_approx", false),
                        nutritionApproximate = j.optBoolean("approx", false),
                        isPlantFood = plantIdentity.isPlantFood,
                        plantFoodKind = plantIdentity.kind,
                        plantDiversityKey = plantIdentity.diversityKey
                    )
                )
            }
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3_000
            readTimeout = 4_500
            requestMethod = "GET"
            useCaches = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val value = opt(key) ?: return null
        val parsed = when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().replace(',', '.').toDoubleOrNull()
            else -> null
        }
        return parsed?.takeIf { it.isFinite() && it > 0.0 }
    }

    private fun JSONObject.hasNonNegativeNumber(key: String): Boolean {
        if (!has(key) || isNull(key)) return false
        val value = opt(key) ?: return false
        val parsed = when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().replace(',', '.').toDoubleOrNull()
            else -> null
        }
        return parsed?.let { it.isFinite() && it >= 0.0 } == true
    }

    private fun JSONObject.hasFiniteNumber(key: String): Boolean {
        if (!has(key) || isNull(key)) return false
        val value = opt(key) ?: return false
        val parsed = when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().replace(',', '.').toDoubleOrNull()
            else -> null
        }
        return parsed?.isFinite() == true
    }

    private fun JSONObject.optTagList(key: String): List<String> {
        val value = opt(key) ?: return emptyList()
        return when (value) {
            is JSONArray -> buildList {
                for (i in 0 until value.length()) {
                    value.optString(i)
                        .substringAfter(':')
                        .trim()
                        .takeIf(String::isNotBlank)
                        ?.let(::add)
                }
            }
            is String -> value.split(',').map(String::trim).filter(String::isNotBlank)
            else -> emptyList()
        }
    }

    private fun JSONObject.optDoubleSafe(key: String): Double {
        val value = opt(key) ?: return 0.0
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    private fun JSONObject.optStringList(key: String): String {
        val value = opt(key) ?: return ""
        return when (value) {
            is JSONArray -> buildList {
                for (i in 0 until value.length()) value.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }.joinToString(", ") { it.substringAfter(':').replace('-', ' ') }
            is String -> value
            else -> value.toString()
        }
    }
}
