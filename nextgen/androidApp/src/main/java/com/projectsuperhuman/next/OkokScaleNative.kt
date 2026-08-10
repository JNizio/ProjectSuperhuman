package com.projectsuperhuman.next

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.projectsuperhuman.next.core.HealthDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.round

private const val TARGET_SCALE_MAC = "28:FA:7A:4D:42:59"
private const val TARGET_SCALE_NAME = "Bluetooth Scale1"

internal data class OkokMeasurement(val weightKg: Double, val impedanceOhm: Double?, val stable: Boolean, val timestamp: Long)
internal data class OkokBiaResult(
    val bodyFatPct: Double, val fatMassKg: Double, val fatFreeMassKg: Double,
    val waterPct: Double, val totalBodyWaterL: Double, val musclePct: Double,
    val muscleMassKg: Double, val skeletalMusclePct: Double, val skeletalMuscleMassKg: Double,
    val visceralFatEstimate: Double, val bmi: Double, val ffmi: Double, val fmi: Double
)

internal object OkokScaleManager {
    var status by mutableStateOf("Ready — step on your scale"); private set
    var listening by mutableStateOf(false); private set
    var measurement by mutableStateOf<OkokMeasurement?>(null); private set
    var lastSavedAt by mutableStateOf<Long?>(null); private set

    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var callback: ScanCallback? = null
    private var restartJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var lastWeight: Double? = null
    private var stableCount = 0
    private var lastSeenAt = 0L
    private var lastImpedance: Double? = null
    private var profileHeightCm: Double? = null
    private var profileMale: Boolean? = null
    private var lastSavedWeight: Double? = null
    private var lastSavedImpedance: Double? = null

