package com.projectsuperhuman.next.trudy.performance

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.emotional.EmotionalMetricIds
import com.projectsuperhuman.next.environment.EnvironmentalMetricIds

private typealias D = PerformanceKnowledgeDomain
private typealias S = PerformanceSafetyBoundary

/**
 * Versioned, structured performance knowledge. Claims remain separate from personal evidence:
 * integration code should retrieve claims to explain context, then use Data Vault tools for the
 * user's actual measurements.
 */
object PerformanceKnowledgeCatalog {
    const val VERSION = 1

    val claims: List<PerformanceKnowledgeClaim> = listOf(
        claim(
            "sleep_duration_context", setOf(D.SLEEP),
            "Sleep duration is one dimension of sleep health. Most healthy adults should regularly obtain at least seven hours, while individual need and circumstances vary.",
            listOf("Compare repeated nights and daytime function, not one isolated night.", "Treat time in bed and estimated time asleep as different quantities."),
            listOf("A duration target does not diagnose insomnia or another sleep disorder."),
            setOf("aasm_sleep_duration_2015")
        ),
        claim(
            "sleep_timing_regularity", setOf(D.SLEEP),
            "Sleep timing and regularity provide information that duration alone misses.",
            listOf("Compare bedtime and wake-time distributions across several days.", "Interpret schedule changes alongside work, travel, illness and social timing."),
            listOf("A regular schedule should not be achieved by deliberately restricting needed sleep."),
            setOf("aasm_sleep_duration_2015")
        ),
        claim(
            "sleep_continuity", setOf(D.SLEEP),
            "Continuity concerns how consolidated sleep is, including awakenings, awake time and longer interruptions.",
            listOf("Use interruption count, awake minutes and efficiency together when available.", "A brief wearable-detected awakening may not have been consciously remembered."),
            listOf("Consumer devices can misclassify quiet wakefulness as sleep and movement as wakefulness."),
            setOf("aasm_consumer_sleep_technology_2018"),
            setOf(S.WEARABLE_ESTIMATE_NOT_CLINICAL_MEASUREMENT)
        ),
        claim(
            "sleep_stages_and_wearables", setOf(D.SLEEP),
            "Consumer wearables estimate sleep stages from indirect signals; polysomnography uses clinical neurophysiological measurements and is not equivalent.",
            listOf("Prefer broad longitudinal patterns over precise nightly stage percentages.", "Preserve device/source identity when comparing estimates."),
            listOf("Wearable stage estimates must not be used to diagnose a sleep disorder."),
            setOf("aasm_consumer_sleep_technology_2018"),
            setOf(S.WEARABLE_ESTIMATE_NOT_CLINICAL_MEASUREMENT, S.NON_DIAGNOSTIC)
        ),
        claim(
            "sleep_debt_concept", setOf(D.SLEEP, D.RECOVERY),
            "Sleep debt is a useful description of accumulated short sleep relative to a chosen need estimate, not a directly measured biological substance.",
            listOf("Calculate against an explicit, revisable sleep-need assumption.", "Show both the assumption and the observed durations."),
            listOf("A single long sleep does not prove that all consequences of repeated short sleep have been reversed."),
            setOf("aasm_sleep_duration_2015")
        ),
        claim(
            "circadian_light_context", setOf(D.SLEEP, D.ENVIRONMENT),
            "Light exposure and timing are major cues for circadian timing, while personal schedules and prior light history modify response.",
            listOf("Relate daylight and light-timing questions to sleep timing rather than only sleep duration.", "Treat travel and shift schedules as major context."),
            listOf("Recorded outdoor daylight duration is not the same as the user's actual eye-level light exposure."),
            setOf("aasm_sleep_duration_2015")
        ),
        claim(
            "caffeine_sleep_context", setOf(D.SLEEP, D.EXPERIMENTATION),
            "Caffeine can affect sleep in a dose-, timing- and person-dependent way, including effects that users may not accurately perceive.",
            listOf("Record dose and clock time where possible.", "Keep total dose stable when testing timing.", "Use repeated nights rather than one comparison."),
            listOf("Do not present one universal caffeine cut-off as correct for everyone."),
            setOf("gardiner_caffeine_sleep_2025", "cent_n_of_1_2015"),
            setOf(S.PERSONAL_ASSOCIATION_NOT_CAUSATION, S.EXPERIMENT_NOT_UNIVERSAL_PROOF)
        ),
        claim(
            "exercise_sleep_context", setOf(D.SLEEP, D.EXERCISE, D.RECOVERY),
            "Regular exercise can support sleep, and evening exercise is not uniformly sleep-disrupting; intensity, finish time, temperature and individual response matter.",
            listOf("Separate exercise timing from exercise load.", "Compare similar session types when examining sleep afterwards."),
            listOf("An association between a late workout and one poor night does not establish the cause."),
            setOf("stutz_evening_exercise_sleep_2019"),
            setOf(S.PERSONAL_ASSOCIATION_NOT_CAUSATION)
        ),
        claim(
            "sleep_thermal_environment", setOf(D.SLEEP, D.ENVIRONMENT),
            "Thermal conditions can influence sleep, but indoor temperature, bedding, clothing, humidity and personal comfort jointly determine exposure.",
            listOf("Do not substitute outdoor temperature for measured bedroom temperature.", "Compare temperature with continuity and timing across repeated nights."),
            listOf("Environmental association does not establish the reason for a sleep change."),
            setOf("okamoto_sleep_thermal_2012"),
            setOf(S.PERSONAL_ASSOCIATION_NOT_CAUSATION)
        ),
        claim(
            "sleep_stress_arousal", setOf(D.SLEEP, D.EMOTIONAL_WELLBEING, D.RECOVERY),
            "Stress and cognitive or physiological arousal can coincide with difficulty winding down, fragmented sleep and next-day fatigue.",
            listOf("Use time-aligned Emotional self-reports as context, not as a diagnosis.", "Consider bidirectionality: poor sleep can also coincide with worse mood or stress."),
            listOf("The available data cannot by itself identify a psychiatric or sleep disorder."),
            setOf("goyal_meditation_2014", "aasm_consumer_sleep_technology_2018"),
            setOf(S.MENTAL_WELLBEING_NOT_DIAGNOSIS, S.PERSONAL_ASSOCIATION_NOT_CAUSATION)
        ),
        claim(
            "napping_context", setOf(D.SLEEP, D.RECOVERY),
            "Naps may improve short-term alertness but can also change sleep pressure and later sleep timing depending on duration, timing and the person.",
            listOf("Record nap timing and duration before attributing a later sleep change.", "Interpret naps differently during shift work or acute sleep loss."),
            listOf("A nap is not a complete substitute for adequate habitual sleep."),
            setOf("aasm_sleep_duration_2015")
        ),
        claim(
            "shift_schedule_context", setOf(D.SLEEP, D.RECOVERY, D.ENVIRONMENT),
            "Shift work and irregular schedules can create conflict between required sleep timing, circadian timing, light exposure and social obligations.",
            listOf("Analyse workdays and non-workdays separately.", "Preserve local clock time and travel/time-zone context."),
            listOf("Generic sleep-hygiene advice may be impractical for rotating or night-shift schedules."),
            setOf("aasm_sleep_duration_2015")
        ),
        claim(
            "sleep_hygiene_context", setOf(D.SLEEP),
            "Sleep hygiene is a collection of modifiable conditions and routines, not a guaranteed treatment or a moral test of discipline.",
            listOf("Prioritise the few factors relevant to the user's pattern rather than issuing a long generic checklist.", "Escalate persistent or concerning sleep problems instead of endlessly adding habits."),
            listOf("Sleep hygiene alone is not equivalent to clinical treatment for chronic insomnia."),
            setOf("aasm_sleep_duration_2015"),
            setOf(S.NON_DIAGNOSTIC)
        ),

        claim(
            "physical_activity_foundation", setOf(D.PHYSICAL_ACTIVITY, D.EXERCISE),
            "Physical activity includes structured exercise and everyday movement; some activity is better than none, and sedentary time remains relevant.",
            listOf("Distinguish steps and movement from a planned training session.", "Use weekly patterns rather than judging one low-activity day."),
            listOf("Population guidelines are not an automatic prescription for an individual with symptoms or restrictions."),
            setOf("who_physical_activity_2020"),
            setOf(S.AVOID_AUTONOMOUS_EXERCISE_PRESCRIPTION, S.RESPECT_CLINICIAN_RESTRICTIONS)
        ),
        claim(
            "resistance_training_principles", setOf(D.EXERCISE),
            "Resistance-training adaptation depends on an appropriate combination of exercise selection, load, repetitions, sets, frequency, effort and recovery.",
            listOf("Compare like-for-like movements and technique where possible.", "Represent volume as one load descriptor rather than the whole training stimulus."),
            listOf("Trudy should explain principles, not prescribe unsafe loads or override pain and clinical restrictions."),
            setOf("acsm_progression_resistance_2009"),
            setOf(S.AVOID_AUTONOMOUS_EXERCISE_PRESCRIPTION, S.STOP_FOR_CONCERNING_SYMPTOMS)
        ),
        claim(
            "training_fitt_and_load", setOf(D.EXERCISE, D.RECOVERY),
            "Frequency, intensity, time and type describe different parts of training exposure, while internal response can differ despite the same external session.",
            listOf("Do not infer intensity from duration alone.", "Combine session volume or minutes with heart-rate and subjective context when available."),
            listOf("Wearable calories and heart-rate summaries are estimates, not complete measures of training stress."),
            setOf("acsm_exercise_testing_2021", "halson_training_load_2014")
        ),
        claim(
            "progressive_overload", setOf(D.EXERCISE),
            "Progressive overload means gradually increasing an appropriate training demand as adaptation occurs; it does not mean every session must be harder.",
            listOf("Look for progression across suitable time windows.", "Allow stable or lighter sessions when fatigue, technique or recovery warrants them."),
            listOf("Increasing several training variables at once makes response harder to interpret and can increase risk."),
            setOf("acsm_progression_resistance_2009"),
            setOf(S.AVOID_AUTONOMOUS_EXERCISE_PRESCRIPTION)
        ),
        claim(
            "cardiovascular_training_context", setOf(D.EXERCISE, D.HEART_RATE_CONTEXT),
            "Cardiovascular training can be described by modality, duration, frequency and intensity, with intensity estimated using several imperfect methods.",
            listOf("Use pace, power, perceived effort and heart rate together when available.", "Account for heat, hydration, fatigue, medication and sensor quality when heart rate differs."),
            listOf("A heart-rate target should not override concerning symptoms or clinician advice."),
            setOf("acsm_exercise_testing_2021"),
            setOf(S.AVOID_AUTONOMOUS_EXERCISE_PRESCRIPTION, S.STOP_FOR_CONCERNING_SYMPTOMS)
        ),
        claim(
            "heart_rate_zone_limitations", setOf(D.HEART_RATE_CONTEXT, D.EXERCISE),
            "Heart-rate zones based on age-predicted maximum heart rate are approximate; individual maximum, thresholds, medication, environment and device error can shift interpretation.",
            listOf("Identify which zone method was used.", "Prefer personal validated thresholds when legitimately available.", "Treat wrist optical readings cautiously during motion or poor contact."),
            listOf("A racing or unusual pulse with concerning symptoms needs appropriate medical assessment, not zone coaching."),
            setOf("tanaka_max_heart_rate_2001", "acsm_exercise_testing_2021"),
            setOf(S.NON_DIAGNOSTIC, S.STOP_FOR_CONCERNING_SYMPTOMS)
        ),
        claim(
            "resting_heart_rate_context", setOf(D.HEART_RATE_CONTEXT, D.RECOVERY),
            "Resting heart rate is most useful when measured under comparable conditions and interpreted as a personal trend alongside sleep, load, stress and illness context.",
            listOf("Compare similar times and postures where metadata permits.", "Use several observations before describing a shift."),
            listOf("One value does not prove fitness, illness, anxiety or recovery status."),
            setOf("halson_training_load_2014"),
            setOf(S.NON_DIAGNOSTIC, S.AVOID_SINGLE_SCORE_READINESS_CLAIM)
        ),
        claim(
            "recovery_multidomain", setOf(D.RECOVERY, D.SLEEP, D.EXERCISE, D.EMOTIONAL_WELLBEING),
            "Recovery is multidimensional: recent load, sleep, resting heart rate, mood, soreness, nutrition, hydration and environment may all provide context.",
            listOf("Report contributing signals separately before offering an overall interpretation.", "Give data quality and missingness explicit weight."),
            listOf("No single recovery score proves physiological readiness or the need to train or rest."),
            setOf("halson_training_load_2014", "meeusen_overtraining_2013"),
            setOf(S.AVOID_SINGLE_SCORE_READINESS_CLAIM)
        ),
        claim(
            "doms_and_soreness", setOf(D.EXERCISE, D.RECOVERY),
            "Delayed-onset muscle soreness can follow unfamiliar or demanding exercise and does not directly measure workout quality or adaptation.",
            listOf("Distinguish ordinary diffuse soreness from acute pain, injury mechanism, swelling, weakness or functional loss.", "Consider novelty and eccentric loading."),
            listOf("Trudy should not label unusual or severe symptoms as DOMS without assessment."),
            setOf("acsm_progression_resistance_2009"),
            setOf(S.NON_DIAGNOSTIC, S.STOP_FOR_CONCERNING_SYMPTOMS)
        ),
        claim(
            "fatigue_and_overreaching", setOf(D.RECOVERY, D.EXERCISE, D.EMOTIONAL_WELLBEING),
            "Fatigue may be acute and expected, while persistent performance decline and broader symptoms require more cautious interpretation; no single marker identifies overtraining.",
            listOf("Examine duration, trend, performance, sleep, mood and load together.", "Separate a hard session from a persistent change."),
            listOf("Do not diagnose overtraining syndrome from app data."),
            setOf("meeusen_overtraining_2013", "halson_training_load_2014"),
            setOf(S.NON_DIAGNOSTIC, S.AVOID_SINGLE_SCORE_READINESS_CLAIM)
        ),
        claim(
            "warm_up_cool_down", setOf(D.EXERCISE),
            "A warm-up should prepare the body and rehearse the task without creating unnecessary fatigue; cool-down preferences can be contextualised without exaggerated recovery claims.",
            listOf("Match warm-up content to the planned activity.", "Avoid treating one fixed routine as universally necessary."),
            listOf("Warm-up does not make an unsafe activity safe or replace symptom assessment."),
            setOf("bishop_warm_up_2003"),
            setOf(S.AVOID_AUTONOMOUS_EXERCISE_PRESCRIPTION)
        ),
        claim(
            "detraining_context", setOf(D.EXERCISE, D.RECOVERY),
            "Training adaptations decline at different rates during reduced training; a short break does not erase all progress.",
            listOf("Consider prior training history, break length and which quality is being discussed.", "Use a gradual return after longer interruptions or illness."),
            listOf("A previous workload may not be immediately appropriate after a break."),
            setOf("mujika_detraining_2000"),
            setOf(S.AVOID_AUTONOMOUS_EXERCISE_PRESCRIPTION)
        ),
        claim(
            "training_consistency_tracking", setOf(D.EXERCISE, D.EXPERIMENTATION),
            "Consistent measurement definitions matter more than false precision when tracking performance.",
            listOf("Keep exercise naming, range of motion and logging conventions stable.", "Separate missing data from zero activity."),
            listOf("More logged numbers do not automatically create a valid comparison."),
            setOf("acsm_progression_resistance_2009", "cent_n_of_1_2015")
        ),

        claim(
            "heat_humidity_performance", setOf(D.ENVIRONMENT, D.EXERCISE, D.RECOVERY),
            "Heat load reflects environmental heat, humidity, radiant exposure, airflow, clothing and metabolic work; acclimatisation and hydration modify response.",
            listOf("Interpret elevated exercise heart rate or pace alongside heat and humidity.", "Compare similar activities and avoid inferring indoor exposure from outdoor weather alone."),
            listOf("Weather records cannot determine an individual's heat illness risk by themselves."),
            setOf("cdc_niosh_heat_2024"),
            setOf(S.PERSONAL_ASSOCIATION_NOT_CAUSATION, S.STOP_FOR_CONCERNING_SYMPTOMS)
        ),
        claim(
            "air_quality_context", setOf(D.ENVIRONMENT, D.EXERCISE),
            "Air-quality metrics describe specific pollutants or an index over a defined averaging period; source, timestamp and metric identity must be preserved.",
            listOf("Do not treat AQI and PM2.5 as interchangeable.", "Check freshness before describing current conditions.", "Use authoritative local advice for protective actions."),
            listOf("A personal symptom association does not prove that pollution caused the symptom."),
            setOf("who_air_quality_2021"),
            setOf(S.PERSONAL_ASSOCIATION_NOT_CAUSATION, S.NON_DIAGNOSTIC)
        ),
        claim(
            "daylight_activity_context", setOf(D.ENVIRONMENT, D.SLEEP, D.PHYSICAL_ACTIVITY),
            "Daylight duration and weather can provide useful seasonal context for sleep timing and activity, but they do not measure personal light exposure or opportunity.",
            listOf("Compare seasons and schedules, not only adjacent days.", "Keep actual activity and sleep outcomes separate from environmental exposure."),
            listOf("Seasonal association may reflect many unmeasured behavioural changes."),
            setOf("who_physical_activity_2020"),
            setOf(S.PERSONAL_ASSOCIATION_NOT_CAUSATION)
        ),
        claim(
            "cold_environment_context", setOf(D.ENVIRONMENT, D.EXERCISE, D.SLEEP),
            "Cold exposure interacts with wind, wetness, clothing, activity and acclimatisation; outdoor readings do not describe indoor sleep conditions.",
            listOf("Preserve feels-like, wind and precipitation context where available.", "Avoid attributing performance or sleep change to temperature alone."),
            listOf("The app should not autonomously prescribe extreme cold exposure."),
            setOf("cdc_niosh_heat_2024", "okamoto_sleep_thermal_2012"),
            setOf(S.PERSONAL_ASSOCIATION_NOT_CAUSATION)
        ),

        claim(
            "emotional_wellbeing_context", setOf(D.EMOTIONAL_WELLBEING, D.RECOVERY),
            "Mood, calmness, energy and focus are useful self-reported context and may change in both directions with sleep, workload and life events.",
            listOf("Preserve the user's language and the scale's numeric direction.", "Treat self-reports as observations, not objective diagnoses."),
            listOf("App data cannot diagnose anxiety, depression or another mental-health condition."),
            setOf("goyal_meditation_2014"),
            setOf(S.MENTAL_WELLBEING_NOT_DIAGNOSIS)
        ),
        claim(
            "mindfulness_context", setOf(D.MINDFULNESS, D.EMOTIONAL_WELLBEING),
            "Mindfulness programs can modestly improve some stress-related outcomes for some people, but effects and evidence strength vary.",
            listOf("Compare stress before and after repeated sessions while acknowledging expectancy and context.", "Offer brief, optional strategies rather than guaranteed outcomes."),
            listOf("Mindfulness is not a replacement for appropriate mental-health or medical care."),
            setOf("goyal_meditation_2014"),
            setOf(S.MENTAL_WELLBEING_NOT_DIAGNOSIS)
        ),
        claim(
            "slow_breathing_context", setOf(D.BREATHING, D.MINDFULNESS, D.EMOTIONAL_WELLBEING),
            "Slow, comfortable breathing practices can alter autonomic measures and perceived arousal, but evidence varies by technique and study quality.",
            listOf("Describe the exact breathing pattern and duration instead of treating all breathwork as identical.", "Use comfort and perceived response as context."),
            listOf("Do not promise vagal, anxiety or disease treatment effects from a brief exercise."),
            setOf("zaccaro_slow_breathing_2018"),
            setOf(S.MENTAL_WELLBEING_NOT_DIAGNOSIS, S.NON_DIAGNOSTIC)
        ),
        claim(
            "breathing_safety", setOf(D.BREATHING),
            "Breathing exercises should remain gentle and optional; forceful over-breathing, prolonged breath holds or exercises that cause marked dizziness or distress are outside routine wellbeing guidance.",
            listOf("Prefer normal-volume, comfortable pacing.", "Stop when symptoms become concerning rather than trying to complete a target."),
            listOf("Breathing exercises do not replace assessment of unexplained breathlessness, chest pain, fainting or other concerning symptoms."),
            setOf("zaccaro_slow_breathing_2018"),
            setOf(S.STOP_FOR_CONCERNING_SYMPTOMS, S.NON_DIAGNOSTIC)
        ),

        claim(
            "experiment_design_foundation", setOf(D.EXPERIMENTATION),
            "A useful personal experiment specifies a hypothesis, baseline, one reproducible intervention, a primary outcome, duration and important confounders before reviewing results.",
            listOf("Pre-specify the main comparison and outcome.", "Choose reversible, low-risk interventions.", "Record deviations and missing observations."),
            listOf("Not every behaviour or health question is suitable for self-experimentation."),
            setOf("cent_n_of_1_2015"),
            setOf(S.EXPERIMENT_NOT_UNIVERSAL_PROOF)
        ),
        claim(
            "experiment_repeated_measurement", setOf(D.EXPERIMENTATION),
            "Repeated measurements help distinguish a consistent personal signal from normal day-to-day noise, regression to the mean and one-off events.",
            listOf("Use comparable measurement timing and methods.", "Inspect variability, sample count and adherence alongside the average."),
            listOf("A larger number of low-quality measurements does not remove systematic bias."),
            setOf("cent_n_of_1_2015"),
            setOf(S.EXPERIMENT_NOT_UNIVERSAL_PROOF)
        ),
        claim(
            "experiment_confounders_carryover", setOf(D.EXPERIMENTATION),
            "Confounders change alongside an intervention, while carryover means an intervention may continue affecting later periods.",
            listOf("Keep major routines stable when practical.", "Record illness, travel, unusual training, alcohol, schedule and environmental changes.", "Use washout only when it is safe and meaningful."),
            listOf("Adjustment cannot account for important unmeasured influences."),
            setOf("cent_n_of_1_2015"),
            setOf(S.EXPERIMENT_NOT_UNIVERSAL_PROOF)
        ),
        claim(
            "experiment_interpretation_boundary", setOf(D.EXPERIMENTATION),
            "A small personal experiment may inform one person's future choice under similar conditions; it does not prove universal causality or treatment efficacy.",
            listOf("Use supports, did not support or inconclusive rather than proves.", "Report uncertainty and adverse effects alongside the outcome."),
            listOf("Do not generalise one person's result to other people or medical treatment decisions."),
            setOf("cent_n_of_1_2015"),
            setOf(S.EXPERIMENT_NOT_UNIVERSAL_PROOF, S.PERSONAL_ASSOCIATION_NOT_CAUSATION)
        )
    )

