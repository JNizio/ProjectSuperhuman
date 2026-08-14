package com.projectsuperhuman.next.trudy.nutrition

/**
 * Curated presentation/reasoning knowledge. It contains no personal data and no persistence.
 * Personal conclusions must be produced by the existing evidence and tool layers.
 */
class CuratedNutritionBodyKnowledgeRepository : TrudyNutritionKnowledgeProvider {
    private val sourceCatalog = buildSources()
    private val sourceById = sourceCatalog.associateBy { it.id }
    private val topicCatalog = buildTopics()
    private val topicById = topicCatalog.associateBy { it.id }
    private val nutrientCatalog = buildNutrients()
    private val nutrientTerms = buildMap {
        nutrientCatalog.forEach { nutrient ->
            (nutrient.aliases + nutrient.id + nutrient.displayName).forEach { term ->
                put(normalize(term), nutrient)
            }
        }
    }
    private val foods = buildFoodTerms()

    init {
        require(sourceCatalog.map { it.id }.distinct().size == sourceCatalog.size)
        require(topicCatalog.map { it.id }.distinct().size == topicCatalog.size)
        require(nutrientCatalog.map { it.id }.distinct().size == nutrientCatalog.size)
        val knownSources = sourceById.keys
        require((topicCatalog.flatMap { it.sourceIds } + nutrientCatalog.flatMap { it.sourceIds }).all { it in knownSources }) {
            "Every curated nutrition statement must resolve to source provenance"
        }
    }

    override val nutrientCount: Int get() = nutrientCatalog.size

    override fun sources(): List<NutritionKnowledgeSource> = sourceCatalog

    override fun topic(id: String): NutritionTopic? = topicById[normalize(id).replace(' ', '_')]

    override fun nutrient(idOrAlias: String): NutrientReference? = nutrientTerms[normalize(idOrAlias)]

    override fun searchNutrients(query: String, limit: Int): List<NutrientReference> {
        require(limit in 1..32)
        val normalized = normalize(query)
        if (normalized.isBlank()) return emptyList()
        return nutrientCatalog.mapNotNull { nutrient ->
            val terms = nutrient.aliases + nutrient.id.replace('_', ' ') + nutrient.displayName
            val score = terms.maxOfOrNull { term ->
                val candidate = normalize(term)
                when {
                    normalized == candidate -> 4
                    containsPhrase(normalized, candidate) -> 3
                    candidate.length >= 4 && containsPhrase(candidate, normalized) -> 2
                    candidate.split(' ').any { it.length >= 4 && it in normalized.split(' ') } -> 1
                    else -> 0
                }
            } ?: 0
            if (score == 0) null else score to nutrient
        }.sortedWith(compareByDescending<Pair<Int, NutrientReference>> { it.first }.thenBy { it.second.displayName })
            .take(limit)
            .map { it.second }
    }

    override fun resolveFood(text: String, localeHint: FoodLocale?): FoodTermResolution {
        val query = normalize(text)
        if (query.isBlank()) return FoodTermResolution.NotFound
        val matches = foods.flatMap { food ->
            food.aliases.mapNotNull { alias ->
                val phrase = normalize(alias.phrase)
                val localeMatches = alias.locales.isEmpty() || localeHint == null || localeHint in alias.locales
                if (localeMatches && containsPhrase(query, phrase)) Triple(phrase.length, food, alias) else null
            }
        }
        if (matches.isEmpty()) return FoodTermResolution.NotFound
        val bestLength = matches.maxOf { it.first }
        val best = matches.filter { it.first == bestLength }
        val localeSpecific = if (localeHint == null) best else best.filter { it.third.locales.isEmpty() || localeHint in it.third.locales }
        val candidates = localeSpecific.map { it.second }.distinctBy { it.id }
        val phrase = localeSpecific.firstOrNull()?.third?.phrase ?: best.first().third.phrase
        return when (candidates.size) {
            0 -> FoodTermResolution.NotFound
            1 -> FoodTermResolution.Resolved(candidates.single(), phrase)
            else -> FoodTermResolution.Ambiguous(candidates, phrase)
        }
    }

    override fun resolveIntent(question: String): NutritionQuestionIntent {
        val q = normalize(question)
        return when {
            hasAny(q, "weight jump overnight", "weight jumped overnight", "overnight weight", "gained overnight", "scale jumped") ->
                NutritionQuestionIntent.OVERNIGHT_WEIGHT_CHANGE
            hasAny(q, "body composition", "body fat", "muscle mass", "fat free mass", "lean mass", "smart scale") ->
                NutritionQuestionIntent.BODY_COMPOSITION_CHANGE
            hasAny(q, "enough protein", "protein intake", "protein target", "need more protein", "eating protein") ->
                NutritionQuestionIntent.PROTEIN_ADEQUACY
            hasAny(q, "drinking enough", "enough water", "hydrated", "hydration", "fluid intake", "dehydrated") ->
                NutritionQuestionIntent.HYDRATION_ADEQUACY
            hasAny(q, "nutrients might i be low", "nutrient gaps", "low in nutrients", "missing nutrients", "vitamin deficiency", "mineral deficiency") ->
                NutritionQuestionIntent.DIETARY_NUTRIENT_GAP
            searchNutrients(q, 1).isNotEmpty() && hasAny(q, "low", "deficient", "deficiency", "enough", "getting") ->
                NutritionQuestionIntent.DIETARY_NUTRIENT_GAP
            hasAny(q, "recovery meal", "after exercise", "before exercise", "pre workout", "post workout", "fuel training", "exercise nutrition") ->
                NutritionQuestionIntent.EXERCISE_NUTRITION
            hasAny(q, "food affecting my energy", "food be affecting my energy", "food affect my energy", "meal affecting energy", "after eating tired", "energy after meals") ->
                NutritionQuestionIntent.FOOD_AND_ENERGY
            hasAny(q, "energy balance", "calorie balance", "calorie deficit", "calorie surplus", "metabolism", "maintenance calories") ->
                NutritionQuestionIntent.ENERGY_BALANCE
            else -> NutritionQuestionIntent.GENERAL_NUTRITION
        }
    }

