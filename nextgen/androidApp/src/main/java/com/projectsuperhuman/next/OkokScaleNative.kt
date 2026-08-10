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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

private const val SCALE_PREFS = "project_superhuman_scale"
private const val PREF_SCALE_MAC = "okok_scale_mac"
private const val PREF_SCALE_NAME = "okok_scale_name"

internal data class OkokCandidate(val name: String, val mac: String, val rssi: Int)
internal data class OkokMeasurement(val weightKg: Double, val impedanceOhm: Double?, val mac: String, val rssi: Int, val stable: Boolean)
internal data class OkokBiaResult(
    val bodyFatPct: Double, val fatMassKg: Double, val fatFreeMassKg: Double,
    val waterPct: Double, val totalBodyWaterL: Double, val musclePct: Double,
    val muscleMassKg: Double, val skeletalMusclePct: Double, val skeletalMuscleMassKg: Double,
    val visceralFatEstimate: Double, val bmi: Double, val ffmi: Double, val fmi: Double
)

private enum class ScaleMode { IDLE, SYNCING, MEASURING }

internal object OkokScaleManager {
    var status by mutableStateOf("Sync your scale before measuring"); private set
    var scanning by mutableStateOf(false); private set
    var syncedMac by mutableStateOf(""); private set
    var syncedName by mutableStateOf(""); private set
    var candidates by mutableStateOf<List<OkokCandidate>>(emptyList()); private set
    var measurement by mutableStateOf<OkokMeasurement?>(null); private set

    private var mode = ScaleMode.IDLE
    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var callback: ScanCallback? = null
    private var timeoutJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var lastWeight: Double? = null
    private var stableCount = 0
    private var lastSeen = 0L
    private var lastImpedance: Double? = null

    fun loadPairing(context: Context) {
        val p = context.getSharedPreferences(SCALE_PREFS, Context.MODE_PRIVATE)
        syncedMac = normalizeMac(p.getString(PREF_SCALE_MAC, "").orEmpty())
        syncedName = p.getString(PREF_SCALE_NAME, "").orEmpty()
        status = if (syncedMac.isBlank()) "No scale synced" else "Scale synced"
    }

    fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    fun hasPermissions(context: Context): Boolean = requiredPermissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    fun pair(context: Context, candidate: OkokCandidate) {
        stop("")
        syncedMac = normalizeMac(candidate.mac)
        syncedName = candidate.name.ifBlank { "OKOK / Chipsea scale" }
        context.getSharedPreferences(SCALE_PREFS, Context.MODE_PRIVATE).edit().putString(PREF_SCALE_MAC, syncedMac).putString(PREF_SCALE_NAME, syncedName).apply()
        candidates = emptyList(); measurement = null; status = "Scale synced"
    }

    @SuppressLint("MissingPermission")
    fun startSync(context: Context) {
        if (!prepareScanner(context)) return
        resetSession(); mode = ScaleMode.SYNCING; scanning = true; candidates = emptyList(); measurement = null
        status = "Searching — wake the scale by stepping on it"
        beginScan(12_000)
    }

    @SuppressLint("MissingPermission")
    fun startMeasure(context: Context) {
        if (syncedMac.isBlank()) { status = "Sync your scale first"; return }
        if (!prepareScanner(context)) return
        resetSession(); mode = ScaleMode.MEASURING; scanning = true; measurement = null
        status = "Measuring — step on your synced scale barefoot"
        beginScan(15_000)
    }