    private fun buildTopics(): List<PerformanceTopic> = listOf(
        topic("sleep_duration", "Sleep duration", D.SLEEP, setOf("sleep_duration_context"), sleepDurationMetrics),
        topic("sleep_timing", "Sleep timing and regularity", D.SLEEP, setOf("sleep_timing_regularity", "circadian_light_context"), sleepTimingMetrics, setOf(D.ENVIRONMENT)),
        topic("sleep_continuity", "Sleep continuity and awakenings", D.SLEEP, setOf("sleep_continuity"), sleepContinuityMetrics),
        topic("sleep_stages", "Sleep stages and wearable limitations", D.SLEEP, setOf("sleep_stages_and_wearables"), sleepStageMetrics),
        topic("sleep_debt", "Sleep debt", D.SLEEP, setOf("sleep_debt_concept"), sleepDurationMetrics, setOf(D.RECOVERY)),
        topic("circadian_light", "Circadian rhythm and light", D.SLEEP, setOf("circadian_light_context", "daylight_activity_context"), sleepTimingMetrics + daylightMetrics, setOf(D.ENVIRONMENT), "The current canonical Environment catalog has no direct personal light-exposure or daylight-duration metric; cloud cover and UV are contextual only."),
        topic("caffeine_sleep", "Caffeine and sleep", D.SLEEP, setOf("caffeine_sleep_context"), sleepOutcomeMetrics, setOf(D.EXPERIMENTATION), "Caffeine dose and timing are not yet canonical Data Vault metrics; ask for or record them as experiment exposure metadata."),
        topic("exercise_sleep", "Exercise and sleep", D.SLEEP, setOf("exercise_sleep_context"), exerciseLoadMetrics + sleepOutcomeMetrics, setOf(D.EXERCISE, D.RECOVERY)),
        topic("sleep_environment", "Sleep environment", D.SLEEP, setOf("sleep_thermal_environment", "cold_environment_context"), temperatureMetrics + sleepContinuityMetrics, setOf(D.ENVIRONMENT)),
        topic("sleep_stress_arousal", "Stress, arousal and sleep", D.SLEEP, setOf("sleep_stress_arousal"), emotionalStressMetrics + sleepOutcomeMetrics, setOf(D.EMOTIONAL_WELLBEING, D.RECOVERY)),
        topic("napping", "Napping", D.SLEEP, setOf("napping_context"), sleepTimingMetrics, dataAvailabilityNote = "Naps are represented only when the sleep importer preserves them as separate sleep blocks/sessions."),
        topic("shift_schedule", "Shift and irregular schedules", D.SLEEP, setOf("shift_schedule_context", "circadian_light_context"), sleepTimingMetrics + daylightMetrics, setOf(D.ENVIRONMENT)),
        topic("sleep_hygiene", "Sleep hygiene", D.SLEEP, setOf("sleep_hygiene_context"), sleepOutcomeMetrics),

        topic("resistance_training", "Resistance training", D.EXERCISE, setOf("resistance_training_principles", "progressive_overload", "training_consistency_tracking"), resistanceMetrics),
        topic("cardiovascular_training", "Cardiovascular training", D.EXERCISE, setOf("cardiovascular_training_context", "training_fitt_and_load"), cardioMetrics, setOf(D.HEART_RATE_CONTEXT)),
        topic("training_load", "Training load and volume", D.EXERCISE, setOf("training_fitt_and_load", "resistance_training_principles"), exerciseLoadMetrics, setOf(D.RECOVERY)),
        topic("progressive_overload", "Progressive overload", D.EXERCISE, setOf("progressive_overload", "training_consistency_tracking"), resistanceMetrics),
        topic("training_recovery", "Training recovery and rest", D.RECOVERY, setOf("recovery_multidomain", "fatigue_and_overreaching"), recoveryMetrics, setOf(D.EXERCISE, D.SLEEP, D.EMOTIONAL_WELLBEING)),
        topic("doms", "DOMS and soreness", D.RECOVERY, setOf("doms_and_soreness"), exerciseLoadMetrics, setOf(D.EXERCISE), "Soreness is not currently a canonical numeric metric; treat it as self-report context when available."),
        topic("fatigue", "Fatigue", D.RECOVERY, setOf("fatigue_and_overreaching", "recovery_multidomain"), recoveryMetrics, setOf(D.EMOTIONAL_WELLBEING, D.SLEEP, D.EXERCISE)),
        topic("heart_rate_context", "Heart-rate context", D.HEART_RATE_CONTEXT, setOf("heart_rate_zone_limitations", "resting_heart_rate_context"), heartRateMetrics, setOf(D.EXERCISE, D.RECOVERY)),
        topic("warm_up_cool_down", "Warm-up and cool-down", D.EXERCISE, setOf("warm_up_cool_down"), exerciseLoadMetrics),
        topic("detraining", "Detraining and returning after a break", D.EXERCISE, setOf("detraining_context"), exerciseLoadMetrics, setOf(D.RECOVERY)),
        topic("physical_activity", "Physical activity and steps", D.PHYSICAL_ACTIVITY, setOf("physical_activity_foundation"), activityMetrics, setOf(D.EXERCISE)),
        topic("sedentary_time", "Sedentary time", D.PHYSICAL_ACTIVITY, setOf("physical_activity_foundation"), activityMetrics, dataAvailabilityNote = "The current canonical vault stores steps and exercise minutes, not direct sitting-time observations."),
        topic("performance_tracking", "Performance tracking", D.EXERCISE, setOf("training_consistency_tracking", "experiment_repeated_measurement"), resistanceMetrics + cardioMetrics, setOf(D.EXPERIMENTATION)),
        topic("recovery_readiness", "Recovery and readiness", D.RECOVERY, setOf("recovery_multidomain", "fatigue_and_overreaching"), recoveryMetrics, setOf(D.SLEEP, D.EXERCISE, D.EMOTIONAL_WELLBEING, D.ENVIRONMENT)),

        topic("heat_humidity", "Heat and humidity", D.ENVIRONMENT, setOf("heat_humidity_performance"), heatMetrics + heartRateMetrics, setOf(D.EXERCISE, D.RECOVERY)),
        topic("air_quality", "Air quality", D.ENVIRONMENT, setOf("air_quality_context"), airQualityMetrics, setOf(D.EXERCISE)),
        topic("daylight", "Daylight", D.ENVIRONMENT, setOf("daylight_activity_context", "circadian_light_context"), daylightMetrics + sleepTimingMetrics, setOf(D.SLEEP, D.PHYSICAL_ACTIVITY), "The current canonical Environment catalog has no direct personal light-exposure or daylight-duration metric; do not treat UV or cloud cover as substitutes."),
        topic("cold_environment", "Cold environment", D.ENVIRONMENT, setOf("cold_environment_context"), temperatureMetrics, setOf(D.EXERCISE, D.SLEEP)),
        topic("exercise_environment", "Exercise environment", D.ENVIRONMENT, setOf("heat_humidity_performance", "air_quality_context", "cold_environment_context"), heatMetrics + airQualityMetrics + exerciseLoadMetrics, setOf(D.EXERCISE)),

        topic("emotional_stress", "Stress and emotional arousal", D.EMOTIONAL_WELLBEING, setOf("emotional_wellbeing_context", "sleep_stress_arousal"), emotionalStressMetrics, setOf(D.SLEEP, D.RECOVERY)),
        topic("mood_energy_focus", "Mood, energy and focus", D.EMOTIONAL_WELLBEING, setOf("emotional_wellbeing_context", "recovery_multidomain"), emotionalWellbeingMetrics, setOf(D.RECOVERY)),
        topic("mindfulness", "Mindfulness", D.MINDFULNESS, setOf("mindfulness_context", "emotional_wellbeing_context"), mindfulnessMetrics + emotionalStressMetrics, setOf(D.EMOTIONAL_WELLBEING)),
        topic("breathing_exercises", "Breathing exercises", D.BREATHING, setOf("slow_breathing_context", "breathing_safety"), mindfulnessMetrics, setOf(D.MINDFULNESS, D.EMOTIONAL_WELLBEING), "Breathwork type, pace and duration are not yet first-class canonical metrics; mindfulness session duration is only a fallback when that is how the session was recorded."),

        topic("personal_experiments", "Personal performance experiments", D.EXPERIMENTATION, setOf("experiment_design_foundation", "experiment_repeated_measurement", "experiment_confounders_carryover", "experiment_interpretation_boundary"), emptyList()),
        topic("caffeine_experiment", "Caffeine and sleep experiment", D.EXPERIMENTATION, setOf("caffeine_sleep_context", "experiment_design_foundation", "experiment_repeated_measurement", "experiment_interpretation_boundary"), sleepOutcomeMetrics, setOf(D.SLEEP)),
        topic("sleep_schedule_experiment", "Sleep schedule experiment", D.EXPERIMENTATION, setOf("sleep_timing_regularity", "experiment_design_foundation", "experiment_repeated_measurement"), sleepTimingMetrics + sleepOutcomeMetrics, setOf(D.SLEEP)),
        topic("exercise_timing_experiment", "Exercise timing experiment", D.EXPERIMENTATION, setOf("exercise_sleep_context", "experiment_confounders_carryover", "experiment_interpretation_boundary"), exerciseLoadMetrics + sleepOutcomeMetrics, setOf(D.EXERCISE, D.SLEEP)),
        topic("mindfulness_experiment", "Mindfulness routine experiment", D.EXPERIMENTATION, setOf("mindfulness_context", "experiment_design_foundation", "experiment_interpretation_boundary"), mindfulnessMetrics + emotionalStressMetrics, setOf(D.MINDFULNESS, D.EMOTIONAL_WELLBEING)),
        topic("bedroom_temperature_experiment", "Bedroom temperature and sleep experiment", D.EXPERIMENTATION, setOf("sleep_thermal_environment", "experiment_design_foundation", "experiment_confounders_carryover"), temperatureMetrics + sleepContinuityMetrics, setOf(D.SLEEP, D.ENVIRONMENT), "Use an actual bedroom measurement where possible; outdoor temperature is only contextual.")
    )