    override fun contextFor(question: String, localeHint: FoodLocale?): NutritionKnowledgeContext {
        require(question.isNotBlank())
        val intent = resolveIntent(question)
        val topics = topicIdsFor(intent).mapNotNull(topicById::get)
        val nutrients = searchNutrients(question)
        val sourceIds = (topics.flatMap { it.sourceIds } + nutrients.flatMap { it.sourceIds }).toSet()
        return NutritionKnowledgeContext(
            question = question,
            intent = intent,
            topics = topics,
            nutrients = nutrients,
            foodResolution = resolveFood(question, localeHint),
            metricBindings = TrudyNutritionMetricCatalog.forIntent(intent),
            sources = sourceIds.mapNotNull(sourceById::get),
            safetyInstructions = TrudyNutritionSafetyPolicy.constraints
        )
    }

    private fun topicIdsFor(intent: NutritionQuestionIntent): List<String> = when (intent) {
        NutritionQuestionIntent.PROTEIN_ADEQUACY -> listOf("protein", "energy_balance", "exercise_nutrition")
        NutritionQuestionIntent.HYDRATION_ADEQUACY -> listOf("hydration", "electrolytes")
        NutritionQuestionIntent.OVERNIGHT_WEIGHT_CHANGE -> listOf("short_term_weight", "body_composition_measurement", "energy_balance")
        NutritionQuestionIntent.BODY_COMPOSITION_CHANGE -> listOf("body_composition_measurement", "weight_trends")
        NutritionQuestionIntent.ENERGY_BALANCE -> listOf("energy_balance", "metabolism", "energy_density_and_satiety")
        NutritionQuestionIntent.FOOD_AND_ENERGY -> listOf("meal_composition", "glycaemic_context", "meal_timing")
        NutritionQuestionIntent.DIETARY_NUTRIENT_GAP -> listOf("vitamins_and_minerals", "dietary_patterns")
        NutritionQuestionIntent.EXERCISE_NUTRITION -> listOf("exercise_nutrition", "recovery_nutrition", "hydration")
        NutritionQuestionIntent.GENERAL_NUTRITION -> listOf("dietary_patterns", "energy_balance", "macronutrients")
    }

    private fun buildSources(): List<NutritionKnowledgeSource> = listOf(
        source("who_healthy_diet", "Healthy diet", "World Health Organization", "https://www.who.int/news-room/fact-sheets/detail/healthy-diet", NutritionEvidenceType.PUBLIC_HEALTH_GUIDANCE, "2026-01-26"),
        source("nhs_eatwell", "The Eatwell Guide", "NHS", "https://www.nhs.uk/live-well/eat-well/food-guidelines-and-food-labels/the-eatwell-guide/", NutritionEvidenceType.GOVERNMENT_GUIDANCE),
        source("nhs_hydration", "Water, drinks and hydration", "NHS", "https://www.nhs.uk/live-well/eat-well/food-guidelines-and-food-labels/water-drinks-nutrition/", NutritionEvidenceType.GOVERNMENT_GUIDANCE),
        source("cdc_heat_hydration", "Heat and Cold Illness in Travelers", "US Centers for Disease Control and Prevention", "https://www.cdc.gov/yellow-book/hcp/environmental-hazards-risks/heat-and-cold-illness-in-travelers.html", NutritionEvidenceType.PUBLIC_HEALTH_GUIDANCE),
        source("efsa_drv", "Dietary reference values", "European Food Safety Authority", "https://www.efsa.europa.eu/en/topics/topic/dietary-reference-values", NutritionEvidenceType.PUBLIC_HEALTH_GUIDANCE, "2024-08-05"),
        source("acsm_sports_nutrition", "Nutrition and Athletic Performance", "Academy of Nutrition and Dietetics, Dietitians of Canada, and American College of Sports Medicine", "https://pubmed.ncbi.nlm.nih.gov/26920240/", NutritionEvidenceType.PROFESSIONAL_POSITION, "2016"),
        source("uclh_glycaemic_index", "What is the Glycaemic Index", "University College London Hospitals NHS Foundation Trust", "https://www.uclh.nhs.uk/patients-and-visitors/patient-information-pages/what-glycaemic-index", NutritionEvidenceType.GOVERNMENT_GUIDANCE),
        source("niddk_body_weight", "About the Body Weight Planner", "National Institute of Diabetes and Digestive and Kidney Diseases", "https://www.niddk.nih.gov/health-information/weight-management/body-weight-planner", NutritionEvidenceType.GOVERNMENT_GUIDANCE, "2017-05"),
        source("bia_validity_review", "The validity of bioelectrical impedance models in clinical populations", "Clinical Nutrition / PubMed", "https://pubmed.ncbi.nlm.nih.gov/16215137/", NutritionEvidenceType.PEER_REVIEWED_RESEARCH, "2005"),
        source("glycogen_water_study", "Relationship between muscle water and glycogen recovery after prolonged exercise in the heat in humans", "European Journal of Applied Physiology / PubMed", "https://pubmed.ncbi.nlm.nih.gov/25911631/", NutritionEvidenceType.PEER_REVIEWED_RESEARCH, "2015"),
        ods("calcium", "Calcium", "Calcium"),
        ods("copper", "Copper", "Copper"),
        ods("iron", "Iron", "Iron"),
        ods("iodine", "Iodine", "Iodine"),
        ods("magnesium", "Magnesium", "Magnesium"),
        ods("manganese", "Manganese", "Manganese"),
        ods("phosphorus", "Phosphorus", "Phosphorus"),
        ods("potassium", "Potassium", "Potassium"),
        ods("selenium", "Selenium", "Selenium"),
        ods("zinc", "Zinc", "Zinc"),
        ods("vitamin_a", "Vitamin A and Carotenoids", "VitaminA"),
        ods("vitamin_b1", "Thiamin", "Thiamin"),
        ods("vitamin_b2", "Riboflavin", "Riboflavin"),
        ods("niacin", "Niacin", "Niacin"),
        ods("pantothenic_acid", "Pantothenic Acid", "PantothenicAcid"),
        ods("vitamin_b6", "Vitamin B6", "VitaminB6"),
        ods("folate", "Folate", "Folate"),
        ods("vitamin_b12", "Vitamin B12", "VitaminB12"),
        ods("biotin", "Biotin", "Biotin"),
        ods("vitamin_c", "Vitamin C", "VitaminC"),
        ods("vitamin_d", "Vitamin D", "VitaminD"),
        ods("vitamin_e", "Vitamin E", "VitaminE"),
        ods("vitamin_k", "Vitamin K", "VitaminK"),
        ods("choline", "Choline", "Choline"),
        ods("omega_3", "Omega-3 Fatty Acids", "Omega3FattyAcids")
    )

