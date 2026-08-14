package com.projectsuperhuman.next.trudy.performance

/** Curated provenance only; no source text is copied into the product. */
object PerformanceEvidenceCatalog {
    val sources: List<PerformanceEvidenceSource> = listOf(
        source(
            "aasm_sleep_duration_2015",
            "Recommended amount of sleep for a healthy adult: AASM/SRS joint consensus statement",
            "American Academy of Sleep Medicine and Sleep Research Society",
            2015,
            PerformanceEvidenceKind.CONSENSUS_STATEMENT,
            "https://pubmed.ncbi.nlm.nih.gov/26194576/",
            "Adult sleep-duration consensus; individual sleep need and context still vary."
        ),
        source(
            "aasm_consumer_sleep_technology_2018",
            "Consumer sleep technology: an AASM position statement",
            "American Academy of Sleep Medicine",
            2018,
            PerformanceEvidenceKind.POSITION_STATEMENT,
            "https://aasm.org/advocacy/position-statements/consumer-sleep-technology/",
            "Consumer devices may support discussion and longitudinal observation but do not diagnose sleep disorders."
        ),
        source(
            "who_physical_activity_2020",
            "WHO guidelines on physical activity and sedentary behaviour",
            "World Health Organization",
            2020,
            PerformanceEvidenceKind.GUIDELINE,
            "https://www.who.int/publications/i/item/9789240015128",
            "Population-level activity, strengthening and sedentary-behaviour guidance; not an individual training prescription."
        ),
        source(
            "acsm_progression_resistance_2009",
            "Progression models in resistance training for healthy adults",
            "American College of Sports Medicine",
            2009,
            PerformanceEvidenceKind.POSITION_STATEMENT,
            "https://pubmed.ncbi.nlm.nih.gov/19204579/",
            "Principles for progression, frequency, loading and volume in healthy adults."
        ),
        source(
            "acsm_exercise_testing_2021",
            "ACSM's guidelines for exercise testing and prescription, 11th edition",
            "American College of Sports Medicine",
            2021,
            PerformanceEvidenceKind.GUIDELINE,
            "https://www.acsm.org/education-resources/books/guidelines-exercise-testing-prescription",
            "General exercise-testing and prescription principles; clinical screening remains outside Trudy's autonomous boundary."
        ),
        source(
            "meeusen_overtraining_2013",
            "Prevention, diagnosis and treatment of the overtraining syndrome: joint consensus statement",
            "European College of Sport Science and American College of Sports Medicine",
            2013,
            PerformanceEvidenceKind.CONSENSUS_STATEMENT,
            "https://pubmed.ncbi.nlm.nih.gov/23247672/",
            "Training fatigue is multifactorial and no single marker establishes readiness or overtraining."
        ),
        source(
            "halson_training_load_2014",
            "Monitoring training load to understand fatigue in athletes",
            "Sports Medicine",
            2014,
            PerformanceEvidenceKind.SYSTEMATIC_REVIEW,
            "https://pubmed.ncbi.nlm.nih.gov/25200666/",
            "Supports combining external load, internal response and subjective measures rather than relying on one score."
        ),
        source(
            "stutz_evening_exercise_sleep_2019",
            "Effects of evening exercise on sleep in healthy participants: systematic review and meta-analysis",
            "Sports Medicine",
            2019,
            PerformanceEvidenceKind.SYSTEMATIC_REVIEW,
            "https://pubmed.ncbi.nlm.nih.gov/30374942/",
            "Evening exercise is not uniformly harmful to sleep; timing, intensity and individual response matter."
        ),
        source(
            "gardiner_caffeine_sleep_2025",
            "Dose and timing effects of caffeine on subsequent sleep: randomized crossover trial",
            "Sleep Research Society",
            2025,
            PerformanceEvidenceKind.CONTROLLED_TRIAL,
            "https://pubmed.ncbi.nlm.nih.gov/39377163/",
            "Caffeine effects were dose- and timing-dependent in a small male sample; avoid universal cut-offs."
        ),
        source(
            "tanaka_max_heart_rate_2001",
            "Age-predicted maximal heart rate revisited",
            "Journal of the American College of Cardiology",
            2001,
            PerformanceEvidenceKind.SYSTEMATIC_REVIEW,
            "https://pubmed.ncbi.nlm.nih.gov/11153730/",
            "Age-predicted maximum-heart-rate equations describe population averages and have substantial individual error."
        ),
        source(
            "bishop_warm_up_2003",
            "Warm up II: performance changes following active warm up and how to structure the warm up",
            "Sports Medicine",
            2003,
            PerformanceEvidenceKind.SYSTEMATIC_REVIEW,
            "https://pubmed.ncbi.nlm.nih.gov/12930142/",
            "Warm-up effects depend on task, intensity, duration and recovery before performance."
        ),
        source(
            "mujika_detraining_2000",
            "Detraining: loss of training-induced physiological and performance adaptations",
            "Sports Medicine",
            2000,
            PerformanceEvidenceKind.SYSTEMATIC_REVIEW,
            "https://pubmed.ncbi.nlm.nih.gov/10966148/",
            "Training adaptations decay at different rates; short interruptions do not erase all progress."
        ),
        source(
            "who_air_quality_2021",
            "WHO global air quality guidelines",
            "World Health Organization",
            2021,
            PerformanceEvidenceKind.GUIDELINE,
            "https://www.who.int/publications/i/item/9789240034228",
            "Health-based guidance for PM2.5, PM10, ozone, nitrogen dioxide, sulfur dioxide and carbon monoxide."
        ),
        source(
            "cdc_niosh_heat_2024",
            "Workplace recommendations for heat stress",
            "US CDC National Institute for Occupational Safety and Health",
            2024,
            PerformanceEvidenceKind.GUIDELINE,
            "https://www.cdc.gov/niosh/heat-stress/recommendations/",
            "Heat risk depends on temperature, humidity, radiant heat, workload, clothing, acclimatisation and hydration."
        ),
        source(
            "okamoto_sleep_thermal_2012",
            "Effects of thermal environment on sleep and circadian rhythm",
            "Journal of Physiological Anthropology",
            2012,
            PerformanceEvidenceKind.SYSTEMATIC_REVIEW,
            "https://pubmed.ncbi.nlm.nih.gov/22215934/",
            "Thermal conditions can influence sleep, but comfort, bedding, clothing and individual acclimatisation modify exposure."
        ),
        source(
            "goyal_meditation_2014",
            "Meditation programs for psychological stress and well-being: systematic review and meta-analysis",
            "JAMA Internal Medicine",
            2014,
            PerformanceEvidenceKind.SYSTEMATIC_REVIEW,
            "https://pubmed.ncbi.nlm.nih.gov/24395196/",
            "Mindfulness programs showed small-to-moderate benefits for some distress outcomes; evidence was not uniformly strong."
        ),
        source(
            "zaccaro_slow_breathing_2018",
            "How breath-control can change your life: systematic review of slow breathing",
            "Frontiers in Human Neuroscience",
            2018,
            PerformanceEvidenceKind.SYSTEMATIC_REVIEW,
            "https://pubmed.ncbi.nlm.nih.gov/30245619/",
            "Slow breathing was associated with autonomic and psychological changes; studies were heterogeneous and often small."
        ),
        source(
            "cent_n_of_1_2015",
            "CONSORT extension for reporting N-of-1 trials (CENT) 2015",
            "CENT Group / CONSORT",
            2015,
            PerformanceEvidenceKind.METHODS_STANDARD,
            "https://pubmed.ncbi.nlm.nih.gov/26272792/",
            "Methods and reporting principles for planned repeated single-person comparisons; not all interventions suit N-of-1 designs."
        )
    )

    private val byId = sources.associateBy { it.id }

    init {
        require(byId.size == sources.size) { "Performance evidence source IDs must be unique" }
    }

    fun source(id: String): PerformanceEvidenceSource? = byId[id]

    private fun source(
        id: String,
        title: String,
        organisation: String,
        year: Int,
        kind: PerformanceEvidenceKind,
        url: String,
        scopeNote: String
    ) = PerformanceEvidenceSource(id, title, organisation, year, kind, url, scopeNote)
}
