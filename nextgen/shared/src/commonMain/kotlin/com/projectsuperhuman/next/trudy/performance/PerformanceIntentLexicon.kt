package com.projectsuperhuman.next.trudy.performance

/**
 * Normalised semantic groups keep slang and spelling variants out of the planner. Adding a phrase
 * does not add a new routing branch; it enriches one reusable topic group.
 */
object PerformanceIntentLexicon {
    val groups: List<PerformancePhraseGroup> = listOf(
        group("sleep_general", setOf("sleep_duration", "sleep_continuity"), 1,
            "sleep", "slept", "my sleep", "sleep quality", "good night sleep", "bad night sleep"),
        group("sleep_bad", setOf("sleep_duration", "sleep_continuity", "sleep_stress_arousal"), 6,
            "i slept like crap", "i slept like shit", "slept terribly", "slept awful", "rough night", "bad nights sleep",
            "terrible sleep", "sleep was rubbish", "sleep sucked", "tossed and turned", "couldnt sleep", "cant sleep"),
        group("sleep_fragmented", setOf("sleep_continuity"), 8,
            "kept waking up", "woke up loads", "waking up constantly", "waking up all night", "broken sleep",
            "fragmented sleep", "restless sleep", "awake all night", "multiple awakenings", "waso", "sleep interruptions"),
        group("sleep_short", setOf("sleep_duration", "sleep_debt"), 7,
            "not enough sleep", "barely slept", "only slept", "short sleep", "sleep deprived", "sleep deprivation",
            "running on no sleep", "lost sleep", "catch up sleep", "sleep debt", "sleep deficit"),
        group("sleep_timing", setOf("sleep_timing", "circadian_light"), 6,
            "bed time", "bedtime", "wake time", "sleep schedule", "sleep routine", "late night", "went to bed late",
            "slept in", "lie in", "all nighter", "pulled an all nighter", "body clock", "circadian", "jet lag"),
        group("sleep_stages", setOf("sleep_stages"), 8,
            "sleep stages", "deep sleep", "rem sleep", "light sleep", "sleep cycle", "sleep architecture",
            "polysomnography", "psg", "watch sleep", "wearable sleep", "sleep tracker accuracy"),
        group("naps", setOf("napping", "sleep_timing"), 7,
            "nap", "naps", "napped", "daytime sleep", "power nap", "siesta"),
        group("shift_work", setOf("shift_schedule", "circadian_light"), 8,
            "night shift", "shift work", "rotating shift", "early shift", "late shift", "work nights", "irregular shifts"),
        group("sleep_hygiene", setOf("sleep_hygiene"), 7,
            "sleep hygiene", "wind down", "night routine", "bedroom routine", "sleep habits", "how to sleep better"),
        group("wired_arousal", setOf("sleep_stress_arousal", "emotional_stress"), 8,
            "i feel wired", "wired but tired", "cant switch off", "mind racing", "brain wont switch off", "too alert to sleep",
            "on edge", "keyed up", "restless and tired", "adrenaline at night"),
        group("caffeine_sleep", setOf("caffeine_sleep"), 8,
            "caffeine and sleep", "coffee and sleep", "late coffee", "caffeine cut off", "caffeine cutoff", "pre workout sleep",
            "energy drink sleep", "does caffeine affect sleep"),

        group("exercise_general", setOf("training_load", "performance_tracking"), 2,
            "exercise", "training", "workout", "work out", "gym", "fitness", "trained"),
        group("hard_workout_slang", setOf("training_load", "training_recovery", "fatigue"), 8,
            "i smashed the gym", "smashed the gym", "crushed my workout", "killed my workout", "big gym session",
            "brutal workout", "hard session", "trained hard", "went beast mode", "workout destroyed me", "gym destroyed me",
            "absolutely gassed", "cooked after training", "wrecked after gym"),
        group("resistance_training", setOf("resistance_training", "progressive_overload"), 7,
            "resistance training", "strength training", "weight training", "lifting", "weights", "reps", "sets",
            "one rep max", "1rm", "personal best", "pb", "personal record", "pr", "hypertrophy"),
        group("cardio_training", setOf("cardiovascular_training", "heart_rate_context"), 7,
            "cardio", "running", "jogging", "cycling", "swimming", "aerobic", "conditioning", "hiit", "liss",
            "zone 2", "zone two", "intervals", "vo2 max"),
        group("training_volume", setOf("training_load"), 8,
            "training volume", "workout volume", "exercise load", "training load", "tonnage", "weekly sets",
            "session rpe", "rpe", "intensity", "frequency"),
        group("progressive_overload", setOf("progressive_overload"), 9,
            "progressive overload", "add weight", "more reps", "getting stronger", "strength progress", "stalled lift", "plateau"),
        group("doms_soreness", setOf("doms", "training_recovery"), 9,
            "doms", "delayed onset muscle soreness", "muscle soreness", "sore after gym", "aching after workout",
            "legs are sore", "arms are sore", "workout soreness"),
        group("fatigue_slang", setOf("fatigue", "recovery_readiness", "mood_energy_focus"), 8,
            "im shattered", "im knackered", "im drained", "im exhausted", "im wiped", "wiped out", "im wrecked",
            "im beat", "dead tired", "no energy", "low energy", "running on empty", "feel cooked", "feeling fatigued"),
        group("recovery", setOf("recovery_readiness", "training_recovery"), 8,
            "recovery", "readiness", "am i recovered", "should i rest", "rest day", "need a rest", "under recovered",
            "poor recovery", "recovery score", "ready to train", "deload"),
        group("warm_up", setOf("warm_up_cool_down"), 8,
            "warm up", "warmup", "cool down", "cooldown", "mobility before training", "prepare for workout"),
        group("detraining", setOf("detraining"), 8,
            "detraining", "lost fitness", "lost my gains", "time off gym", "break from training", "return to training",
            "back to gym", "havent trained", "haven t trained"),
        group("low_activity", setOf("physical_activity", "sedentary_time"), 8,
            "ive been sitting around all day", "sitting around all day", "barely moved", "didnt move", "inactive",
            "sedentary", "couch potato", "stuck at desk", "desk all day", "low step day", "no steps"),
        group("steps_walking", setOf("physical_activity"), 7,
            "steps", "step count", "walking", "walked", "daily movement", "physical activity", "active minutes"),
        group("performance_tracking", setOf("performance_tracking"), 7,
            "track progress", "performance trend", "getting fitter", "training consistency", "workout consistency",
            "exercise history", "training history", "am i improving"),

        group("heart_rate", setOf("heart_rate_context"), 7,
            "heart rate", "heartrate", "hr", "bpm", "pulse", "resting heart rate", "rhr", "heart rate zone", "hr zone"),
        group("racing_pulse", setOf("heart_rate_context", "emotional_stress"), 10,
            "my pulse is racing", "heart is racing", "racing heart", "heart pounding", "pulse pounding", "heart going mad",
            "heart beating fast", "high pulse", "high heart rate", "palpitations"),

        group("heat", setOf("heat_humidity", "exercise_environment"), 8,
            "hot weather", "heat", "heatwave", "too hot", "training in heat", "exercise in heat", "hot run", "heat stress"),
        group("hot_bedroom", setOf("sleep_environment", "heat_humidity"), 10,
            "hot bedroom", "bedroom too hot", "hot room", "sleeping in heat", "too hot to sleep", "sweaty night"),
        group("humidity", setOf("heat_humidity", "sleep_environment"), 8,
            "humidity", "humid", "muggy", "sticky weather", "dry air", "bedroom humidity"),
        group("air_quality", setOf("air_quality", "exercise_environment"), 9,
            "air quality", "aqi", "pm2 5", "pm 2 5", "pm10", "pm 10", "pollution", "smog", "bad air"),
        group("daylight", setOf("daylight", "circadian_light"), 8,
            "daylight", "sunlight", "light exposure", "morning light", "dark mornings", "short days", "long days", "seasonal light"),
        group("cold", setOf("cold_environment", "exercise_environment"), 8,
            "cold weather", "freezing", "training in cold", "running in cold", "cold bedroom", "wind chill", "icy weather"),
        group("weather_general", setOf("exercise_environment", "sleep_environment"), 4,
            "weather", "environment", "environmental conditions", "weather affecting me"),

        group("stress", setOf("emotional_stress", "sleep_stress_arousal", "recovery_readiness"), 9,
            "im stressed out", "stressed out", "stressed", "stress", "overwhelmed", "under pressure", "tense",
            "anxious feelings", "feeling anxious", "on edge", "cant relax", "burnt out", "burned out"),
        group("mood", setOf("mood_energy_focus"), 7,
            "mood", "feeling low", "feeling good", "happy", "sad", "irritable", "grumpy", "emotional wellbeing"),
        group("brain_fog", setOf("mood_energy_focus", "fatigue", "sleep_duration"), 9,
            "my head feels foggy", "head feels foggy", "brain fog", "foggy headed", "cant focus", "poor concentration",
            "distracted", "mentally drained", "sluggish thinking"),
        group("mindfulness", setOf("mindfulness"), 8,
            "mindfulness", "meditation", "meditate", "body scan", "present moment", "grounding exercise", "relaxation practice"),
        group("breathing", setOf("breathing_exercises"), 8,
            "breathing exercise", "breathwork", "breath work", "slow breathing", "paced breathing", "box breathing",
            "deep breathing", "belly breathing", "diaphragmatic breathing", "breathing routine", "breath hold"),

        group("experiment_general", setOf("personal_experiments"), 8,
            "experiment", "personal experiment", "self experiment", "n of 1", "n=1", "hypothesis", "baseline",
            "intervention", "outcome", "confounder", "sample size", "measurement noise", "repeated measures"),
        group("test_caffeine", setOf("caffeine_experiment", "caffeine_sleep"), 10,
            "test whether caffeine affects sleep", "test caffeine and sleep", "caffeine experiment", "coffee experiment",
            "does coffee ruin my sleep", "find my caffeine cut off"),
        group("test_sleep_schedule", setOf("sleep_schedule_experiment", "sleep_timing"), 10,
            "test sleep schedule", "sleep consistency experiment", "bedtime experiment", "consistent wake time experiment"),
        group("test_exercise_timing", setOf("exercise_timing_experiment", "exercise_sleep"), 10,
            "test exercise timing", "workout timing experiment", "late workout sleep", "does evening exercise affect my sleep"),
        group("test_mindfulness", setOf("mindfulness_experiment", "mindfulness"), 10,
            "mindfulness experiment", "test meditation", "does meditation help my stress", "test breathing routine"),
        group("test_temperature", setOf("bedroom_temperature_experiment", "sleep_environment"), 10,
            "temperature sleep experiment", "test bedroom temperature", "does heat affect my sleep", "cooler bedroom experiment")
    )