    private fun buildTopics(): List<NutritionTopic> = listOf(
        topic("energy_balance", "Energy balance", NutritionKnowledgeArea.ENERGY_AND_METABOLISM, setOf("calories", "calorie balance", "energy intake"), "Body energy stores change through the relationship between intake and expenditure over time, not from one isolated day.", listOf("Use multi-day intake, activity and body-weight trends.", "Wearable expenditure and food logging are estimates with different error sources."), listOf("Do not turn an estimated deficit or surplus into a diagnosis or a precise prediction."), "who_healthy_diet", "niddk_body_weight"),
        topic("metabolism", "Metabolism", NutritionKnowledgeArea.ENERGY_AND_METABOLISM, setOf("metabolic rate", "maintenance calories", "tdee"), "Metabolism includes the chemical processes that sustain the body; daily expenditure also reflects resting needs, activity and the thermic effect of food.", listOf("Treat calculated expenditure as a starting estimate and calibrate it against a stable weight trend."), listOf("A short plateau does not prove a damaged or unusually slow metabolism."), "niddk_body_weight"),
        topic("macronutrients", "Macronutrients", NutritionKnowledgeArea.MACRONUTRIENTS, setOf("macros", "protein carbs fat"), "Protein, carbohydrate and fat provide energy or structural substrates, while fibre supports gastrointestinal and metabolic health.", listOf("Interpret macros within total energy, food quality, preference, training and clinical context."), listOf("There is no single ideal macro split for every person."), "who_healthy_diet", "efsa_drv"),
        topic("protein", "Protein", NutritionKnowledgeArea.MACRONUTRIENTS, setOf("protein intake", "amino acids"), "Protein supplies amino acids used in tissue maintenance, enzymes, hormones and adaptation to training.", listOf("Assess logged intake across several representative days.", "Consider body size, training, energy intake, age and dietary pattern."), listOf("A food log cannot identify kidney function or justify extreme intake."), "who_healthy_diet", "acsm_sports_nutrition"),
        topic("hydration", "Hydration", NutritionKnowledgeArea.HYDRATION_AND_ELECTROLYTES, setOf("water", "fluid intake", "drinking"), "Fluid needs vary with food water, activity, heat, sweating, illness, pregnancy and individual physiology.", listOf("Use intake patterns with thirst, conditions and activity rather than one universal target.", "Tea, coffee, milk and other suitable drinks can contribute to fluid intake."), listOf("Forced intake can be harmful; medical fluid restrictions override general guidance."), "nhs_hydration", "cdc_heat_hydration"),
        topic("electrolytes", "Electrolytes", NutritionKnowledgeArea.HYDRATION_AND_ELECTROLYTES, setOf("sodium potassium", "salts", "sweat minerals"), "Sodium, potassium, chloride and other ions support fluid balance, nerve signalling and muscle function.", listOf("Electrolyte relevance rises with prolonged heavy sweating, heat or gastrointestinal losses."), listOf("More electrolytes are not automatically better; kidney, cardiovascular and medication context matters."), "efsa_drv", "cdc_heat_hydration"),
        topic("vitamins_and_minerals", "Vitamins and minerals", NutritionKnowledgeArea.MICRONUTRIENTS, setOf("micronutrients", "nutrient gaps"), "Micronutrients perform distinct physiological roles and requirements vary by life stage and context.", listOf("Evaluate variety and repeated dietary coverage.", "Preserve missing food-composition values as unknown."), listOf("Dietary estimates cannot diagnose a deficiency or replace clinical assessment."), "who_healthy_diet", "efsa_drv"),
        topic("meal_timing", "Meal timing", NutritionKnowledgeArea.FOOD_AND_DIETARY_PATTERNS, setOf("when to eat", "eating schedule"), "Meal timing can affect convenience, comfort, training fuel and sleep for some people, but total pattern and consistency usually provide essential context.", listOf("Relate timing to the user's own sleep, exercise and symptom records when data are adequate."), listOf("Do not impose fasting or rigid schedules as universally superior."), "acsm_sports_nutrition", "nhs_eatwell"),
        topic("meal_composition", "Meal composition", NutritionKnowledgeArea.FOOD_AND_DIETARY_PATTERNS, setOf("balanced meal", "food energy"), "The mix of carbohydrate, protein, fat, fibre, fluids and portion size can influence satiety, gastrointestinal comfort and post-meal experience.", listOf("Compare like-for-like meal patterns and record timing when exploring personal associations."), listOf("Post-meal tiredness has many possible explanations and is not diagnostic."), "nhs_eatwell", "uclh_glycaemic_index"),
        topic("energy_density_and_satiety", "Energy density and satiety", NutritionKnowledgeArea.FOOD_AND_DIETARY_PATTERNS, setOf("fullness", "satiety", "energy density"), "Foods differ in energy per gram and in how protein, fibre, water, texture and palatability contribute to fullness.", listOf("Use satiety as one signal alongside adequacy, enjoyment and sustainability."), listOf("Do not label energy-dense foods as inherently bad or low-energy foods as automatically adequate."), "who_healthy_diet", "nhs_eatwell"),
        topic("glycaemic_context", "Glycaemic context", NutritionKnowledgeArea.FOOD_AND_DIETARY_PATTERNS, setOf("glycaemic index", "glycemic index", "blood sugar speed"), "Glycaemic index describes the relative post-meal glucose response to carbohydrate foods, while portion and total carbohydrate load also matter.", listOf("Processing, ripeness, cooking and mixed-meal fat, protein and fibre can change the response."), listOf("GI alone does not define overall food quality and does not diagnose glucose problems."), "uclh_glycaemic_index"),
        topic("dietary_patterns", "Dietary patterns", NutritionKnowledgeArea.FOOD_AND_DIETARY_PATTERNS, setOf("balanced diet", "healthy eating"), "Adequacy, balance, moderation and diversity can be achieved through many culturally appropriate dietary patterns.", listOf("Look across days or weeks rather than demanding a perfect meal."), listOf("Respect preference, access, culture, allergy and clinical needs."), "who_healthy_diet", "nhs_eatwell"),
        topic("exercise_nutrition", "Exercise nutrition", NutritionKnowledgeArea.EXERCISE_AND_RECOVERY, setOf("training fuel", "pre workout food"), "Training nutrition links energy, carbohydrate, protein, fluids and timing to the type and duration of activity.", listOf("Use recent exercise load and environmental conditions to frame needs."), listOf("Athletic recommendations are scenario-specific; personalised plans belong with a qualified professional."), "acsm_sports_nutrition"),
        topic("recovery_nutrition", "Recovery nutrition", NutritionKnowledgeArea.EXERCISE_AND_RECOVERY, setOf("post workout", "recovery meal"), "Recovery nutrition supports refuelling, tissue repair and rehydration between sessions.", listOf("The urgency and composition depend on session demands and time until the next session."), listOf("One recovery meal cannot be interpreted without the wider daily pattern."), "acsm_sports_nutrition"),
        topic("digestion_context", "Digestion context", NutritionKnowledgeArea.DIGESTION, setOf("digestion", "bloating", "food tolerance"), "Fibre amount, meal size, fat, fermentable carbohydrates, caffeine, timing and individual tolerance can affect gastrointestinal experience.", listOf("Change one variable at a time and preserve symptom timing when exploring patterns."), listOf("Do not diagnose intolerance, allergy, IBS or malabsorption from a food association."), "nhs_eatwell"),
        topic("short_term_weight", "Short-term weight fluctuation", NutritionKnowledgeArea.BODY_WEIGHT_AND_COMPOSITION, setOf("overnight weight", "scale jump", "daily fluctuation"), "Short-term scale weight can shift through hydration, glycogen, sodium, gut contents and measurement conditions without equivalent fat change.", listOf("Compare measurements under similar conditions and prioritise a rolling trend."), listOf("Do not recommend compensatory restriction or exercise after a single high reading."), "glycogen_water_study", "niddk_body_weight"),
        topic("weight_trends", "Body-weight trends", NutritionKnowledgeArea.BODY_WEIGHT_AND_COMPOSITION, setOf("weight trend", "gaining weight", "losing weight"), "A consistent weight trend over weeks is more informative than isolated readings when considering energy balance.", listOf("Use enough observations to distinguish signal from normal variability."), listOf("Weight alone does not describe health, body composition or behaviour quality."), "niddk_body_weight"),
        topic("body_composition_measurement", "Body-composition measurement", NutritionKnowledgeArea.BODY_WEIGHT_AND_COMPOSITION, setOf("body fat scale", "bioimpedance", "bia"), "Consumer bioimpedance estimates depend on device equations and measurement conditions and are best treated as approximate trends.", listOf("Measure under similar hydration, meal, exercise and time-of-day conditions."), listOf("Do not treat one body-fat or muscle estimate as a precise tissue measurement."), "bia_validity_review", "glycogen_water_study")
    )

