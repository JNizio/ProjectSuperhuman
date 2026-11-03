package com.projectsuperhuman.next

import kotlin.math.abs
import kotlin.math.max

/**
 * Uses only already-imported, traceable USDA food-composition records to supplement
 * the small bundled generic catalogue. No guessed nutrient constants or product-label
 * values are introduced. A missing/ambiguous match remains unknown.
 *
 * This is intentionally conservative: ready meals, branded-style products and
 * recipes do not inherit the profile of one of their ingredients.
 */
internal object BundledFoodMicronutrientMatcher {
    // These are single foods or well-defined generic staples, not arbitrary recipes.
    // Excluding mixed dishes is essential: there is no accurate universal micronutrient
    // profile for e.g. pierogi, bigos, pizza or a generic protein bar.
    private val eligibleIds: Set<String> =
        ((1..98).filterNot { it in setOf(27, 28, 34, 55, 60, 86, 87) } +
            listOf(114, 115, 116, 119, 120, 123, 124, 128, 129, 130,
                131, 132, 133, 141, 143, 144, 145, 146, 151))
            .mapTo(mutableSetOf()) { "f$it" }

    private val ignoredWords = setOf(
        "a", "and", "the", "with", "without", "of", "or", "in", "for", "plain",
        "typical", "natural", "fresh", "whole", "skinless", "skin", "raw",
        "cooked", "boiled", "baked", "roasted", "steamed", "grilled",
        "drained", "canned", "dry", "dried", "frozen", "low", "fat", "lowfat",
        "high", "reduced", "skimmed", "semi", "semi-skimmed", "oven",
        "spring", "water", "unsweetened", "seed", "seeds", "firm", "flesh",
        "including", "only", "meat", "no", "percent"
    )
    private val preparationWords = setOf(
        "raw", "boiled", "baked", "roasted", "grilled", "fried",
        "steamed", "cooked", "smoked", "canned", "dried", "frozen"
    )
    private val identityChangingWords = setOf(
        "juice", "butter", "oil", "flour", "powder", "puree", "concentrate",
        "milk", "yogurt", "cheese", "bread", "cereal", "rice", "pasta", "soy",
        "peanut", "almond", "cashew", "shiitake", "portabella", "portobello",
        "oyster", "chanterelle", "porcini", "honey"
    )
    private val additiveWords = setOf(
        "sweetened", "syrup", "fortified", "enriched", "breaded", "battered",
        "sauce", "flavored", "flavoured", "chocolate", "dessert", "pudding",
        "pie", "cake", "salad", "soup", "casserole", "sandwich"
    )

    private fun tokens(name: String): Set<String> {
        val normalized = name.lowercase()
            .replace("yoghurt", "yogurt")
            .replace("prawns", "shrimp")
            .replace("rapeseed", "canola")
            .replace("mince", "ground")
            .replace("wholemeal", "wholewheat")
            .replace("wholewheat", "whole wheat")
            .replace("semi-skimmed", "reduced fat")
            .replace("tomatoes", "tomato")
            .replace("potatoes", "potato")
            .replace("strawberries", "strawberry")
            .replace("blueberries", "blueberry")
            .replace("raspberries", "raspberry")
            .replace("avocados", "avocado")
            .replace("almonds", "almond")
            .replace("walnuts", "walnut")
            .replace("cashews", "cashew")
            .replace("mushrooms", "mushroom")
            .replace("onions", "onion")
            .replace("carrots", "carrot")
            .replace("lentils", "lentil")
            .replace("chickpeas", "chickpea")
            .replace("beans", "bean")
            .replace("creams", "cream")
            .replace("bananas", "banana")
            .replace("apples", "apple")
            .replace("pears", "pear")
            .replace("grapes", "grape")
            .replace("oranges", "orange")
            .replace("peppers", "pepper")
            .replace(Regex("[^a-z0-9]+"), " ")
        return normalized.split(' ').filter { it.length > 1 }.toSet()
    }

    private fun preparationCompatible(local: Set<String>, reference: Set<String>): Boolean {
        val explicit = local.intersect(preparationWords)
        if (explicit.isNotEmpty()) {
            // "Cooked" can be supplied as an actual cooking method in USDA descriptors.
            val cookedMethods = setOf("boiled", "baked", "roasted", "grilled", "fried", "steamed")
            if ("cooked" in explicit) {
                if ("raw" in reference || (reference intersect (cookedMethods + "cooked")).isEmpty()) return false
            }
            for (word in explicit - "cooked") {
                if (word !in reference && !(word == "baked" && "roasted" in reference)) return false
            }
        } else {
            // Unqualified foods must not silently inherit cooked/processed variants.
            if (reference.any { it in preparationWords && it != "raw" }) return false
        }
        if ("raw" in local && "raw" !in reference) return false
        return true
    }

