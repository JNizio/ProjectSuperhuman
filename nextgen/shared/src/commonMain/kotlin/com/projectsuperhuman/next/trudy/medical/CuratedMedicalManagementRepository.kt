package com.projectsuperhuman.next.trudy.medical

import com.projectsuperhuman.next.core.HealthDomain

/**
 * Small reviewed seed pack. Statements are deliberately paraphrased and category-level.
 * This is reference knowledge, not Data Vault content and not a prescribing engine.
 */
class CuratedMedicalManagementRepository(
    entries: List<MedicalManagementEntry> = CuratedMedicalManagementKnowledge.entries
) : MedicalKnowledgeProvider {
    private val entriesById = entries.associateBy { it.conditionId }.also {
        require(it.size == entries.size) { "Duplicate medical management condition IDs" }
    }

    override suspend fun byConditionIds(conditionIds: Set<String>): List<MedicalManagementEntry> {
        conditionIds.forEach(::requireStableConditionId)
        return conditionIds.mapNotNull(entriesById::get)
    }

    override suspend fun searchManagement(query: String, limit: Int): List<MedicalKnowledgeMatch> {
        require(limit in 1..20)
        val normalized = normalize(query)
        if (normalized.length < 3) return emptyList()
        val tokens = normalized.split(' ').filter { it.length >= 3 }.toSet()
        return entriesById.values.mapNotNull { entry ->
            val terms = (entry.aliases + entry.displayName + entry.conditionId.replace('_', ' '))
                .map(::normalize)
                .filter { it.isNotBlank() }
            val exact = terms.filter { normalized.contains(it) }
            val tokenHits = terms.flatMap { it.split(' ') }.toSet().intersect(tokens)
            if (exact.isEmpty() && tokenHits.isEmpty()) null else {
                val score = (0.45 + exact.size * 0.25 + tokenHits.size * 0.08).coerceAtMost(1.0)
                MedicalKnowledgeMatch(entry.conditionId, score, (exact + tokenHits).distinct(), entry)
            }
        }.sortedByDescending { it.relevance }.take(limit)
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}

object CuratedMedicalManagementKnowledge {
    private const val REVIEWED = "2026-08-14"
    private const val REVIEW_DUE = "2027-02-14"

    val entries: List<MedicalManagementEntry> = listOf(
        asthma(),
        irritableBowelSyndrome(),
        gastroOesophagealReflux(),
        hiatusHernia(),
        lowBackPainAndSciatica(),
        panicDisorder(),
        hypertension()
    )

    private fun asthma(): MedicalManagementEntry {
        val sources = listOf(
            source("nhs_asthma", "Asthma", "NHS", "https://www.nhs.uk/conditions/asthma/"),
            source("nice_asthma", "Asthma: diagnosis, monitoring and chronic asthma management", "NICE", "https://www.nice.org.uk/guidance/ng245", MedicalEvidenceType.CLINICAL_GUIDELINE)
        )
        return entry(
            id = "asthma",
            name = "Asthma",
            aliases = setOf("asthma", "reactive airway disease", "wheeze"),
            sources = sources,
            options = listOf(
                option("asthma_trigger_reduction", MedicalManagementCategory.PREVENTION_RISK_REDUCTION, "Identify and reduce avoidable triggers, including tobacco smoke; vaccination and an agreed action plan commonly support prevention of flare-ups.", MedicalCareSetting.SELF_CARE, "nhs_asthma"),
                option("asthma_activity", MedicalManagementCategory.LIFESTYLE, "Regular activity is generally encouraged when asthma is controlled; symptoms provoked by activity are a reason to review control rather than simply avoid movement.", MedicalCareSetting.SELF_CARE, "nhs_asthma"),
                option("asthma_inhaler_therapy", MedicalManagementCategory.FIRST_LINE_TREATMENT, "Inhaled anti-inflammatory and airway-opening treatment categories are the usual foundation of care, selected through a clinician-led asthma pathway.", MedicalCareSetting.CLINICIAN_LED, "nice_asthma", "nhs_asthma"),
                option("asthma_technique_review", MedicalManagementCategory.MONITORING, "Control reviews commonly include symptom frequency, night waking, reliever use, exacerbations, inhaler technique and adherence.", MedicalCareSetting.PRIMARY_CARE, "nice_asthma"),
                option("asthma_specialist", MedicalManagementCategory.SECOND_LINE_SPECIALIST, "Persistent poor control, diagnostic uncertainty or severe attacks can require specialist assessment and additional treatment categories such as biologic therapy.", MedicalCareSetting.SPECIALIST_ONLY, "nice_asthma"),
                option("asthma_no_self_escalation", MedicalManagementCategory.SELF_TREATMENT_LIMIT, "Do not improvise prescription changes from app data; worsening control or an ineffective reliever needs prompt use of the person's action plan and professional review.", MedicalCareSetting.CLINICIAN_LED, "nhs_asthma")
            ),
            signals = listOf(
                signal(HealthDomain.EXERCISE, listOf("exercise_duration_min", "heart_rate_bpm"), "Whether symptoms and tolerance vary around activity"),
                signal(HealthDomain.SLEEP, purpose = "Night waking and sleep disruption can make poor control more relevant"),
                signal(HealthDomain.ENVIRONMENT, purpose = "Air quality, pollen and weather may provide non-diagnostic trigger context"),
                signal(HealthDomain.CLINICAL, purpose = "Recorded respiratory tests or clinician observations")
            )
        )
    }

    private fun irritableBowelSyndrome(): MedicalManagementEntry {
        val sources = listOf(
            source("nhs_ibs", "Diet, lifestyle and medicines for IBS", "NHS", "https://www.nhs.uk/conditions/irritable-bowel-syndrome-ibs/diet-lifestyle-and-medicines/"),
            source("nice_ibs", "Irritable bowel syndrome in adults", "NICE", "https://www.nice.org.uk/guidance/cg61", MedicalEvidenceType.CLINICAL_GUIDELINE)
        )
        return entry(
            id = "irritable_bowel_syndrome",
            name = "Irritable bowel syndrome",
            aliases = setOf("ibs", "irritable bowel", "irritable bowel syndrome"),
            sources = sources,
            options = listOf(
                option("ibs_regular_pattern", MedicalManagementCategory.DIETARY, "Regular meals, adequate fluid and an individual food-and-symptom record are common first steps; broad restrictions should not be assumed necessary.", MedicalCareSetting.SELF_CARE, "nhs_ibs", "nice_ibs"),
                option("ibs_activity_stress", MedicalManagementCategory.LIFESTYLE, "Regular activity and practical stress-management approaches can be part of symptom management.", MedicalCareSetting.SELF_CARE, "nhs_ibs"),
                option("ibs_symptom_targeted_categories", MedicalManagementCategory.FIRST_LINE_TREATMENT, "Clinicians or pharmacists may use symptom-targeted categories for pain, constipation or diarrhoea after considering the predominant pattern and contraindications.", MedicalCareSetting.CLINICIAN_LED, "nice_ibs"),
                option("ibs_dietitian", MedicalManagementCategory.SECOND_LINE_SPECIALIST, "A structured exclusion approach such as low FODMAP is normally reserved for persistent symptoms and is best supported by a clinician or dietitian to reduce nutritional risk.", MedicalCareSetting.SPECIALIST_ONLY, "nhs_ibs", "nice_ibs"),
                option("ibs_psychological", MedicalManagementCategory.PSYCHOLOGICAL_THERAPY, "For persistent or refractory symptoms, gut-directed psychological approaches such as cognitive behavioural therapy or hypnotherapy may be considered.", MedicalCareSetting.SPECIALIST_ONLY, "nice_ibs"),
                option("ibs_reassess_red_flags", MedicalManagementCategory.SELF_TREATMENT_LIMIT, "New bleeding, unexplained weight loss, anaemia, a mass, fever or a substantial change from the established pattern should not be managed as an assumed IBS flare.", MedicalCareSetting.PRIMARY_CARE, "nice_ibs")
            ),
            signals = listOf(
                signal(HealthDomain.NUTRITION, purpose = "Meal composition and timing recorded near symptom changes"),
                signal(HealthDomain.HYDRATION, purpose = "Hydration context, especially with diarrhoea or constipation"),
                signal(HealthDomain.EXERCISE, purpose = "Activity pattern without assuming causation"),
                signal(HealthDomain.EMOTIONAL, purpose = "Stress and emotional context without psychologising symptoms"),
                signal(HealthDomain.CLINICAL, purpose = "Tests relevant to excluding alternative explanations")
            )
        )
    }

    private fun gastroOesophagealReflux(): MedicalManagementEntry {
        val sources = listOf(
            source("nhs_reflux", "Heartburn and acid reflux", "NHS", "https://www.nhs.uk/conditions/heartburn-and-acid-reflux/"),
            source("nice_gord", "Gastro-oesophageal reflux disease and dyspepsia in adults", "NICE", "https://www.nice.org.uk/guidance/cg184", MedicalEvidenceType.CLINICAL_GUIDELINE)
        )
        return entry(
            id = "gastro_oesophageal_reflux_disease",
            name = "Gastro-oesophageal reflux disease",
            aliases = setOf("gord", "gerd", "acid reflux", "heartburn", "gastro oesophageal reflux"),
            sources = sources,
            options = listOf(
                option("gord_meal_timing", MedicalManagementCategory.BEHAVIOURAL, "Smaller meals, avoiding personally reproducible triggers and leaving time between eating and lying down can reduce symptoms for some people.", MedicalCareSetting.SELF_CARE, "nhs_reflux"),
                option("gord_bed_position", MedicalManagementCategory.LIFESTYLE, "For night symptoms, raising the head end of the bed is a commonly suggested positional measure; extra pillows alone may increase abdominal pressure.", MedicalCareSetting.SELF_CARE, "nhs_reflux"),
                option("gord_risk_reduction", MedicalManagementCategory.PREVENTION_RISK_REDUCTION, "Weight management where relevant, avoiding tobacco and moderating alcohol can reduce reflux burden and longer-term risk.", MedicalCareSetting.SELF_CARE, "nhs_reflux", "nice_gord"),
                option("gord_medicine_categories", MedicalManagementCategory.FIRST_LINE_TREATMENT, "Antacid or alginate products and clinician-directed acid-suppression categories are commonly used according to symptom pattern and response.", MedicalCareSetting.CLINICIAN_LED, "nhs_reflux", "nice_gord"),
                option("gord_investigation", MedicalManagementCategory.PROFESSIONAL_EVALUATION, "Persistent, atypical or treatment-resistant symptoms may prompt review of medicines and tests such as endoscopy or reflux studies.", MedicalCareSetting.CLINICIAN_LED, "nice_gord"),
                option("gord_alarm_features", MedicalManagementCategory.SELF_TREATMENT_LIMIT, "Difficulty swallowing, recurrent vomiting, gastrointestinal bleeding, unexplained weight loss, anaemia or severe/new chest or upper-abdominal pain need professional assessment rather than repeated self-treatment.", MedicalCareSetting.PRIMARY_CARE, "nhs_reflux", "nice_gord")
            ),
            signals = listOf(
                signal(HealthDomain.NUTRITION, purpose = "Meal timing and personally observed food associations"),
                signal(HealthDomain.SLEEP, purpose = "Night symptoms and sleep disruption"),
                signal(HealthDomain.BODY, listOf("weight_kg"), "Weight trend where relevant, without blame"),
                signal(HealthDomain.CLINICAL, purpose = "Recorded investigations, anaemia markers or clinician findings")
            )
        )
    }

    private fun hiatusHernia(): MedicalManagementEntry {
        val sources = listOf(source("nhs_hiatus_hernia", "Hiatus hernia", "NHS", "https://www.nhs.uk/conditions/hiatus-hernia/"))
        return entry(
            id = "hiatus_hernia",
            name = "Hiatus hernia",
            aliases = setOf("hiatus hernia", "hiatal hernia"),
            sources = sources,
            options = listOf(
                option("hernia_reflux_measures", MedicalManagementCategory.LIFESTYLE, "When symptoms are reflux-related, smaller meals, individual trigger avoidance and elevating the head end of the bed are common self-care measures.", MedicalCareSetting.SELF_CARE, "nhs_hiatus_hernia"),
                option("hernia_weight_context", MedicalManagementCategory.PREVENTION_RISK_REDUCTION, "Weight management may be discussed when excess weight contributes to symptoms; advice should remain proportionate and non-judgemental.", MedicalCareSetting.SELF_CARE, "nhs_hiatus_hernia"),
                option("hernia_medicine_categories", MedicalManagementCategory.FIRST_LINE_TREATMENT, "Pharmacy symptom-relief products and clinician-directed acid suppression are common treatment categories when lifestyle measures are insufficient.", MedicalCareSetting.CLINICIAN_LED, "nhs_hiatus_hernia"),
                option("hernia_tests", MedicalManagementCategory.PROFESSIONAL_EVALUATION, "Persistent symptoms despite treatment can lead to tests to confirm the cause and assess complications or alternative explanations.", MedicalCareSetting.CLINICIAN_LED, "nhs_hiatus_hernia"),
                option("hernia_surgery", MedicalManagementCategory.PROCEDURE, "Keyhole repair may be considered for selected people with severe or persistent symptoms after assessment; expected benefits, recurrence and postoperative swallowing or bloating effects require specialist discussion.", MedicalCareSetting.SPECIALIST_ONLY, "nhs_hiatus_hernia"),
                option("hernia_alarm_features", MedicalManagementCategory.SELF_TREATMENT_LIMIT, "Progressive swallowing difficulty, repeated vomiting, bleeding, unexplained weight loss or significant upper-abdominal pain should not be treated as routine reflux without prompt review.", MedicalCareSetting.PRIMARY_CARE, "nhs_hiatus_hernia")
            ),
            signals = listOf(
                signal(HealthDomain.NUTRITION, purpose = "Meal size, timing and reproducible symptom context"),
                signal(HealthDomain.SLEEP, purpose = "Positional or night-time symptom burden"),
                signal(HealthDomain.BODY, listOf("weight_kg"), "Weight trend where clinically relevant"),
                signal(HealthDomain.CLINICAL, purpose = "Endoscopy, imaging or laboratory context")
            )
        )
    }

    private fun lowBackPainAndSciatica(): MedicalManagementEntry {
        val sources = listOf(
            source("nice_back_pain", "Low back pain and sciatica in over 16s", "NICE", "https://www.nice.org.uk/guidance/ng59/chapter/recommendations", MedicalEvidenceType.CLINICAL_GUIDELINE),
            source("nhs_cauda_equina", "Cauda equina syndrome", "Buckinghamshire Healthcare NHS Trust", "https://www.buckshealthcare.nhs.uk/pifs/cauda-equina-syndrome/")
        )
        return entry(
            id = "low_back_pain_and_sciatica",
            name = "Low back pain and sciatica",
            aliases = setOf("low back pain", "lower back pain", "sciatica", "slipped disc", "herniated disc", "lumbar radiculopathy"),
            sources = sources,
            options = listOf(
                option("back_self_management", MedicalManagementCategory.BEHAVIOURAL, "Support staying active and continuing ordinary activity as tolerated, with pacing and clear safety-netting rather than prolonged bed rest.", MedicalCareSetting.SELF_CARE, "nice_back_pain"),
                option("back_exercise", MedicalManagementCategory.PHYSIOTHERAPY_REHABILITATION, "Exercise programmes can be selected around needs, preferences and capability; a physiotherapist can guide graded rehabilitation when pain or nerve symptoms limit progress.", MedicalCareSetting.CLINICIAN_LED, "nice_back_pain"),
                option("back_manual_therapy", MedicalManagementCategory.PHYSIOTHERAPY_REHABILITATION, "Manual therapy may be considered only as part of a package that includes exercise, rather than as a stand-alone cure.", MedicalCareSetting.CLINICIAN_LED, "nice_back_pain"),
                option("back_psychological", MedicalManagementCategory.PSYCHOLOGICAL_THERAPY, "For persistent pain or barriers to recovery, a combined physical and cognitive-behavioural approach may help function and coping without implying that pain is imaginary.", MedicalCareSetting.CLINICIAN_LED, "nice_back_pain"),
                option("back_specialist", MedicalManagementCategory.SECOND_LINE_SPECIALIST, "Persistent disabling sciatica, progressive neurological findings or failure of non-surgical care can justify specialist assessment; imaging is generally used when it is likely to change management.", MedicalCareSetting.SPECIALIST_ONLY, "nice_back_pain"),
                option("back_emergency_limit", MedicalManagementCategory.SELF_TREATMENT_LIMIT, "New bladder or bowel disturbance, saddle/genital sensory loss, or severe/progressive neurological deficits with back or leg pain require emergency assessment for possible cauda equina compression.", MedicalCareSetting.SPECIALIST_ONLY, "nhs_cauda_equina")
            ),
            signals = listOf(
                signal(HealthDomain.EXERCISE, purpose = "Function, activity tolerance and graded rehabilitation context"),
                signal(HealthDomain.SLEEP, purpose = "Sleep disruption and recovery context"),
                signal(HealthDomain.EMOTIONAL, purpose = "Pain burden and coping context, never proof of a psychological cause"),
                signal(HealthDomain.CLINICAL, purpose = "Recorded neurological examination, imaging or clinician findings")
            )
        )
    }

    private fun panicDisorder(): MedicalManagementEntry {
        val sources = listOf(
            source("nhs_panic", "Panic disorder", "NHS", "https://www.nhs.uk/mental-health/conditions/panic-disorder/", updated = "2026-03-06"),
            source("nice_panic", "Generalised anxiety disorder and panic disorder in adults", "NICE", "https://www.nice.org.uk/guidance/cg113/chapter/Recommendations", MedicalEvidenceType.CLINICAL_GUIDELINE)
        )
        return entry(
            id = "panic_disorder",
            name = "Panic disorder",
            aliases = setOf("panic disorder", "panic attacks", "panic attack"),
            sources = sources,
            options = listOf(
                option("panic_in_attack", MedicalManagementCategory.BEHAVIOURAL, "During a familiar panic episode, slowing breathing, orienting to the present and remembering that the surge will pass may reduce escalation; new or atypical physical symptoms still need appropriate medical assessment.", MedicalCareSetting.SELF_CARE, "nhs_panic"),
                option("panic_cbt_self_help", MedicalManagementCategory.PSYCHOLOGICAL_THERAPY, "CBT-based self-help and professionally delivered cognitive behavioural therapy are established approaches that address fear of symptoms and avoidance.", MedicalCareSetting.CLINICIAN_LED, "nhs_panic", "nice_panic"),
                option("panic_lifestyle", MedicalManagementCategory.LIFESTYLE, "Regular activity, sleep support and reducing personally aggravating caffeine, alcohol or tobacco can complement treatment.", MedicalCareSetting.SELF_CARE, "nhs_panic"),
                option("panic_medicine_categories", MedicalManagementCategory.FIRST_LINE_TREATMENT, "Antidepressant categories may be considered with a prescriber when psychological treatment is insufficient, declined or not preferred; benefits, adverse effects and withdrawal planning need clinical discussion.", MedicalCareSetting.CLINICIAN_LED, "nice_panic", "nhs_panic"),
                option("panic_review", MedicalManagementCategory.MONITORING, "Review commonly covers attack frequency, anticipatory fear, avoidance, daily function, treatment effects and whether another physical or mental-health explanation needs evaluation.", MedicalCareSetting.PRIMARY_CARE, "nhs_panic", "nice_panic"),
                option("panic_no_assumption", MedicalManagementCategory.SELF_TREATMENT_LIMIT, "Do not label a first, severe or unusual episode as panic solely from symptom overlap; chest pain, fainting, marked breathlessness, neurological symptoms or self-harm risk need proportionate urgent assessment.", MedicalCareSetting.PRIMARY_CARE, "nhs_panic")
            ),
            signals = listOf(
                signal(HealthDomain.EMOTIONAL, purpose = "Self-reported anxiety and functioning, not a diagnostic test"),
                signal(HealthDomain.SLEEP, purpose = "Sleep disruption and recovery context"),
                signal(HealthDomain.EXERCISE, purpose = "Activity and avoidance patterns"),
                signal(HealthDomain.BLOOD_PRESSURE, purpose = "Recorded pulse or pressure may contextualise an episode but cannot prove panic"),
                signal(HealthDomain.CLINICAL, purpose = "Professional evaluation or tests used to consider alternatives")
            )
        )
    }

    private fun hypertension(): MedicalManagementEntry {
        val sources = listOf(
            source("who_hypertension", "Hypertension", "World Health Organization", "https://www.who.int/news-room/fact-sheets/detail/hypertension", MedicalEvidenceType.PUBLIC_HEALTH_GUIDANCE, "2025-09-25"),
            source("cdc_bp_management", "Managing High Blood Pressure", "US Centers for Disease Control and Prevention", "https://www.cdc.gov/high-blood-pressure/living-with/index.html", MedicalEvidenceType.PUBLIC_HEALTH_GUIDANCE, "2024-12-13"),
            source("nhs_high_bp", "High blood pressure", "NHS", "https://www.nhs.uk/conditions/high-blood-pressure/")
        )
        return entry(
            id = "hypertension",
            name = "Hypertension",
            aliases = setOf("hypertension", "high blood pressure", "raised blood pressure"),
            sources = sources,
            options = listOf(
                option("bp_measurement", MedicalManagementCategory.MONITORING, "Diagnosis and control depend on repeated, correctly obtained blood-pressure measurements rather than symptoms or a single isolated reading.", MedicalCareSetting.CLINICIAN_LED, "cdc_bp_management", "nhs_high_bp"),
                option("bp_lifestyle", MedicalManagementCategory.LIFESTYLE, "A balanced lower-sodium eating pattern, regular activity, weight management where relevant, adequate sleep and limiting alcohol support blood-pressure and cardiovascular risk reduction.", MedicalCareSetting.SELF_CARE, "who_hypertension", "cdc_bp_management"),
                option("bp_tobacco", MedicalManagementCategory.PREVENTION_RISK_REDUCTION, "Avoiding tobacco reduces cardiovascular risk even when it is not the sole cause of a person's blood-pressure pattern.", MedicalCareSetting.SELF_CARE, "who_hypertension", "cdc_bp_management"),
                option("bp_medicine_categories", MedicalManagementCategory.FIRST_LINE_TREATMENT, "Several antihypertensive medicine categories are commonly used; selection and combinations depend on confirmed readings, overall risk, age, other conditions, pregnancy potential and adverse effects.", MedicalCareSetting.CLINICIAN_LED, "who_hypertension"),
                option("bp_follow_up", MedicalManagementCategory.PROFESSIONAL_EVALUATION, "Professional follow-up commonly reviews cardiovascular risk, treatment response, adherence, adverse effects and possible secondary causes or organ effects when indicated.", MedicalCareSetting.CLINICIAN_LED, "who_hypertension", "cdc_bp_management"),
                option("bp_no_false_reassurance", MedicalManagementCategory.SELF_TREATMENT_LIMIT, "Normal symptoms do not rule out hypertension, while symptoms alone do not diagnose it; severe readings with chest pain, neurological symptoms or severe breathlessness need urgent assessment.", MedicalCareSetting.CLINICIAN_LED, "nhs_high_bp")
            ),
            signals = listOf(
                signal(HealthDomain.BLOOD_PRESSURE, purpose = "Repeated readings, timing and measurement context"),
                signal(HealthDomain.EXERCISE, purpose = "Activity pattern relevant to risk reduction"),
                signal(HealthDomain.NUTRITION, purpose = "Dietary pattern, especially sodium-related context where available"),
                signal(HealthDomain.SLEEP, purpose = "Sleep duration and quality context"),
                signal(HealthDomain.BODY, listOf("weight_kg"), "Weight trend where relevant and non-judgemental"),
                signal(HealthDomain.CLINICAL, purpose = "Renal, metabolic and cardiovascular risk markers")
            )
        )
    }

    private fun source(
        id: String,
        title: String,
        organisation: String,
        url: String,
        type: MedicalEvidenceType = MedicalEvidenceType.GOVERNMENT_PATIENT_GUIDANCE,
        updated: String? = null
    ) = MedicalSource(id, title, organisation, url, type, updated, REVIEWED)

    private fun option(
        id: String,
        category: MedicalManagementCategory,
        summary: String,
        setting: MedicalCareSetting,
        vararg sourceIds: String
    ) = MedicalManagementOption(id, category, summary, setting, sourceIds.toList())

    private fun signal(
        domain: HealthDomain,
        metrics: List<String> = emptyList(),
        purpose: String
    ) = MedicalVaultSignalSpec(domain, metrics, purpose)

    private fun entry(
        id: String,
        name: String,
        aliases: Set<String>,
        sources: List<MedicalSource>,
        options: List<MedicalManagementOption>,
        signals: List<MedicalVaultSignalSpec>
    ) = MedicalManagementEntry(id, name, aliases, options, sources, signals, REVIEW_DUE)
}
