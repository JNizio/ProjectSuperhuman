from pathlib import Path

root = Path('.')
sleep = root / 'nextgen/androidApp/src/main/java/com/projectsuperhuman/next/NativeSleepParity.kt'
history = root / 'nextgen/androidApp/src/main/java/com/projectsuperhuman/next/SleepHistoryPage.kt'

# Latest Sleep screen only needs recent series for its baselines. Never materialise
# years of sleep rows just to render one dashboard.
s = sleep.read_text()
old_candidates = [
    'val all = NativeDataHub.allValuesAsync().filter { it.domain == HealthDomain.SLEEP }',
    'val all = NativeDataHub.domainBetween(HealthDomain.SLEEP, 0L, Long.MAX_VALUE)'
]
replacement = '''val totalSeries = NativeDataHub.pageForMetric(
            HealthDomain.SLEEP, "sleep_total_minutes", limit = 32
        )
        val scoreSeries = NativeDataHub.pageForMetric(
            HealthDomain.SLEEP, "sleep_score", limit = 32
        )'''
replaced = False
for old in old_candidates:
    if old in s:
        s = s.replace(old, replacement, 1)
        replaced = True
        break
if not replaced and 'val totalSeries = NativeDataHub.pageForMetric(' not in s:
    raise SystemExit('NativeSleepParity recent-series insertion point not found')
# Remove the old derived lists if they still follow the new block.
s = s.replace('''        val totalSeries = all.filter { it.metric == "sleep_total_minutes" }
        val scoreSeries = all.filter { it.metric == "sleep_score" }
''', '', 1)
sleep.write_text(s)

# Historical Sleep is allowed to read its domain, but never the entire cross-module
# archive. Nightly sleep metrics are low-frequency and remain bounded by the SLEEP index.
h = history.read_text()
old = '''val values = NativeDataHub.allValuesAsync()
            .filter { it.domain == com.projectsuperhuman.next.core.HealthDomain.SLEEP }
            .filter { it.metric.startsWith("sleep_") }'''
new = '''val values = NativeDataHub.domainBetween(
            com.projectsuperhuman.next.core.HealthDomain.SLEEP,
            0L,
            Long.MAX_VALUE
        ).filter { it.metric.startsWith("sleep_") }'''
if old in h:
    h = h.replace(old, new, 1)
elif 'NativeDataHub.domainBetween(' not in h:
    raise SystemExit('SleepHistoryPage scoped-history query not found')
history.write_text(h)

print('Step 1 Data Vault finishing patch applied successfully')