    private fun macroCompatible(local: NativeFood, reference: NativeFood): Boolean {
        if (local.kcalKnown && reference.kcalKnown &&
            abs(local.kcal - reference.kcal) > max(25.0, local.kcal * .25)) return false
        if (local.proteinKnown && reference.proteinKnown &&
            abs(local.protein - reference.protein) > max(3.0, local.protein * .42)) return false
        if (local.fatKnown && reference.fatKnown &&
            abs(local.fat - reference.fat) > max(1.2, local.fat * .42)) return false
        return true
    }

    private fun matchScore(local: NativeFood, reference: NativeFood): Int? {
        if (reference.micronutrients.size < 6 || reference.sourceRecordId.isBlank()) return null
        if (!reference.source.startsWith("USDA ")) return null
        val localWords = tokens(local.name)
        val sourceWords = tokens(reference.originalName.ifBlank { reference.name })
        val coreWords = localWords - ignoredWords
        if (coreWords.isEmpty() || !coreWords.all(sourceWords::contains)) return null
        if (!preparationCompatible(localWords, sourceWords)) return null
        if ((sourceWords intersect (additiveWords - localWords)).isNotEmpty()) return null
        if ((sourceWords intersect (identityChangingWords - localWords)).isNotEmpty()) return null
        if (!macroCompatible(local, reference)) return null
        val sourcePriority = when {
            reference.source.contains("Foundation") -> 30
            reference.source.contains("SR Legacy") -> 20
            else -> 10
        }
        val extraWords = (sourceWords - coreWords - ignoredWords).size
        return sourcePriority + reference.micronutrients.size.coerceAtMost(25) - extraWords * 2
    }

    /** A short search term for source lookup (e.g. "rice", not "white"). */
    fun referenceSearchTerm(local: NativeFood): String {
        val adjectives = setOf("white", "brown", "greek", "red", "green", "lean",
            "low", "plain", "raw", "cooked", "baked", "whole", "wholewheat", "smoked")
        return tokens(local.name).firstOrNull { it !in ignoredWords && it !in adjectives }
            ?: local.name
    }

    fun isEligible(local: NativeFood): Boolean =
        local.id in eligibleIds && local.micronutrients.isEmpty()

    /** Enrich only with an unambiguous, same-preparation USDA source candidate. */
    fun enrich(local: NativeFood, sources: List<NativeFood>): NativeFood {
        if (local.id !in eligibleIds || local.micronutrients.isNotEmpty()) return local
        val compatible = sources.mapNotNull { source ->
            matchScore(local, source)?.let { score -> source to score }
        }.sortedByDescending { it.second }
        if (compatible.isEmpty()) return local
        val best = compatible.first()
        // Competing references with similar scores but different composition are ambiguous.
        val runnerUp = compatible.getOrNull(1)
        if (runnerUp != null && runnerUp.second == best.second) {
            val veryDifferentEnergy =
                abs(runnerUp.first.kcal - best.first.kcal) > max(15.0, best.first.kcal * .10)
            val sharedNutrients = best.first.micronutrients.keys intersect runnerUp.first.micronutrients.keys
            val materiallyDifferentMicros = sharedNutrients.count { id ->
                val a = best.first.micronutrients.getValue(id).valuePer100
                val b = runnerUp.first.micronutrients.getValue(id).valuePer100
                val larger = max(a, b)
                larger >= 0.01 && abs(a - b) / larger > .40
            } >= 3
            if (veryDifferentEnergy || materiallyDifferentMicros) return local
        }

        val source = best.first
        // Bundled milk/juice may be per 100 ml, whereas USDA values are per 100 g.
        val scale = when (local.basisUnit) {
            FoodUnit.G -> 1.0
            FoodUnit.ML -> local.densityGPerMl?.takeIf { it in .8..1.5 } ?: return local
            else -> return local
        }
        val referenceName = source.originalName.ifBlank { source.name }
        val micros = source.micronutrients.mapValues { (_, n) ->
            n.copy(
                valuePer100 = n.valuePer100 * scale,
                evidenceKind = NutrientEvidenceKind.DERIVED,
                source = source.source,
                sourceRecordId = source.sourceRecordId,
                derivedFrom = "Representative USDA food profile: $referenceName"
            )
        }
        return local.copy(
            micronutrients = micros,
            nutrientEvidence = local.nutrientEvidence +
                micros.keys.associateWith { NutrientEvidenceKind.DERIVED },
            sourceWarnings = local.sourceWarnings +
                "Micronutrients use a matched USDA reference ($referenceName); values are " +
                "representative estimates, not an analysis of this specific food."
        )
    }
}