    private fun claim(
        id: String,
        domains: Set<D>,
        summary: String,
        rules: List<String>,
        limitations: List<String>,
        sources: Set<String>,
        safety: Set<S> = emptySet()
    ) = PerformanceKnowledgeClaim(id, domains, summary, rules, limitations, sources, safety)

    private fun topic(
        id: String,
        displayName: String,
        primary: D,
        claims: Set<String>,
        metrics: List<PerformanceMetricBinding>,
        related: Set<D> = emptySet(),
        dataAvailabilityNote: String? = null
    ) = PerformanceTopic(id, displayName, primary, related, claims, metrics.distinctBy { it.domain to it.metricId to it.role }, dataAvailabilityNote)

    private fun core(domain: HealthDomain, metric: String, role: PerformanceMetricRole, why: String) =
        PerformanceMetricBinding(domain, metric, role, PerformanceMetricOrigin.CORE_METRIC_REGISTRY, why)

    private fun environment(metric: String, role: PerformanceMetricRole, why: String) =
        PerformanceMetricBinding(HealthDomain.ENVIRONMENT, metric, role, PerformanceMetricOrigin.ENVIRONMENTAL_DOMAIN, why)

    private fun emotional(metric: String, role: PerformanceMetricRole, why: String) =
        PerformanceMetricBinding(HealthDomain.EMOTIONAL, metric, role, PerformanceMetricOrigin.EMOTIONAL_DOMAIN, why)

