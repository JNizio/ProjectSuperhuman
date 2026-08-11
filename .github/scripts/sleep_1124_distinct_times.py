from pathlib import Path

root = Path('.')
hc = root / 'nextgen/androidApp/src/main/java/com/projectsuperhuman/next/SleepHealthConnect.kt'
parity = root / 'nextgen/androidApp/src/main/java/com/projectsuperhuman/next/NativeSleepParity.kt'
hero = root / 'nextgen/androidApp/src/main/java/com/projectsuperhuman/next/SleepNightDashboardHero.kt'
gradle = root / 'nextgen/androidApp/build.gradle.kts'

s = hc.read_text()
s = s.replace(
'''    private data class Breakdown(val awake: Int, val light: Int, val deep: Int, val rem: Int) {
        val stagedSleep: Int get() = light + deep + rem
    }''',
'''    private data class Breakdown(val awake: Int, val light: Int, val deep: Int, val rem: Int, val unknown: Int) {
        val stagedSleep: Int get() = light + deep + rem
        val stagedWindow: Int get() = awake + light + deep + rem + unknown
    }'''
)
s = s.replace(
'''        val asleepMinutes: Int,
        val awakeMinutes: Int,''',
'''        val asleepMinutes: Int,
        val sleepTimeMinutes: Int,
        val awakeMinutes: Int,'''
)
s = s.replace(
'''        var rem = 0
        var asleep = 0''',
'''        var rem = 0
        var unknown = 0
        var asleep = 0'''
)
s = s.replace(
'''            rem += breakdown.rem

            val encoded = encodeStages(session)''',
'''            rem += breakdown.rem
            unknown += breakdown.unknown

            val encoded = encodeStages(session)'''
)
s = s.replace(
'''            asleepMinutes = asleep,
            awakeMinutes = awake,''',
'''            asleepMinutes = asleep,
            // Samsung-style sleep time is the complete staged interval inside the
            // sleep records. Unknown intervals are real source time and must not be
            // silently discarded just because they cannot be classified as a stage.
            sleepTimeMinutes = if (ordered.any { it.stages.isNotEmpty() }) {
                (asleep + awake + unknown).coerceAtLeast(asleep)
            } else asleep,
            awakeMinutes = awake,'''
)
s = s.replace(
'''        var rem = 0L
        session.stages.forEach { stage ->''',
'''        var rem = 0L
        var unknown = 0L
        session.stages.forEach { stage ->'''
)
s = s.replace(
'''                SleepSessionRecord.STAGE_TYPE_REM -> rem += min
            }
        }
        return Breakdown(awake.toInt(), light.toInt(), deep.toInt(), rem.toInt())''',
'''                SleepSessionRecord.STAGE_TYPE_REM -> rem += min
                SleepSessionRecord.STAGE_TYPE_UNKNOWN -> unknown += min
                else -> unknown += min
            }
        }
        return Breakdown(awake.toInt(), light.toInt(), deep.toInt(), rem.toInt(), unknown.toInt())'''
)
s = s.replace(
'''            else -> "light"
        }''',
'''            SleepSessionRecord.STAGE_TYPE_LIGHT,
            SleepSessionRecord.STAGE_TYPE_SLEEPING -> "light"
            else -> "unknown"
        }'''
)
s = s.replace(
'''                        addMetric("sleep_total_minutes", summary.asleepMinutes.toDouble(), "min")
                        addMetric("sleep_awake_minutes", summary.awakeMinutes.toDouble(), "min")''',
'''                        addMetric("sleep_total_minutes", summary.asleepMinutes.toDouble(), "min")
                        addMetric("sleep_time_minutes", summary.sleepTimeMinutes.toDouble(), "min")
                        addMetric("sleep_awake_minutes", summary.awakeMinutes.toDouble(), "min")'''
)
hc.write_text(s)

p = parity.read_text()
p = p.replace(
'''    val totalMinutes: Int? = null,
    val awakeMinutes: Int? = null,''',
'''    val totalMinutes: Int? = null,
    val sleepTimeMinutes: Int? = null,
    val awakeMinutes: Int? = null,'''
)
p = p.replace(
'''            totalMinutes = metric("sleep_total_minutes")?.value?.roundToInt(),
            awakeMinutes = metric("sleep_awake_minutes")?.value?.roundToInt(),''',
'''            totalMinutes = metric("sleep_total_minutes")?.value?.roundToInt(),
            sleepTimeMinutes = metric("sleep_time_minutes")?.value?.roundToInt(),
            awakeMinutes = metric("sleep_awake_minutes")?.value?.roundToInt(),'''
)
parity.write_text(p)

h = hero.read_text()
h = h.replace(
'''    val sleepTime = total?.let { it + (s.awakeMinutes ?: 0) }''',
'''    val sleepTime = s?.sleepTimeMinutes ?: total?.let { it + (s.awakeMinutes ?: 0) }'''
)
hero.write_text(h)

g = gradle.read_text()
g = g.replace('versionCode = 11203', 'versionCode = 11204')
g = g.replace('versionName = "11.2.3"', 'versionName = "11.2.4"')
gradle.write_text(g)