    private fun buildNutrients(): List<NutrientReference> = listOf(
        nutrient("calcium", "Calcium", NutrientCategory.MINERAL, setOf("ca"), "Supports bone and tooth structure, muscle contraction, nerve signalling and vascular function.", listOf("Milk, yoghurt and cheese", "Calcium-set tofu and fortified alternatives", "Some leafy greens and fish with edible bones"), "Long-term inadequate intake can compromise bone health, but serum calcium is not a simple measure of dietary adequacy.", "High supplemental intake can cause adverse effects and interact with medicines; food intake is normally the preferred context.", listOf("Needs differ in adolescence, later life, pregnancy and some restrictive diets."), listOf("Vitamin D supports absorption.", "Separate some calcium supplements from medicines only under pharmacist or clinician guidance."), "nih_ods_calcium", "efsa_drv"),
        nutrient("chloride", "Chloride", NutrientCategory.ELECTROLYTE, setOf("chloride ion"), "Contributes to fluid and acid-base balance and is a component of stomach acid.", listOf("Table salt and salted foods", "Many breads, cheeses and processed foods", "Naturally present in varied foods"), "Low dietary intake is uncommon; losses or clinical disturbances matter more than a food log alone.", "Excess commonly travels with high sodium intake and should be interpreted within the overall dietary and clinical context.", listOf("Heavy losses, vomiting and some medicines can alter electrolyte balance."), listOf("A food diary cannot determine blood chloride status."), "efsa_drv"),
        nutrient("copper", "Copper", NutrientCategory.MINERAL, emptySet(), "Supports iron metabolism, connective tissue, energy production, pigmentation and nervous and immune function.", listOf("Shellfish and organ meats", "Nuts and seeds", "Whole grains and cocoa"), "Sustained low intake is uncommon but risk can rise with malabsorption or excessive zinc exposure.", "Chronic excessive intake can damage the gastrointestinal tract or liver.", listOf("People with malabsorption or rare genetic disorders need clinical assessment."), listOf("High zinc intake can impair copper status."), "nih_ods_copper"),
        nutrient("iron", "Iron", NutrientCategory.MINERAL, setOf("ferrum", "heme iron", "haem iron", "nonheme iron", "non haem iron"), "Enables oxygen transport through haemoglobin and myoglobin and supports cellular metabolism and development.", listOf("Meat, poultry and seafood", "Beans, lentils and tofu", "Fortified cereals and some leafy greens"), "Repeatedly low intake can contribute to risk, but deficiency requires clinical assessment using appropriate history and laboratory markers.", "Unnecessary high-dose supplementation can cause gastrointestinal harm and iron overload; acute ingestion can be dangerous.", listOf("Menstruation, pregnancy, blood loss and growth can increase needs.", "Plant-based diets rely more heavily on non-haem iron."), listOf("Vitamin C can improve non-haem absorption.", "Tea, coffee and calcium around a meal can reduce absorption in some contexts.", "Do not recommend iron supplements from a food log alone."), "nih_ods_iron", "efsa_drv"),
        nutrient("iodine", "Iodine", NutrientCategory.MINERAL, emptySet(), "Required for thyroid hormone production and normal growth and neurological development.", listOf("Iodised salt where used", "Fish, seafood and dairy", "Eggs and some fortified foods"), "Low intake can impair thyroid hormone production, but symptoms and thyroid status cannot be inferred from food records.", "Excess iodine can also disrupt thyroid function, especially in susceptible people.", listOf("Pregnancy and lactation increase importance.", "Vegan diets can be low when iodised salt or fortified foods are absent."), listOf("Seaweed iodine content can be extremely variable.", "Thyroid disease requires clinician-led advice."), "nih_ods_iodine"),
        nutrient("magnesium", "Magnesium", NutrientCategory.MINERAL, setOf("mg mineral"), "Participates in energy metabolism, protein synthesis, nerve and muscle function and many enzyme reactions.", listOf("Nuts and seeds", "Beans and whole grains", "Leafy greens"), "Low intake may reduce coverage over time; true deficiency is more often linked with losses, medicines or health conditions than one low day.", "Excess from food is usually handled in healthy people, while high supplemental intake can cause diarrhoea and more serious problems in impaired kidney function.", listOf("Gastrointestinal losses, alcohol dependence and some medicines can increase risk."), listOf("Some antibiotics and other medicines interact with magnesium supplements."), "nih_ods_magnesium"),
        nutrient("manganese", "Manganese", NutrientCategory.MINERAL, emptySet(), "Supports enzyme systems involved in metabolism, antioxidant defence, bone formation and wound processes.", listOf("Whole grains", "Nuts and legumes", "Tea and leafy vegetables"), "Dietary deficiency is rare and cannot be identified from nonspecific symptoms.", "Chronic excessive exposure can affect the nervous system; supplement accumulation is a concern in some liver conditions.", listOf("Occupational exposure is different from dietary intake."), listOf("Do not infer manganese status from a single food record."), "nih_ods_manganese", "efsa_drv"),
        nutrient("phosphorus", "Phosphorus", NutrientCategory.MINERAL, setOf("phosphate"), "Supports bone mineral, cell membranes, energy transfer and acid-base regulation.", listOf("Dairy, meat and fish", "Beans, nuts and whole grains", "Foods containing phosphate additives"), "Low intake is uncommon with a varied diet and is more often clinically contextual.", "High intake can matter in kidney disease and when large amounts come from highly absorbable additives.", listOf("Kidney disease changes phosphorus handling and requires professional advice."), listOf("Food labels and databases may incompletely capture phosphate additives."), "nih_ods_phosphorus"),
        nutrient("potassium", "Potassium", NutrientCategory.ELECTROLYTE, setOf("k mineral"), "Supports intracellular fluid balance, nerve transmission, muscle contraction and blood-pressure regulation.", listOf("Potatoes, beans and lentils", "Fruit and vegetables", "Dairy, fish and nuts"), "Low intake may indicate limited plant-food variety, but blood potassium is governed by more than diet.", "High potassium can be dangerous when kidney function or certain medicines limit excretion.", listOf("Kidney disease, vomiting, diarrhoea and some medicines materially change interpretation."), listOf("Do not recommend potassium supplements or salt substitutes without relevant clinical context."), "nih_ods_potassium", "who_healthy_diet"),
        nutrient("selenium", "Selenium", NutrientCategory.MINERAL, emptySet(), "Supports selenoproteins involved in antioxidant defence, thyroid hormone metabolism and reproduction.", listOf("Seafood, meat and eggs", "Brazil nuts", "Grains, with content varying by soil"), "Sustained low intake can contribute to poor status in low-soil regions or restrictive diets.", "Chronic excess can cause selenosis; Brazil-nut content is highly variable.", listOf("Geography and soil influence food selenium."), listOf("Avoid treating a handful of high-selenium foods as a precise dose."), "nih_ods_selenium"),
        nutrient("sodium", "Sodium", NutrientCategory.ELECTROLYTE, setOf("salt", "na"), "Supports extracellular fluid balance, nerve impulses and muscle function.", listOf("Salt and salty seasonings", "Bread, cheese and processed foods", "Naturally present in many foods"), "Dietary deficiency is uncommon outside substantial losses or clinical circumstances.", "Habitually high intake can raise blood pressure in salt-sensitive people and populations.", listOf("Prolonged heavy sweating changes replacement context.", "Kidney, heart and blood-pressure conditions require individual guidance."), listOf("Salt is sodium chloride; food labels may report either salt or sodium.", "Sweat loss does not justify unlimited sodium."), "who_healthy_diet", "efsa_drv", "cdc_heat_hydration"),
        nutrient("zinc", "Zinc", NutrientCategory.MINERAL, emptySet(), "Supports immunity, DNA and protein synthesis, growth, wound healing and taste.", listOf("Oysters, meat and dairy", "Beans, nuts and seeds", "Fortified cereals"), "Low intake or absorption can impair status, but common symptoms are nonspecific.", "Long-term excessive intake can impair copper status and immune function.", listOf("Plant-based diets can have lower zinc bioavailability.", "Growth, pregnancy and malabsorption can change risk."), listOf("High-dose zinc can cause copper deficiency and interact with medicines."), "nih_ods_zinc"),
        nutrient("vitamin_a", "Vitamin A", NutrientCategory.VITAMIN, setOf("retinol", "beta carotene", "beta-carotene"), "Supports vision, epithelial tissues, immune function, growth and reproduction.", listOf("Liver, eggs and dairy", "Orange and dark-green vegetables", "Fortified foods"), "Low status can affect vision and immunity, but is uncommon in many well-nourished populations.", "Preformed vitamin A can accumulate and become toxic; pregnancy requires particular caution with high-retinol sources and supplements.", listOf("Young children, pregnancy and fat-malabsorption conditions need special consideration."), listOf("Beta-carotene and preformed retinol have different risk profiles."), "nih_ods_vitamin_a", "efsa_drv"),
        nutrient("vitamin_b1", "Thiamin (B1)", NutrientCategory.VITAMIN, setOf("thiamine", "vitamin b1", "b1"), "Helps convert carbohydrate into usable energy and supports nerve function.", listOf("Whole and fortified grains", "Pork", "Beans, seeds and nuts"), "Deficiency can occur with severe undernutrition, alcohol dependence, malabsorption or increased losses and requires clinical assessment.", "Toxicity from food is not a usual concern.", listOf("Alcohol dependence and prolonged poor intake substantially change risk."), listOf("Nonspecific fatigue does not identify thiamin deficiency."), "nih_ods_vitamin_b1"),
        nutrient("vitamin_b2", "Riboflavin (B2)", NutrientCategory.VITAMIN, setOf("riboflavin", "vitamin b2", "b2"), "Supports energy metabolism, cellular growth and activation of other vitamins.", listOf("Milk and eggs", "Meat", "Fortified grains and mushrooms"), "Low intake can occur within broader dietary inadequacy; isolated deficiency is uncommon.", "Toxicity from food is not a usual concern.", listOf("Vegan diets depend more on fortified foods and plant sources."), listOf("Light can degrade riboflavin in exposed foods."), "nih_ods_vitamin_b2"),
        nutrient("niacin", "Niacin (B3)", NutrientCategory.VITAMIN, setOf("vitamin b3", "b3", "nicotinic acid", "nicotinamide"), "Supports energy metabolism and cellular signalling and repair.", listOf("Meat, poultry and fish", "Peanuts and whole grains", "Fortified foods"), "Severe deficiency is uncommon where diets are varied or foods are fortified.", "High supplemental forms can cause flushing, liver injury and metabolic effects.", listOf("Very restrictive diets or malabsorption can increase risk."), listOf("Therapeutic niacin is a medicine-level intervention, not a food-log recommendation."), "nih_ods_niacin"),
        nutrient("pantothenic_acid", "Pantothenic acid (B5)", NutrientCategory.VITAMIN, setOf("vitamin b5", "b5"), "Forms part of coenzyme A, central to energy and fatty-acid metabolism.", listOf("Chicken, beef and eggs", "Whole grains and mushrooms", "Many vegetables"), "Deficiency is rare outside severe general undernutrition.", "Very high supplemental intake can cause gastrointestinal effects.", listOf("Widespread food distribution makes isolated low intake unusual."), listOf("Do not infer status from fatigue or food logs alone."), "nih_ods_pantothenic_acid"),
        nutrient("vitamin_b6", "Vitamin B6", NutrientCategory.VITAMIN, setOf("pyridoxine", "vitamin b6", "b6"), "Supports amino-acid metabolism, neurotransmitter synthesis, haemoglobin and immune function.", listOf("Poultry and fish", "Potatoes and chickpeas", "Fortified cereals"), "Low status can occur with kidney disease, malabsorption, alcohol dependence or some medicines.", "Chronic high supplemental intake can cause sensory neuropathy.", listOf("Pregnancy and some medicines can alter requirements or status."), listOf("Supplement toxicity is more relevant than excess from food."), "nih_ods_vitamin_b6", "efsa_drv"),
        nutrient("folate", "Folate (B9)", NutrientCategory.VITAMIN, setOf("folic acid", "vitamin b9", "b9"), "Supports DNA synthesis, cell division and red-blood-cell formation.", listOf("Leafy greens and legumes", "Citrus and avocado", "Fortified grains"), "Low status can cause megaloblastic anaemia and is especially consequential around conception and pregnancy.", "High folic-acid intake can mask the haematological signs of vitamin B12 deficiency.", listOf("People who could become pregnant have specific public-health guidance.", "Malabsorption and some medicines can raise risk."), listOf("Food folate and synthetic folic acid are not identical forms.", "Do not use folate intake to rule out B12 deficiency."), "nih_ods_folate", "efsa_drv"),
        nutrient("vitamin_b12", "Vitamin B12", NutrientCategory.VITAMIN, setOf("cobalamin", "b12", "vitamin b12"), "Supports DNA synthesis, red-blood-cell formation and neurological function.", listOf("Meat, fish, eggs and dairy", "Fortified plant milks and cereals", "Fortified nutritional yeast"), "Low intake or impaired absorption can lead to anaemia or neurological harm; diagnosis requires appropriate clinical testing.", "No common food-level toxicity concern is established, but supplementation should still match need and context.", listOf("Vegans need reliable fortified foods or appropriate professional guidance.", "Older age, pernicious anaemia, gastrointestinal surgery, metformin and acid-suppressing medicines can affect status."), listOf("A normal or high intake does not prove normal absorption or blood status."), "nih_ods_vitamin_b12"),
        nutrient("biotin", "Biotin (B7)", NutrientCategory.VITAMIN, setOf("vitamin b7", "b7"), "Acts as a cofactor in fatty-acid, glucose and amino-acid metabolism.", listOf("Eggs, meat and fish", "Nuts and seeds", "Some vegetables"), "Deficiency is rare but can occur with specific genetic conditions, prolonged raw egg-white intake or other clinical contexts.", "High supplemental biotin can interfere with important laboratory tests even when it does not cause classic toxicity.", listOf("Pregnancy may alter status.", "People having thyroid, cardiac or other immunoassay-based tests need to disclose supplement use."), listOf("Laboratory interference is a major caveat for high-dose supplements."), "nih_ods_biotin"),
        nutrient("vitamin_c", "Vitamin C", NutrientCategory.VITAMIN, setOf("ascorbic acid", "vitamin c"), "Supports collagen synthesis, antioxidant systems, immune function and non-haem iron absorption.", listOf("Citrus and berries", "Peppers and tomatoes", "Broccoli and potatoes"), "Very low sustained intake can cause scurvy; milder symptoms are nonspecific.", "High supplemental intake can cause gastrointestinal effects and may matter in people prone to some kidney stones or iron overload.", listOf("Smoking increases turnover and requirement.", "Highly restrictive diets can increase risk."), listOf("Storage and cooking can reduce food vitamin C."), "nih_ods_vitamin_c"),
        nutrient("vitamin_d", "Vitamin D", NutrientCategory.VITAMIN, setOf("calciferol", "vitamin d", "d3", "d2"), "Supports calcium and phosphate regulation, bone mineralisation, muscle and immune functions.", listOf("Oily fish and egg yolk", "Fortified foods", "Limited natural food sources; skin synthesis also contributes"), "Low status can impair bone mineralisation, but food intake alone cannot determine serum vitamin D.", "Excess supplemental vitamin D can cause hypercalcaemia and organ damage.", listOf("Limited sun exposure, darker skin, older age, covering skin and malabsorption can increase risk."), listOf("Use serum 25-hydroxyvitamin D and clinical context when assessment is warranted.", "Do not infer status from diet alone."), "nih_ods_vitamin_d", "efsa_drv"),
        nutrient("vitamin_e", "Vitamin E", NutrientCategory.VITAMIN, setOf("alpha tocopherol", "tocopherol", "vitamin e"), "Acts as a lipid-phase antioxidant and supports immune and cellular functions.", listOf("Vegetable oils", "Nuts and seeds", "Avocado and some green vegetables"), "Deficiency is uncommon without fat-malabsorption or rare genetic conditions.", "High supplemental intake can increase bleeding risk and interact with anticoagulant treatment.", listOf("Fat-malabsorption conditions change risk."), listOf("Food intake and high-dose supplements have different safety profiles."), "nih_ods_vitamin_e", "efsa_drv"),
        nutrient("vitamin_k", "Vitamin K", NutrientCategory.VITAMIN, setOf("phylloquinone", "menaquinone", "vitamin k"), "Supports normal blood clotting and proteins involved in bone metabolism.", listOf("Leafy green vegetables", "Vegetable oils", "Some fermented foods"), "Deficiency is uncommon in healthy adults but can occur with malabsorption or medicines.", "No common food-level toxicity concern is established, but sudden intake changes can interfere with vitamin K antagonist therapy.", listOf("Newborns have specific preventive care.", "People taking warfarin or similar medicines need consistent intake and clinical guidance."), listOf("Do not advise avoiding nutritious vitamin-K foods; consistency and prescriber guidance matter."), "nih_ods_vitamin_k"),
        nutrient("choline", "Choline", NutrientCategory.OTHER_ESSENTIAL_NUTRIENT, emptySet(), "Supports cell membranes, acetylcholine synthesis, methyl-group metabolism and lipid transport from the liver.", listOf("Eggs and meat", "Soy foods and beans", "Fish and some cruciferous vegetables"), "Low intake may reduce coverage, although requirements vary and food databases can be incomplete.", "High supplemental intake can cause gastrointestinal effects, low blood pressure and a fishy body odour.", listOf("Pregnancy and lactation increase importance.", "Genetic variation influences needs."), listOf("Food logs often omit choline, so absence is not zero."), "nih_ods_choline"),
        nutrient("omega_3", "Omega-3 fatty acids", NutrientCategory.FATTY_ACID, setOf("omega 3", "epa", "dha", "ala", "n 3 fatty acids"), "ALA is essential, while EPA and DHA have structural and signalling roles, particularly in cardiovascular and neural tissues.", listOf("Oily fish and seafood", "Flax, chia and walnuts for ALA", "Fortified foods and algae sources"), "Low intake is best framed as limited dietary coverage rather than a symptom diagnosis.", "High supplemental intakes can affect bleeding or interact with medicines; product quality and contaminant control matter.", listOf("Pregnancy requires species and contaminant-aware seafood guidance.", "People avoiding fish can use plant ALA and may consider algae sources with professional guidance."), listOf("ALA conversion to EPA and DHA is limited and variable.", "Project Superhuman does not yet have a canonical omega-3 food metric, so logs may not support a personal estimate."), "nih_ods_omega_3", "acsm_sports_nutrition")
    )