    private val byId = groups.associateBy { it.id }

    init {
        require(byId.size == groups.size) { "Performance phrase-group IDs must be unique" }
        val topics = PerformanceKnowledgeCatalog.topics.map { it.id }.toSet()
        groups.forEach { group -> require(group.topicIds.all { it in topics }) { "Unknown topic on ${group.id}" } }
    }

    fun group(id: String): PerformancePhraseGroup? = byId[id]

    private fun group(id: String, topics: Set<String>, priority: Int, vararg aliases: String) =
        PerformancePhraseGroup(id, topics, aliases.toSet(), priority)
}

object PerformanceTextNormalizer {
    private val spellingAliases = mapOf(
        "behavior" to "behaviour",
        "behaviors" to "behaviours",
        "analyze" to "analyse",
        "analyzing" to "analysing",
        "center" to "centre",
        "excersize" to "exercise",
        "excercise" to "exercise",
        "exersize" to "exercise",
        "exhaustion" to "fatigue",
        "meditating" to "meditate",
        "workouts" to "workout"
    )

    private val compounds = mapOf(
        "heartrate" to listOf("heart", "rate"),
        "bedtime" to listOf("bed", "time"),
        "nightshift" to listOf("night", "shift"),
        "breathwork" to listOf("breath", "work")
    )

    fun normalize(text: String): String {
        val punctuationNormalised = text.lowercase()
            .replace('’', '\'')
            .replace("'", "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        if (punctuationNormalised.isEmpty()) return ""
        return punctuationNormalised.split(Regex("\\s+"))
            .flatMap { token -> compounds[token] ?: listOf(spellingAliases[token] ?: token) }
            .joinToString(" ")
    }
}