    fun setProfile(heightCm: Double?, male: Boolean?) { profileHeightCm = heightCm; profileMale = male }
    fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    fun hasPermissions(context: Context): Boolean = requiredPermissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission")
    fun startAutoTracking(context: Context, onSaved: () -> Unit = {}) {
        if (listening) return
        if (!hasPermissions(context)) { status = "Bluetooth permission needed"; return }
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null) { status = "Bluetooth LE unavailable"; return }
        if (!adapter.isEnabled) { status = "Turn Bluetooth on to auto-track"; return }
        scanner = adapter.bluetoothLeScanner
        if (scanner == null) { status = "Bluetooth scanner unavailable"; return }
        resetTransient(); listening = true; status = "Ready — step on your scale"
        callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) { consume(context, result, onSaved) }
            override fun onScanFailed(errorCode: Int) { listening = false; status = "Scale connection paused"; scheduleRestart(context, onSaved) }
        }
        scanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
    }

    @SuppressLint("MissingPermission")
    fun stopAutoTracking() {
        restartJob?.cancel(); restartJob = null
        try { callback?.let { scanner?.stopScan(it) } } catch (_: Exception) {}
        callback = null; listening = false
    }

    private fun scheduleRestart(context: Context, onSaved: () -> Unit) {
        restartJob?.cancel(); restartJob = scope.launch { delay(2500); startAutoTracking(context, onSaved) }
    }
    private fun resetTransient() { lastWeight = null; stableCount = 0; lastSeenAt = 0L; lastImpedance = null }

    @SuppressLint("MissingPermission")
    private fun consume(context: Context, result: ScanResult, onSaved: () -> Unit) {
        val mac = try { result.device?.address.orEmpty().uppercase() } catch (_: SecurityException) { "" }
        if (mac != TARGET_SCALE_MAC) return
        val raw = result.scanRecord?.bytes ?: return
        val weight = decodeWeight(raw) ?: return
        val impedance = decodeImpedance(raw)
        val now = System.currentTimeMillis()
        stableCount = if (lastWeight != null && abs(weight - lastWeight!!) <= 0.12 && now - lastSeenAt < 3500) stableCount + 1 else 1
        lastWeight = weight; lastSeenAt = now; if (impedance != null) lastImpedance = impedance
        val stable = stableCount >= 3
        measurement = OkokMeasurement(weight, lastImpedance, stable, now)
        status = when { stable && lastImpedance != null -> "Measurement captured"; stable -> "Weight stable — finishing body composition"; else -> "Reading — stay still" }
        if (stable) maybeAutoSave(weight, lastImpedance, onSaved)
    }

    private fun maybeAutoSave(weight: Double, impedance: Double?, onSaved: () -> Unit) {
        val sameWeight = lastSavedWeight != null && abs(weight - lastSavedWeight!!) <= 0.05
        val sameImpedance = when { impedance == null && lastSavedImpedance == null -> true; impedance != null && lastSavedImpedance != null -> abs(impedance - lastSavedImpedance!!) <= 1.0; else -> false }
        val recentlySaved = lastSavedAt?.let { System.currentTimeMillis() - it < 20_000 } == true
        if (sameWeight && sameImpedance && recentlySaved) return
        scope.launch {
            NativeDataHub.saveMetric(HealthDomain.BODY, "body_weight_kg", weight, "kg")
            if (impedance != null) {
                NativeDataHub.saveMetric(HealthDomain.BODY, "body_impedance_ohm", impedance, "ohm")
                val h = profileHeightCm; val male = profileMale
                if (h != null && male != null) OkokBiaEstimator.estimate(weight, impedance, h, male)?.let { b ->
                    NativeDataHub.saveMetric(HealthDomain.BODY, "body_fat_pct", b.bodyFatPct, "%")
                    NativeDataHub.saveMetric(HealthDomain.BODY, "body_fat_mass_kg", b.fatMassKg, "kg")
                    NativeDataHub.saveMetric(HealthDomain.BODY, "body_fat_free_mass_kg", b.fatFreeMassKg, "kg")
                    NativeDataHub.saveMetric(HealthDomain.BODY, "body_water_pct", b.waterPct, "%")
                    NativeDataHub.saveMetric(HealthDomain.BODY, "body_water_l", b.totalBodyWaterL, "L")
                    NativeDataHub.saveMetric(HealthDomain.BODY, "body_muscle_pct", b.musclePct, "%")
                    NativeDataHub.saveMetric(HealthDomain.BODY, "body_muscle_mass_kg", b.muscleMassKg, "kg")
                    NativeDataHub.saveMetric(HealthDomain.BODY, "body_skeletal_muscle_pct", b.skeletalMusclePct, "%")
                    NativeDataHub.saveMetric(HealthDomain.BODY, "body_skeletal_muscle_mass_kg", b.skeletalMuscleMassKg, "kg")
                    NativeDataHub.saveMetric(HealthDomain.BODY, "body_visceral_fat_estimate", b.visceralFatEstimate, "index")
                    NativeDataHub.saveMetric(HealthDomain.BODY, "body_bmi", b.bmi, "kg/m2")
                    NativeDataHub.saveMetric(HealthDomain.BODY, "body_ffmi", b.ffmi, "kg/m2")
                    NativeDataHub.saveMetric(HealthDomain.BODY, "body_fmi", b.fmi, "kg/m2")
                }
            }
            lastSavedWeight = weight; lastSavedImpedance = impedance; lastSavedAt = System.currentTimeMillis()
            status = if (impedance != null) "Synced to Body & Progress" else "Weight synced"
            onSaved()
        }
    }

    internal fun decodeWeight(raw: ByteArray): Double? {
        if (raw.size < 22) return null
        val kg = (((raw[20].toInt() and 0xFF) shl 8) or (raw[21].toInt() and 0xFF)) / 100.0
        return kg.takeIf { it in 15.0..350.0 }
    }
    internal fun decodeImpedance(raw: ByteArray): Double? {
        if (raw.size < 24) return null
        val ohm = (((raw[22].toInt() and 0xFF) shl 8) or (raw[23].toInt() and 0xFF)) / 10.0
        return oneDecimal(ohm).takeIf { it in 250.0..1200.0 }
    }
}

