from pathlib import Path

path = Path('nextgen/androidApp/src/main/java/com/projectsuperhuman/next/HomeMiniMetrics.kt')
text = path.read_text()

# Lifecycle-aware foreground polling without introducing another Compose lifecycle dependency.
if 'import android.content.ContextWrapper' not in text:
    text = text.replace('package com.projectsuperhuman.next\n\n', 'package com.projectsuperhuman.next\n\nimport android.content.Context\nimport android.content.ContextWrapper\n', 1)
if 'import androidx.compose.runtime.DisposableEffect' not in text:
    text = text.replace('import androidx.compose.runtime.Composable\n', 'import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.DisposableEffect\n', 1)
if 'import androidx.lifecycle.Lifecycle' not in text:
    text = text.replace('import androidx.health.connect.client.PermissionController\n', 'import androidx.health.connect.client.PermissionController\nimport androidx.lifecycle.Lifecycle\nimport androidx.lifecycle.LifecycleEventObserver\nimport androidx.lifecycle.LifecycleOwner\n', 1)

text = text.replace('val steps7dAverage: Int? = null,', 'val stepsRecentAverage: Int? = null,')
text = text.replace('metrics.steps7dAverage?.let { "7d avg ${compactCount(it)}" }', 'metrics.stepsRecentAverage?.let { "recent avg ${compactCount(it)}" }')
text = text.replace('val steps7dAverage = latestStepPerDay.takeIf { it.isNotEmpty() }?.map { it.value }?.average()?.roundToInt()', '''val completedStepDays = latestStepPerDay.filter { it.metadata["summaryDate"] != today.toString() }
    val stepsRecentAverage = completedStepDays.takeIf { it.isNotEmpty() }?.map { it.value }?.average()?.roundToInt()''')
text = text.replace('steps7dAverage = steps7dAverage,', 'stepsRecentAverage = stepsRecentAverage,')

constants = '''private val MiniStress = Color(0xFF7260BF)\n'''
helper = r'''private val MiniStress = Color(0xFF7260BF)

private fun Context.findLifecycleOwner(): LifecycleOwner? {
    var current: Context? = this
    while (current != null) {
        if (current is LifecycleOwner) return current
        current = (current as? ContextWrapper)?.baseContext
    }
    return null
}

@Composable
private fun rememberMiniMetricsForeground(): Boolean {
    val context = LocalContext.current
    val owner = remember(context) { context.findLifecycleOwner() }
    var resumed by remember(owner) {
        mutableStateOf(owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true)
    }

    DisposableEffect(owner) {
        if (owner == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> resumed = false
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return resumed
}
'''
if 'private fun rememberMiniMetricsForeground()' not in text:
    if constants not in text:
        raise SystemExit('Mini constants anchor not found')
    text = text.replace(constants, helper, 1)

old_home = '''internal fun HomeMiniMetricsGrid(openMetric: (HomeMiniMetric) -> Unit) {
    val context = LocalContext.current
    var metrics by remember { mutableStateOf(MiniMetricSnapshot()) }

    LaunchedEffect(Unit) {
        metrics = loadMiniMetricSnapshot()
        if (MiniMetricsHealthConnect.hasAnyPermission(context)) {
            MiniMetricsHealthConnect.sync(context)
            metrics = loadMiniMetricSnapshot()
            while (true) {
                delay(30_000L)
                MiniMetricsHealthConnect.syncCurrent(context)
                metrics = loadMiniMetricSnapshot()
            }
        }
    }'''
new_home = '''internal fun HomeMiniMetricsGrid(openMetric: (HomeMiniMetric) -> Unit) {
    val context = LocalContext.current
    val isForeground = rememberMiniMetricsForeground()
    var metrics by remember { mutableStateOf(MiniMetricSnapshot()) }

    LaunchedEffect(isForeground) {
        metrics = loadMiniMetricSnapshot()
        if (!isForeground) return@LaunchedEffect
        if (MiniMetricsHealthConnect.hasAnyPermission(context)) {
            // Reconcile history whenever the app becomes active, then poll only while resumed.
            MiniMetricsHealthConnect.sync(context)
            metrics = loadMiniMetricSnapshot()
            while (true) {
                delay(30_000L)
                MiniMetricsHealthConnect.syncCurrent(context)
                metrics = loadMiniMetricSnapshot()
            }
        }
    }'''
if old_home not in text:
    raise SystemExit('Foreground home polling block not found')
text = text.replace(old_home, new_home, 1)

old_detail_context = '''internal fun NativeMiniMetricPlaceholderPage(metric: HomeMiniMetric, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()'''
new_detail_context = '''internal fun NativeMiniMetricPlaceholderPage(metric: HomeMiniMetric, onBack: () -> Unit) {
    val context = LocalContext.current
    val isForeground = rememberMiniMetricsForeground()
    val scope = rememberCoroutineScope()'''
if old_detail_context not in text:
    raise SystemExit('Detail context anchor not found')
text = text.replace(old_detail_context, new_detail_context, 1)

text = text.replace('LaunchedEffect(metric, connected) {\n        if (!connected || metric == HomeMiniMetric.STRESS) return@LaunchedEffect', 'LaunchedEffect(metric, connected, isForeground) {\n        if (!connected || metric == HomeMiniMetric.STRESS || !isForeground) return@LaunchedEffect', 1)

path.write_text(text)