    private fun buildFoodTerms(): List<FoodTerm> = listOf(
        food("chicken_breast", "Chicken breast", "Lean poultry muscle meat; preparation, skin and added ingredients materially change nutrition.", FoodAlias("chicken breast"), FoodAlias("chicken fillet", setOf(FoodLocale.UK)), FoodAlias("boneless skinless chicken breast", setOf(FoodLocale.US))),
        food("coffee", "Coffee", "A brewed coffee drink; cup size, beans, milk, sugar and caffeine strength should not be assumed.", FoodAlias("coffee"), FoodAlias("black coffee"), FoodAlias("americano")),
        food("latte", "Latte", "Coffee with a substantial milk component; cup size, milk type, syrups and number of espresso shots affect energy and caffeine.", FoodAlias("latte"), FoodAlias("cafe latte"), FoodAlias("caffe latte")),
        food("oats", "Oats", "The grain ingredient; distinguish dry weight from cooked porridge and check added ingredients.", FoodAlias("oats"), FoodAlias("rolled oats"), FoodAlias("oatmeal", setOf(FoodLocale.US))),
        food("porridge", "Porridge", "A cooked cereal dish, commonly oats in the UK; liquid, serving weight and toppings determine its logged nutrition.", FoodAlias("porridge", setOf(FoodLocale.UK)), FoodAlias("oat porridge"), FoodAlias("cooked oatmeal", setOf(FoodLocale.US))),
        food("potato_crisps", "Potato crisps", "Thin packaged fried or baked potato snacks.", FoodAlias("crisps", setOf(FoodLocale.UK)), FoodAlias("potato crisps"), FoodAlias("potato chips", setOf(FoodLocale.US)), FoodAlias("chips", setOf(FoodLocale.US))),
        food("fried_potatoes", "Chips / fries", "Cut potato pieces cooked in oil; preparation and portion size materially change nutrition.", FoodAlias("chips", setOf(FoodLocale.UK)), FoodAlias("fries"), FoodAlias("french fries", setOf(FoodLocale.US)), FoodAlias("chip shop chips", setOf(FoodLocale.UK))),
        food("protein_shake", "Protein shake", "A drink containing protein powder or a high-protein base; powder, scoops, liquid and additions must be identified.", FoodAlias("protein shake"), FoodAlias("protein smoothie"), FoodAlias("whey shake"), FoodAlias("plant protein shake"))
    )