internal object OkokBiaEstimator {
    fun estimate(weightKg: Double, impedanceOhm: Double, heightCm: Double, male: Boolean): OkokBiaResult? {
        if (weightKg !in 15.0..350.0 || impedanceOhm !in 250.0..1200.0 || heightCm !in 120.0..230.0) return null
        val ri = heightCm * heightCm / impedanceOhm
        var ffm = if (male) -10.68 + 0.65 * ri + 0.26 * weightKg + 0.02 * impedanceOhm else -9.53 + 0.69 * ri + 0.17 * weightKg + 0.02 * impedanceOhm
        var tbw = if (male) 1.20 + 0.45 * ri + 0.18 * weightKg else 3.75 + 0.45 * ri + 0.11 * weightKg
        ffm = ffm.coerceIn(0.0, weightKg); tbw = tbw.coerceIn(0.0, ffm)
        val fat = (weightKg - ffm).coerceAtLeast(0.0); val bf = fat / weightKg * 100.0; val hm = heightCm / 100.0
        val bmi = weightKg / (hm * hm); val ffmi = ffm / (hm * hm); val fmi = fat / (hm * hm); val waterPct = tbw / weightKg * 100.0
        val musclePct = (100.0 - bf - 3.8).coerceIn(0.0, 100.0); val muscleKg = weightKg * musclePct / 100.0; val skeletalPct = musclePct * 0.569; val skeletalKg = weightKg * skeletalPct / 100.0
        return OkokBiaResult(oneDecimal(bf), oneDecimal(fat), oneDecimal(ffm), oneDecimal(waterPct), oneDecimal(tbw), oneDecimal(musclePct), oneDecimal(muscleKg), oneDecimal(skeletalPct), oneDecimal(skeletalKg), round((bf * 0.394).coerceIn(1.0, 30.0)), oneDecimal(bmi), oneDecimal(ffmi), oneDecimal(fmi))
    }
}

@Composable
internal fun NativeOkokScaleCard(onSaved: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants -> if (grants.values.all { it }) OkokScaleManager.startAutoTracking(context, onSaved) }
    LaunchedEffect(Unit) {
        val values = NativeDataHub.latestForDomain(HealthDomain.BODY)
        val h = values.firstOrNull { it.metric == "body_height_cm" }?.value
        val male = values.firstOrNull { it.metric == "body_sex_code" }?.value?.let { it >= 0.5 }
        OkokScaleManager.setProfile(h, male)
        if (OkokScaleManager.hasPermissions(context)) OkokScaleManager.startAutoTracking(context, onSaved)
    }
    DisposableEffect(Unit) { onDispose { OkokScaleManager.stopAutoTracking() } }

    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF0B3554), Color(0xFF0A6370), Color(0xFF138A78))), RoundedCornerShape(22.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AUTOMATIC BODY SCAN", color = Color.White.copy(alpha=.60f), fontSize=8.sp, fontWeight=FontWeight.Black)
                Text("Smart Scale Sync", color=Color.White, fontSize=17.sp, fontWeight=FontWeight.Black)
                Text(OkokScaleManager.status, color=Color.White.copy(alpha=.80f), fontSize=9.sp)
            }
            if (!OkokScaleManager.hasPermissions(context)) Box(Modifier.background(Color.White, RoundedCornerShape(13.dp)).clickable { launcher.launch(OkokScaleManager.requiredPermissions()) }.padding(horizontal=13.dp, vertical=10.dp)) { Text("ENABLE", color=Color(0xFF0A6370), fontSize=9.sp, fontWeight=FontWeight.Black) }
            else Box(Modifier.background(Color.White.copy(alpha=.12f), RoundedCornerShape(13.dp)).padding(horizontal=12.dp, vertical=9.dp)) { Text(if(OkokScaleManager.listening)"AUTO" else "PAUSED", color=Color.White, fontSize=9.sp, fontWeight=FontWeight.Black) }
        }
        OkokScaleManager.measurement?.let { m -> Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) { ScaleMetric("WEIGHT", "%.2f kg".format(m.weightKg), Modifier.weight(1f)); ScaleMetric("IMPEDANCE", m.impedanceOhm?.let { "%.0f Ω".format(it) } ?: "—", Modifier.weight(1f)) } }
        if (OkokScaleManager.measurement == null) Text("Step on your scale normally. Weight and body composition sync automatically when the reading settles.", color=Color.White.copy(alpha=.70f), fontSize=8.sp, lineHeight=12.sp)
    }
}

@Composable private fun ScaleMetric(label:String,value:String,modifier:Modifier){Column(modifier.background(Color.White.copy(alpha=.10f),RoundedCornerShape(14.dp)).padding(11.dp)){Text(label,color=Color.White.copy(alpha=.55f),fontSize=7.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(4.dp));Text(value,color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Black)}}
private fun oneDecimal(value:Double):Double=round(value*10.0)/10.0
