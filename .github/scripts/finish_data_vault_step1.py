from pathlib import Path

root = Path('.')

sleep = root / 'nextgen/androidApp/src/main/java/com/projectsuperhuman/next/NativeSleepParity.kt'
history = root / 'nextgen/androidApp/src/main/java/com/projectsuperhuman/next/SleepHistoryPage.kt'
repo = root / 'nextgen/shared/src/commonMain/kotlin/com/projectsuperhuman/next/data/SqlHealthRepository.kt'
hub = root / 'nextgen/androidApp/src/main/java/com/projectsuperhuman/next/NativeDataHub.kt'

# 1) Never scan the whole Data Vault just to build the sleep module.
s = sleep.read_text()
old = 'val all = NativeDataHub.allValuesAsync().filter { it.domain == HealthDomain.SLEEP }'
new = 'val all = NativeDataHub.domainBetween(HealthDomain.SLEEP, 0L, Long.MAX_VALUE)'
if old not in s:
    raise SystemExit('NativeSleepParity allValues hot path not found')
s = s.replace(old, new, 1)
sleep.write_text(s)

h = history.read_text()
old = '''val values = NativeDataHub.allValuesAsync()
            .filter { it.domain == com.projectsuperhuman.next.core.HealthDomain.SLEEP }
            .filter { it.metric.startsWith("sleep_") }'''
new = '''val values = NativeDataHub.domainBetween(
            com.projectsuperhuman.next.core.HealthDomain.SLEEP,
            0L,
            Long.MAX_VALUE
        ).filter { it.metric.startsWith("sleep_") }'''
if old not in h:
    raise SystemExit('SleepHistoryPage allValues hot path not found')
h = h.replace(old, new, 1)
history.write_text(h)

# 2) Expose cheap indexed diagnostics for future scale monitoring.
r = repo.read_text()
needle = '''    fun count(domain: HealthDomain): Long = q.countValuesForDomain(domain.name).executeAsOne()

    /** Indexed targeted delete instead of clearing and rewriting the entire archive. */'''
replacement = '''    fun count(domain: HealthDomain): Long = q.countValuesForDomain(domain.name).executeAsOne()

    fun latestTimestamp(domain: HealthDomain): Long? =
        q.latestTimestampForDomain(domain.name).executeAsOne()

    /** Indexed targeted delete instead of clearing and rewriting the entire archive. */'''
if needle not in r:
    raise SystemExit('SqlHealthRepository diagnostics insertion point not found')
r = r.replace(needle, replacement, 1)
repo.write_text(r)

n = hub.read_text()
needle = '''internal object NativeDataHub {
    private lateinit var repository: SqlHealthRepository
'''
replacement = '''internal data class DataVaultDiagnostics(
    val totalRecords: Long,
    val recordsByDomain: Map<HealthDomain, Long>,
    val latestTimestampByDomain: Map<HealthDomain, Long?>
)

internal object NativeDataHub {
    private lateinit var repository: SqlHealthRepository
'''
if needle not in n:
    raise SystemExit('NativeDataHub diagnostics type insertion point not found')
n = n.replace(needle, replacement, 1)
needle = '''    suspend fun storedValueCount(domain: HealthDomain): Long = withContext(Dispatchers.IO) {
        repository.count(domain)
    }

    suspend fun restoreValues'''
replacement = '''    suspend fun storedValueCount(domain: HealthDomain): Long = withContext(Dispatchers.IO) {
        repository.count(domain)
    }

    /** Cheap indexed health check; never materialises stored HealthValue rows. */
    suspend fun diagnostics(): DataVaultDiagnostics = withContext(Dispatchers.IO) {
        val counts = HealthDomain.entries.associateWith { repository.count(it) }
        val latest = HealthDomain.entries.associateWith { repository.latestTimestamp(it) }
        DataVaultDiagnostics(
            totalRecords = repository.count(),
            recordsByDomain = counts,
            latestTimestampByDomain = latest
        )
    }

    suspend fun restoreValues'''
if needle not in n:
    raise SystemExit('NativeDataHub diagnostics method insertion point not found')
n = n.replace(needle, replacement, 1)
hub.write_text(n)

print('Step 1 Data Vault finishing patch applied successfully')