    private fun source(id: String, title: String, organisation: String, url: String, type: NutritionEvidenceType, updated: String? = null) =
        NutritionKnowledgeSource(id, title, organisation, url, type, updated, REVIEWED_AT)

    private fun ods(id: String, title: String, slug: String) = source(
        "nih_ods_$id",
        "$title — Health Professional Fact Sheet",
        "US National Institutes of Health, Office of Dietary Supplements",
        "https://ods.od.nih.gov/factsheets/$slug-HealthProfessional/",
        NutritionEvidenceType.GOVERNMENT_GUIDANCE
    )

    private fun topic(
        id: String,
        name: String,
        area: NutritionKnowledgeArea,
        aliases: Set<String>,
        summary: String,
        practical: List<String>,
        caveats: List<String>,
        vararg sourceIds: String
    ) = NutritionTopic(id, name, area, aliases, summary, practical, caveats, sourceIds.toSet())

    private fun nutrient(
        id: String,
        name: String,
        category: NutrientCategory,
        aliases: Set<String>,
        role: String,
        foods: List<String>,
        low: String,
        excess: String,
        populations: List<String>,
        caveats: List<String>,
        vararg sourceIds: String
    ) = NutrientReference(id, name, category, aliases, role, foods, low, excess, populations, caveats, sourceIds.toSet())

    private fun food(id: String, name: String, interpretation: String, vararg aliases: FoodAlias) =
        FoodTerm(id, name, aliases.toSet(), interpretation)

    private companion object {
        const val REVIEWED_AT = "2026-08-14"

        fun normalize(value: String): String = value.lowercase()
            .replace('µ', 'u')
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

        fun containsPhrase(text: String, phrase: String): Boolean =
            phrase.isNotBlank() && " $phrase " in " $text "

        fun hasAny(text: String, vararg phrases: String): Boolean =
            phrases.any { containsPhrase(text, normalize(it)) }
    }
}