    @SuppressLint("MissingPermission")
    private fun beginScan(timeoutMs: Long) {
        callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = consume(result)
            override fun onScanFailed(errorCode: Int) { scanning = false; mode = ScaleMode.IDLE; status = "Bluetooth scan failed ($errorCode)" }
        }
        scanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
        timeoutJob = scope.launch {
            delay(timeoutMs)
            if (!scanning) return@launch
            when (mode) {
                ScaleMode.SYNCING -> stop(if (candidates.isEmpty()) "No scale found — wake it and try Sync again" else "Choose your scale below")
                ScaleMode.MEASURING -> {
                    val m = measurement
                    if (m?.stable == true) stop(if (m.impedanceOhm == null) "Stable weight captured; impedance was not received" else "Measurement ready")
                    else { measurement = null; stop("No stable reading — step on the synced scale and try again") }
                }
                ScaleMode.IDLE -> stop("")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun prepareScanner(context: Context): Boolean {
        stop("")
        if (!hasPermissions(context)) { status = "Bluetooth permission is required"; return false }
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null) { status = "Bluetooth LE is not available on this phone"; return false }
        if (!adapter.isEnabled) { status = "Turn Bluetooth on, then try again"; return false }
        scanner = adapter.bluetoothLeScanner
        if (scanner == null) { status = "Bluetooth scanner is unavailable"; return false }
        return true
    }

    private fun resetSession() { timeoutJob?.cancel(); lastWeight = null; lastImpedance = null; stableCount = 0; lastSeen = 0L }

    @SuppressLint("MissingPermission")
    fun stop(message: String = "Cancelled") {
        timeoutJob?.cancel(); timeoutJob = null
        try { callback?.let { scanner?.stopScan(it) } } catch (_: Exception) {}
        callback = null; scanning = false; mode = ScaleMode.IDLE
        if (message.isNotBlank()) status = message
    }

    @SuppressLint("MissingPermission")
    private fun consume(result: ScanResult) {
        val mac = try { normalizeMac(result.device?.address.orEmpty()) } catch (_: SecurityException) { "" }
        if (mac.isBlank()) return
        val raw = result.scanRecord?.bytes ?: return
        val name = try { result.device?.name.orEmpty() } catch (_: SecurityException) { "" }
        when (mode) {
            ScaleMode.SYNCING -> consumeSync(name, mac, result.rssi, raw)
            ScaleMode.MEASURING -> if (mac == syncedMac) consumeMeasurement(mac, result.rssi, raw)
            ScaleMode.IDLE -> Unit
        }
    }

    private fun consumeSync(name: String, mac: String, rssi: Int, raw: ByteArray) {
        if (!looksLikeScale(name, raw)) return
        val c = OkokCandidate(name.ifBlank { "Possible OKOK scale" }, mac, rssi)
        val list = candidates.toMutableList(); val i = list.indexOfFirst { it.mac == mac }
        if (i >= 0) list[i] = c else list.add(c)
        candidates = list.sortedByDescending { it.rssi }.take(6)
        status = "Scale candidate found — select your device"
    }

    private fun consumeMeasurement(mac: String, rssi: Int, raw: ByteArray) {
        val weight = decodeWeight(raw) ?: return
        val impedance = decodeImpedance(raw)
        val now = System.currentTimeMillis()
        stableCount = if (lastWeight != null && abs(weight - lastWeight!!) <= 0.12 && now - lastSeen < 3500) stableCount + 1 else 1
        lastWeight = weight; lastSeen = now; if (impedance != null) lastImpedance = impedance
        val stable = stableCount >= 3
        measurement = OkokMeasurement(weight, lastImpedance, mac, rssi, stable)
        status = when { stable && lastImpedance != null -> "Weight + impedance captured"; stable -> "Weight stable — waiting for impedance"; else -> "Reading your scale — hold still" }
        if (stable && lastImpedance != null) stop("Measurement ready")
    }

    private fun looksLikeScale(name: String, raw: ByteArray): Boolean {
        val n = name.lowercase(); if (n.contains("okok") || n.contains("chipsea") || n.contains("scale")) return true
        if (raw.size < 24) return false
        val hex = raw.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
        return hex.contains("FFF0") || decodeWeight(raw) != null
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
    private fun normalizeMac(v: String) = v.trim().uppercase()
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
        val musclePct = (100.0 - bf - 3.8).coerceIn(0.0, 100.0); val muscleKg = weightKg * musclePct / 100.0; val skelPct = musclePct * 0.569; val skelKg = weightKg * skelPct / 100.0
        return OkokBiaResult(oneDecimal(bf), oneDecimal(fat), oneDecimal(ffm), oneDecimal(waterPct), oneDecimal(tbw), oneDecimal(musclePct), oneDecimal(muscleKg), oneDecimal(skelPct), oneDecimal(skelKg), round((bf * 0.394).coerceIn(1.0, 30.0)), oneDecimal(bmi), oneDecimal(ffmi), oneDecimal(fmi))
    }
}

@Composable
internal fun NativeOkokScaleCard(onSaved: () -> Unit) {
    val context = LocalContext.current; val m = OkokScaleManager.measurement
    var height by remember { mutableStateOf("") }; var male by remember { mutableStateOf<Boolean?>(null) }; var pendingSync by remember { mutableStateOf(false) }; var savedStatus by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        OkokScaleManager.loadPairing(context)
        val values = NativeDataHub.latestForDomain(HealthDomain.BODY)
        values.firstOrNull { it.metric == "body_height_cm" }?.value?.let { height = "%.0f".format(it) }
        values.firstOrNull { it.metric == "body_sex_code" }?.value?.let { male = it >= 0.5 }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) { if (pendingSync) OkokScaleManager.startSync(context) else OkokScaleManager.startMeasure(context) } else savedStatus = "Bluetooth permission wasn't granted"
    }
    fun sync() { pendingSync = true; if (OkokScaleManager.hasPermissions(context)) OkokScaleManager.startSync(context) else launcher.launch(OkokScaleManager.requiredPermissions()) }
    fun measure() { pendingSync = false; if (OkokScaleManager.hasPermissions(context)) OkokScaleManager.startMeasure(context) else launcher.launch(OkokScaleManager.requiredPermissions()) }
    val bia = m?.impedanceOhm?.let { z -> height.toDoubleOrNull()?.let { h -> male?.let { sex -> OkokBiaEstimator.estimate(m.weightKg, z, h, sex) } } }

    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF092B4C), Color(0xFF0C526E), Color(0xFF168A78))), RoundedCornerShape(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SMART SCALE", color = Color.White.copy(alpha=.62f), fontSize=8.sp, fontWeight=FontWeight.Black)
                Text(if (OkokScaleManager.syncedMac.isBlank()) "No scale linked" else "OKOK / Chipsea", color=Color.White, fontSize=18.sp, fontWeight=FontWeight.Black)
                Text(OkokScaleManager.status, color=Color.White.copy(alpha=.78f), fontSize=9.sp)
                if (OkokScaleManager.syncedMac.isNotBlank()) Text("Synced • ${OkokScaleManager.syncedName} • ${OkokScaleManager.syncedMac.takeLast(8)}", color=Color.White.copy(alpha=.56f), fontSize=7.sp)
            }
            Box(Modifier.background(Color.White.copy(alpha=.14f), RoundedCornerShape(14.dp)).clickable { if (OkokScaleManager.scanning) OkokScaleManager.stop() else if (OkokScaleManager.syncedMac.isBlank()) sync() else measure() }.padding(horizontal=14.dp, vertical=11.dp)) {
                Text(if (OkokScaleManager.scanning) "CANCEL" else if (OkokScaleManager.syncedMac.isBlank()) "SYNC" else "MEASURE", color=Color.White, fontSize=9.sp, fontWeight=FontWeight.Black)
            }
        }
        if (OkokScaleManager.syncedMac.isNotBlank() && !OkokScaleManager.scanning) Text("Change synced scale", color=Color.White.copy(alpha=.72f), fontSize=8.sp, fontWeight=FontWeight.Bold, modifier=Modifier.clickable { sync() })
        if (OkokScaleManager.candidates.isNotEmpty()) {
            Text("SELECT YOUR SCALE", color=Color.White.copy(alpha=.62f), fontSize=8.sp, fontWeight=FontWeight.Black)
            OkokScaleManager.candidates.forEach { c ->
                Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha=.10f), RoundedCornerShape(13.dp)).clickable { OkokScaleManager.pair(context, c) }.padding(11.dp), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(c.name, color=Color.White, fontSize=10.sp, fontWeight=FontWeight.Bold); Text("${c.mac} • ${c.rssi} dBm", color=Color.White.copy(alpha=.62f), fontSize=7.sp) }
                    Text("USE", color=Color.White, fontSize=8.sp, fontWeight=FontWeight.Black)
                }
            }
        }
        if (m != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) { ScaleMetric("WEIGHT", "%.2f kg".format(m.weightKg), Modifier.weight(1f)); ScaleMetric("IMPEDANCE", m.impedanceOhm?.let { "%.1f Ω".format(it) } ?: "waiting…", Modifier.weight(1f)) }
            Text(if (m.stable) "Stable reading from your synced scale" else "Hold still while the reading settles", color=Color.White.copy(alpha=.75f), fontSize=8.sp)
            if (m.impedanceOhm != null && (height.isBlank() || male == null)) {
                Text("Complete body profile for BIA estimates", color=Color.White, fontSize=10.sp, fontWeight=FontWeight.Bold)
                OutlinedTextField(height, { height = it.filter { ch -> ch.isDigit() || ch=='.' }.take(6) }, Modifier.fillMaxWidth(), label={Text("Height cm")}, singleLine=true)
                Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha=.10f), RoundedCornerShape(14.dp)).padding(4.dp)) { ScaleSexButton("Male", male==true, Modifier.weight(1f)){male=true}; ScaleSexButton("Female", male==false, Modifier.weight(1f)){male=false} }
            }
            if (bia != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) { ScaleMetric("BODY FAT", "%.1f%%".format(bia.bodyFatPct), Modifier.weight(1f)); ScaleMetric("WATER", "%.1f%%".format(bia.waterPct), Modifier.weight(1f)); ScaleMetric("MUSCLE", "%.1f%%".format(bia.musclePct), Modifier.weight(1f)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) { ScaleMetric("BMI", "%.1f".format(bia.bmi), Modifier.weight(1f)); ScaleMetric("VISCERAL", "%.0f".format(bia.visceralFatEstimate), Modifier.weight(1f)); ScaleMetric("FFMI", "%.1f".format(bia.ffmi), Modifier.weight(1f)) }
            }
            if (m.stable) Box(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(15.dp)).clickable {
                CoroutineScope(Dispatchers.Main).launch {
                    NativeDataHub.saveMetric(HealthDomain.BODY, "body_weight_kg", m.weightKg, "kg"); m.impedanceOhm?.let { NativeDataHub.saveMetric(HealthDomain.BODY, "body_impedance_ohm", it, "ohm") }
                    height.toDoubleOrNull()?.let { NativeDataHub.saveMetric(HealthDomain.BODY, "body_height_cm", it, "cm") }; male?.let { NativeDataHub.saveMetric(HealthDomain.BODY, "body_sex_code", if(it)1.0 else 0.0, "code") }
                    bia?.let { b ->
                        NativeDataHub.saveMetric(HealthDomain.BODY, "body_fat_pct", b.bodyFatPct, "%"); NativeDataHub.saveMetric(HealthDomain.BODY, "body_fat_mass_kg", b.fatMassKg, "kg"); NativeDataHub.saveMetric(HealthDomain.BODY, "body_fat_free_mass_kg", b.fatFreeMassKg, "kg"); NativeDataHub.saveMetric(HealthDomain.BODY, "body_water_pct", b.waterPct, "%"); NativeDataHub.saveMetric(HealthDomain.BODY, "body_water_l", b.totalBodyWaterL, "L"); NativeDataHub.saveMetric(HealthDomain.BODY, "body_muscle_pct", b.musclePct, "%"); NativeDataHub.saveMetric(HealthDomain.BODY, "body_muscle_mass_kg", b.muscleMassKg, "kg"); NativeDataHub.saveMetric(HealthDomain.BODY, "body_skeletal_muscle_pct", b.skeletalMusclePct, "%"); NativeDataHub.saveMetric(HealthDomain.BODY, "body_skeletal_muscle_mass_kg", b.skeletalMuscleMassKg, "kg"); NativeDataHub.saveMetric(HealthDomain.BODY, "body_visceral_fat_estimate", b.visceralFatEstimate, "index"); NativeDataHub.saveMetric(HealthDomain.BODY, "body_bmi", b.bmi, "kg/m2"); NativeDataHub.saveMetric(HealthDomain.BODY, "body_ffmi", b.ffmi, "kg/m2"); NativeDataHub.saveMetric(HealthDomain.BODY, "body_fmi", b.fmi, "kg/m2")
                    }
                    savedStatus = if(m.impedanceOhm!=null) "Saved weight + impedance" else "Saved stable weight"; onSaved()
                }
            }.padding(14.dp), contentAlignment=Alignment.Center) { Text("SAVE MEASUREMENT", color=Color(0xFF0C526E), fontSize=10.sp, fontWeight=FontWeight.Black) }
        }
        if(savedStatus.isNotBlank()) Text(savedStatus, color=Color.White.copy(alpha=.78f), fontSize=8.sp)
    }
}

@Composable private fun ScaleMetric(label:String,value:String,modifier:Modifier){Column(modifier.background(Color.White.copy(alpha=.10f),RoundedCornerShape(15.dp)).padding(11.dp)){Text(label,color=Color.White.copy(alpha=.55f),fontSize=7.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(4.dp));Text(value,color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Black)}}
@Composable private fun ScaleSexButton(label:String,selected:Boolean,modifier:Modifier,onClick:()->Unit){Box(modifier.background(if(selected)Color.White else Color.Transparent,RoundedCornerShape(11.dp)).clickable(onClick=onClick).padding(vertical=10.dp),contentAlignment=Alignment.Center){Text(label,color=if(selected)Color(0xFF0C526E) else Color.White,fontSize=8.sp,fontWeight=FontWeight.Black)}}
private fun oneDecimal(value:Double):Double=round(value*10.0)/10.0