    private val sleepDurationMetrics = listOf(
        core(HealthDomain.SLEEP, "sleep_total_minutes", PerformanceMetricRole.PRIMARY_OUTCOME, "Estimated total sleep duration."),
        core(HealthDomain.SLEEP, "sleep_start_epoch_ms", PerformanceMetricRole.CONTEXT, "Sleep-window start."),
        core(HealthDomain.SLEEP, "sleep_end_epoch_ms", PerformanceMetricRole.CONTEXT, "Sleep-window end.")
    )
    private val sleepTimingMetrics = listOf(
        core(HealthDomain.SLEEP, "sleep_start_epoch_ms", PerformanceMetricRole.PRIMARY_OUTCOME, "Clock timing of sleep onset/window start."),
        core(HealthDomain.SLEEP, "sleep_end_epoch_ms", PerformanceMetricRole.PRIMARY_OUTCOME, "Clock timing of sleep end."),
        core(HealthDomain.SLEEP, "sleep_block_count", PerformanceMetricRole.DATA_QUALITY, "Shows split or multiple imported sleep blocks.")
    )
    private val sleepContinuityMetrics = listOf(
        core(HealthDomain.SLEEP, "sleep_awake_minutes", PerformanceMetricRole.PRIMARY_OUTCOME, "Estimated awake time within the sleep window."),
        core(HealthDomain.SLEEP, "sleep_interruption_count", PerformanceMetricRole.PRIMARY_OUTCOME, "Count of preserved interruptions."),
        core(HealthDomain.SLEEP, "sleep_longest_interruption_minutes", PerformanceMetricRole.SECONDARY_OUTCOME, "Longest preserved interruption."),
        core(HealthDomain.SLEEP, "sleep_efficiency_pct", PerformanceMetricRole.SECONDARY_OUTCOME, "Derived sleep continuity estimate."),
        core(HealthDomain.SLEEP, "sleep_block_count", PerformanceMetricRole.DATA_QUALITY, "Imported block count can explain split sleep.")
    )
    private val sleepStageMetrics = listOf(
        core(HealthDomain.SLEEP, "sleep_light_minutes", PerformanceMetricRole.CONTEXT, "Wearable-estimated light sleep."),
        core(HealthDomain.SLEEP, "sleep_deep_minutes", PerformanceMetricRole.CONTEXT, "Wearable-estimated deep sleep."),
        core(HealthDomain.SLEEP, "sleep_rem_minutes", PerformanceMetricRole.CONTEXT, "Wearable-estimated REM sleep."),
        core(HealthDomain.SLEEP, "sleep_stage_timeline", PerformanceMetricRole.DATA_QUALITY, "Stage timeline when the source preserves it.")
    )
    private val sleepOutcomeMetrics = (sleepDurationMetrics + sleepContinuityMetrics + listOf(
        core(HealthDomain.SLEEP, "sleep_score", PerformanceMetricRole.SECONDARY_OUTCOME, "App/device composite; never the sole outcome.")
    )).distinctBy { it.domain to it.metricId to it.role }
    private val resistanceMetrics = listOf(
        core(HealthDomain.EXERCISE, "exercise_set", PerformanceMetricRole.EXPOSURE, "Individual recorded resistance-training sets."),
        core(HealthDomain.EXERCISE, "workout_session", PerformanceMetricRole.EXPOSURE, "Recorded workout sessions."),
        core(HealthDomain.EXERCISE, "workout_volume", PerformanceMetricRole.PRIMARY_OUTCOME, "External resistance-training volume.")
    )
    private val cardioMetrics = listOf(
        core(HealthDomain.EXERCISE, "exercise_minutes", PerformanceMetricRole.EXPOSURE, "Recorded activity duration."),
        core(HealthDomain.EXERCISE, "heart_rate_avg_bpm", PerformanceMetricRole.CONTEXT, "Average recorded exercise heart rate."),
        core(HealthDomain.EXERCISE, "heart_rate_max_bpm", PerformanceMetricRole.CONTEXT, "Maximum recorded heart rate."),
        core(HealthDomain.EXERCISE, "calories_burned_active_kcal", PerformanceMetricRole.SECONDARY_OUTCOME, "Wearable-estimated active energy.")
    )
    private val exerciseLoadMetrics = (resistanceMetrics + cardioMetrics + listOf(
        core(HealthDomain.EXERCISE, "steps", PerformanceMetricRole.CONTEXT, "Background movement alongside planned training.")
    )).distinctBy { it.domain to it.metricId to it.role }
    private val activityMetrics = listOf(
        core(HealthDomain.EXERCISE, "steps", PerformanceMetricRole.PRIMARY_OUTCOME, "Daily step count."),
        core(HealthDomain.EXERCISE, "exercise_minutes", PerformanceMetricRole.SECONDARY_OUTCOME, "Recorded exercise duration."),
        core(HealthDomain.EXERCISE, "calories_burned_active_kcal", PerformanceMetricRole.CONTEXT, "Estimated active energy expenditure.")
    )
    private val heartRateMetrics = listOf(
        core(HealthDomain.EXERCISE, "heart_rate_bpm", PerformanceMetricRole.PRIMARY_OUTCOME, "Timestamped heart-rate observations."),
        core(HealthDomain.EXERCISE, "heart_rate_avg_bpm", PerformanceMetricRole.SECONDARY_OUTCOME, "Average heart rate for a summary window."),
        core(HealthDomain.EXERCISE, "heart_rate_min_bpm", PerformanceMetricRole.CONTEXT, "Minimum heart rate for a summary window."),
        core(HealthDomain.EXERCISE, "heart_rate_max_bpm", PerformanceMetricRole.CONTEXT, "Maximum heart rate for a summary window."),
        core(HealthDomain.EXERCISE, "resting_heart_rate_bpm", PerformanceMetricRole.PRIMARY_OUTCOME, "Resting heart-rate trend under comparable conditions.")
    )
    private val emotionalStressMetrics = listOf(
        emotional(EmotionalMetricIds.CALMNESS.value, PerformanceMetricRole.PRIMARY_OUTCOME, "Calm-to-anxious self-report axis."),
        emotional(EmotionalMetricIds.ENERGY.value, PerformanceMetricRole.CONTEXT, "Energetic-to-drained self-report axis."),
        emotional(EmotionalMetricIds.FOCUS.value, PerformanceMetricRole.CONTEXT, "Focused-to-distracted self-report axis.")
    )
    private val emotionalWellbeingMetrics = listOf(
        emotional(EmotionalMetricIds.VALENCE.value, PerformanceMetricRole.PRIMARY_OUTCOME, "Positive-to-negative valence self-report axis."),
        emotional(EmotionalMetricIds.CALMNESS.value, PerformanceMetricRole.SECONDARY_OUTCOME, "Calm-to-anxious self-report axis."),
        emotional(EmotionalMetricIds.ENERGY.value, PerformanceMetricRole.SECONDARY_OUTCOME, "Energetic-to-drained self-report axis."),
        emotional(EmotionalMetricIds.FOCUS.value, PerformanceMetricRole.SECONDARY_OUTCOME, "Focused-to-distracted self-report axis.")
    )
    private val mindfulnessMetrics = listOf(
        core(HealthDomain.MINDFULNESS, "mindfulness_session_minutes", PerformanceMetricRole.EXPOSURE, "Recorded session duration."),
        core(HealthDomain.MINDFULNESS, "stress_before", PerformanceMetricRole.PRIMARY_OUTCOME, "Pre-session stress self-report."),
        core(HealthDomain.MINDFULNESS, "stress_after", PerformanceMetricRole.PRIMARY_OUTCOME, "Post-session stress self-report."),
        core(HealthDomain.MINDFULNESS, "mood_score", PerformanceMetricRole.SECONDARY_OUTCOME, "Recorded post/practice mood context.")
    )
    private val temperatureMetrics = listOf(
        environment(EnvironmentalMetricIds.TEMPERATURE_C, PerformanceMetricRole.EXPOSURE, "Recorded outdoor/local-area air temperature."),
        environment(EnvironmentalMetricIds.FEELS_LIKE_C, PerformanceMetricRole.CONTEXT, "Provider-derived feels-like temperature."),
        environment(EnvironmentalMetricIds.RELATIVE_HUMIDITY_PCT, PerformanceMetricRole.CONTEXT, "Relative humidity context.")
    )
    private val heatMetrics = temperatureMetrics + listOf(
        environment(EnvironmentalMetricIds.WIND_SPEED_MPS, PerformanceMetricRole.CONTEXT, "Wind modifies environmental exposure."),
        environment(EnvironmentalMetricIds.UV_INDEX, PerformanceMetricRole.CONTEXT, "UV exposure context for outdoor activity.")
    )
    private val airQualityMetrics = listOf(
        environment(EnvironmentalMetricIds.EUROPEAN_AQI, PerformanceMetricRole.CONTEXT, "Provider's European air-quality index."),
        environment(EnvironmentalMetricIds.PM2_5_UG_M3, PerformanceMetricRole.EXPOSURE, "PM2.5 concentration."),
        environment(EnvironmentalMetricIds.PM10_UG_M3, PerformanceMetricRole.EXPOSURE, "PM10 concentration.")
    )
    private val daylightMetrics = listOf(
        environment(EnvironmentalMetricIds.CLOUD_COVER_PCT, PerformanceMetricRole.CONTEXT, "Cloud cover is only a rough daylight context."),
        environment(EnvironmentalMetricIds.UV_INDEX, PerformanceMetricRole.CONTEXT, "UV index is not equivalent to circadian light exposure.")
    )
    private val recoveryMetrics = (sleepOutcomeMetrics + exerciseLoadMetrics + heartRateMetrics + emotionalWellbeingMetrics + mindfulnessMetrics + temperatureMetrics + listOf(
        core(HealthDomain.HYDRATION, "water_total_l", PerformanceMetricRole.CONTEXT, "Recorded daily hydration total."),
        core(HealthDomain.NUTRITION, "food_kcal", PerformanceMetricRole.CONTEXT, "Recorded energy intake context.")
    )).distinctBy { it.domain to it.metricId to it.role }

    val topics: List<PerformanceTopic> = buildTopics()

    private val claimsById = claims.associateBy { it.id }
    private val topicsById = topics.associateBy { it.id }

    init {
        require(claimsById.size == claims.size) { "Performance claim IDs must be unique" }
        require(topicsById.size == topics.size) { "Performance topic IDs must be unique" }
        val sourceIds = PerformanceEvidenceCatalog.sources.map { it.id }.toSet()
        claims.forEach { claim -> require(claim.evidenceSourceIds.all { it in sourceIds }) { "Unknown source on ${claim.id}" } }
        topics.forEach { topic -> require(topic.claimIds.all { it in claimsById }) { "Unknown claim on ${topic.id}" } }
    }

    fun claim(id: String): PerformanceKnowledgeClaim? = claimsById[id]
    fun topic(id: String): PerformanceTopic? = topicsById[id]

}
