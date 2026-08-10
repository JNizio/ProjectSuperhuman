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

internal data class OkokMeasurement(
    val weightKg: Double,
    val impedanceOhm: Double?,
    val mac: String,
    val rssi: Int,
    val stable: Boolean
)

internal data class OkokBiaResult(
    val bodyFatPct: Double,
    val fatMassKg: Double,
    val fatFreeMassKg: Double,
    val waterPct: Double,
    val totalBodyWaterL: Double,
    val musclePct: Double,
    val muscleMassKg: Double,
    val skeletalMusclePct: Double,
    val skeletalMuscleMassKg: Double,
    val visceralFatEstimate: Double,
    val bmi: Double,
    val ffmi: Double,
    val fmi: Double
)

internal object OkokScaleManager {
    var status by mutableStateOf("Ready to measure")
        private set
    var scanning by mutableStateOf(false)
        private set
    var measurement by mutableStateOf<OkokMeasurement?>(null)
        private set
    var lastRawHex by mutableStateOf("")
        private set

    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var callback: ScanCallback? = null
    private var timeoutJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var lastWeight: Double? = null
    private var stableCount = 0
    private var lastSeen = 0L
    private var lastImpedance: Double? = null

    fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun hasPermissions(context: Context): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        stop("Ready to measure")
        if (!hasPermissions(context)) {
            status = "Bluetooth permission is required"
            return
        }
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        if (adapter == null) {
            status = "Bluetooth LE is not available on this phone"
            return
        }
        if (!adapter.isEnabled) {
            status = "Turn Bluetooth on, then try again"
            return
        }
        scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            status = "Bluetooth scanner is unavailable"
            return
        }

        measurement = null
        lastWeight = null
        lastImpedance = null
        stableCount = 0
        scanning = true
        status = "Listening — step on the scale barefoot"

        callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = consume(result)
            override fun onScanFailed(errorCode: Int) {
                scanning = false
                status = "Scale scan failed ($errorCode)"
            }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner?.startScan(null, settings, callback)
        timeoutJob = scope.launch {
            delay(12_000)
            if (scanning) stop(if (measurement == null) "No scale reading found — wake the scale and retry" else "Measurement captured")
        }
    }

    @SuppressLint("MissingPermission")
    fun stop(message: String = "Measurement cancelled") {
        timeoutJob?.cancel()
        timeoutJob = null
        try { callback?.let { scanner?.stopScan(it) } } catch (_: Exception) {}
        callback = null
        scanning = false
        if (message.isNotBlank()) status = message
    }

    @SuppressLint("MissingPermission")
    private fun consume(result: ScanResult) {
        val raw = result.scanRecord?.bytes ?: return
        val weight = decodeWeight(raw) ?: return
        val impedance = decodeImpedance(raw)
        val now = System.currentTimeMillis()

        if (lastWeight != null && abs(weight - lastWeight!!) <= 0.12 && now - lastSeen < 3500) stableCount++ else stableCount = 1
        lastWeight = weight
        lastSeen = now
        if (impedance != null) lastImpedance = impedance
        lastRawHex = raw.joinToString("") { "%02X".format(it.toInt() and 0xFF) }

        val stable = stableCount >= 3
        val mac = try { result.device?.address.orEmpty() } catch (_: SecurityException) { "" }
        measurement = OkokMeasurement(weight, lastImpedance, mac, result.rssi, stable)
        status = when {
            stable && lastImpedance != null -> "Weight + impedance captured"
            stable -> "Weight stable — waiting for impedance"
            else -> "Weight decoded — hold still"
        }
        if (stable && lastImpedance != null) stop("Measurement ready")
    }

    internal fun decodeWeight(raw: ByteArray): Double? {
        if (raw.size < 22) return null
        val kg = (((raw[20].toInt() and 0xFF) shl 8) or (raw[21].toInt() and 0xFF)) / 100.0
        return kg.takeIf { it in 15.0..350.0 }
    }

    internal fun decodeImpedance(raw: ByteArray): Double? {
        if (raw.size < 24) return null
        val ohm = (((raw[22].toInt() and 0xFF) shl 8) or (raw[23].toInt() and 0xFF)) / 10.0
        return round1(ohm).takeIf { it in 250.0..1200.0 }
    }
}

internal object OkokBiaEstimator {
    fun estimate(weightKg: Double, impedanceOhm: Double, heightCm: Double, male: Boolean): OkokBiaResult? {
        if (weightKg !in 15.0..350.0 || impedanceOhm !in 250.0..1200.0 || heightCm !in 120.0..230.0) return null
        val ri = heightCm * heightCm / impedanceOhm
        var ffm: Double
        var tbw: Double
        if (male) {
            ffm = -10.68 + 0.65 * ri + 0.26 * weightKg + 0.02 * impedanceOhm
            tbw = 1.20 + 0.45 * ri + 0.18 * weightKg
        } else {
            ffm = -9.53 + 0.69 * ri + 0.17 * weightKg + 0.02 * impedanceOhm
            tbw = 3.75 + 0.45 * ri + 0.11 * weightKg
        }
        ffm = ffm.coerceIn(0.0, weightKg)
        tbw = tbw.coerceIn(0.0, ffm)
        val fat = (weightKg - ffm).coerceAtLeast(0.0)
        val bodyFat = fat / weightKg * 100.0
        val hm = heightCm / 100.0
        val bmi = weightKg / (hm * hm)
        val ffmi = ffm / (hm * hm)
        val fmi = fat / (hm * hm)
        val waterPct = tbw / weightKg * 100.0
        val musclePct = (100.0 - bodyFat - 3.8).coerceIn(0.0, 100.0)
        val muscleKg = weightKg * musclePct / 100.0
        val skeletalPct = musclePct * 0.569
        val skeletalKg = weightKg * skeletalPct / 100.0
        val visceral = (bodyFat * 0.394).coerceIn(1.0, 30.0)
        return OkokBiaResult(round1(bodyFat), round1(fat), round1(ffm), round1(waterPct), round1(tbw), round1(musclePct), round1(muscleKg), round1(skeletalPct), round1(skeletalKg), round(visceral), round1(bmi), round1(ffmi), round1(fmi))
    }
}

@Composable
internal fun NativeOkokScaleCard(onSaved: () -> Unit) {
    val context = LocalContext.current
    val m = OkokScaleManager.measurement
    var height by androidx.compose.runtime.remember { mutableStateOf("") }
    var male by androidx.compose.runtime.remember { mutableStateOf(true) }
    var savedStatus by androidx.compose.runtime.remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) OkokScaleManager.start(context) else savedStatus = "Bluetooth permission wasn't granted"
    }

    val bia = m?.impedanceOhm?.let { z -> height.toDoubleOrNull()?.let { h -> OkokBiaEstimator.estimate(m.weightKg, z, h, male) } }

    Column(
        Modifier.fillMaxWidth().background(
            Brush.linearGradient(listOf(Color(0xFF092B4C), Color(0xFF0C526E), Color(0xFF168A78))),
            RoundedCornerShape(24.dp)
        ).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SMART SCALE", color = Color.White.copy(alpha = .62f), fontSize = 8.sp, fontWeight = FontWeight.Black)
                Text("OKOK / Chipsea", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(OkokScaleManager.status, color = Color.White.copy(alpha = .78f), fontSize = 9.sp)
            }
            Box(
                Modifier.background(Color.White.copy(alpha = .14f), RoundedCornerShape(14.dp)).clickable {
                    if (OkokScaleManager.scanning) OkokScaleManager.stop() else if (OkokScaleManager.hasPermissions(context)) OkokScaleManager.start(context) else launcher.launch(OkokScaleManager.requiredPermissions())
                }.padding(horizontal = 14.dp, vertical = 11.dp)
            ) { Text(if (OkokScaleManager.scanning) "CANCEL" else "MEASURE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black) }
        }

        if (m != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScaleMetric("WEIGHT", "%.2f kg".format(m.weightKg), Modifier.weight(1f))
                ScaleMetric("IMPEDANCE", m.impedanceOhm?.let { "%.1f Ω".format(it) } ?: "waiting…", Modifier.weight(1f))
            }
            Text(if (m.stable) "Stable reading" else "Hold still while the reading settles", color = Color.White.copy(alpha = .75f), fontSize = 8.sp)

            if (m.impedanceOhm != null) {
                Text("Body composition setup", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(height, { height = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, Modifier.weight(1f), label = { Text("Height cm") }, singleLine = true)
                    Row(Modifier.weight(1f).background(Color.White.copy(alpha = .10f), RoundedCornerShape(14.dp)).padding(4.dp)) {
                        ScaleSexButton("Male", male, Modifier.weight(1f)) { male = true }
                        ScaleSexButton("Female", !male, Modifier.weight(1f)) { male = false }
                    }
                }
            }

            if (bia != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScaleMetric("BODY FAT", "%.1f%%".format(bia.bodyFatPct), Modifier.weight(1f))
                    ScaleMetric("WATER", "%.1f%%".format(bia.waterPct), Modifier.weight(1f))
                    ScaleMetric("MUSCLE", "%.1f%%".format(bia.musclePct), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScaleMetric("BMI", "%.1f".format(bia.bmi), Modifier.weight(1f))
                    ScaleMetric("VISCERAL", "%.0f".format(bia.visceralFatEstimate), Modifier.weight(1f))
                    ScaleMetric("FFMI", "%.1f".format(bia.ffmi), Modifier.weight(1f))
                }
                Text("Body-composition values are BIA estimates. Trends are most useful under similar conditions.", color = Color.White.copy(alpha = .63f), fontSize = 7.sp)
            }

            Box(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(15.dp)).clickable {
                CoroutineScope(Dispatchers.Main).launch {
                    NativeDataHub.saveMetric(HealthDomain.BODY, "body_weight_kg", m.weightKg, "kg")
                    m.impedanceOhm?.let { NativeDataHub.saveMetric(HealthDomain.BODY, "body_impedance_ohm", it, "ohm") }
                    height.toDoubleOrNull()?.let { NativeDataHub.saveMetric(HealthDomain.BODY, "body_height_cm", it, "cm") }
                    NativeDataHub.saveMetric(HealthDomain.BODY, "body_sex_code", if (male) 1.0 else 0.0, "code")
                    bia?.let {
                        NativeDataHub.saveMetric(HealthDomain.BODY, "body_fat_pct", it.bodyFatPct, "%")
                        NativeDataHub.saveMetric(HealthDomain.BODY, "body_fat_mass_kg", it.fatMassKg, "kg")
                        NativeDataHub.saveMetric(HealthDomain.BODY, "body_fat_free_mass_kg", it.fatFreeMassKg, "kg")
                        NativeDataHub.saveMetric(HealthDomain.BODY, "body_water_pct", it.waterPct, "%")
                        NativeDataHub.saveMetric(HealthDomain.BODY, "body_water_l", it.totalBodyWaterL, "L")
                        NativeDataHub.saveMetric(HealthDomain.BODY, "body_muscle_pct", it.musclePct, "%")
                        NativeDataHub.saveMetric(HealthDomain.BODY, "body_muscle_mass_kg", it.muscleMassKg, "kg")
                        NativeDataHub.saveMetric(HealthDomain.BODY, "body_skeletal_muscle_pct", it.skeletalMusclePct, "%")
                        NativeDataHub.saveMetric(HealthDomain.BODY, "body_skeletal_muscle_mass_kg", it.skeletalMuscleMassKg, "kg")
                        NativeDataHub.saveMetric(HealthDomain.BODY, "body_visceral_fat_estimate", it.visceralFatEstimate, "index")
                        NativeDataHub.saveMetric(HealthDomain.BODY, "body_bmi", it.bmi, "kg/m2")
                        NativeDataHub.saveMetric(HealthDomain.BODY, "body_ffmi", it.ffmi, "kg/m2")
                        NativeDataHub.saveMetric(HealthDomain.BODY, "body_fmi", it.fmi, "kg/m2")
                    }
                    savedStatus = if (bia != null) "Saved weight, impedance and BIA estimates" else "Saved weight${if (m.impedanceOhm != null) " + impedance" else ""}"
                    onSaved()
                }
            }.padding(13.dp), contentAlignment = Alignment.Center) {
                Text("SAVE MEASUREMENT", color = Color(0xFF0C526E), fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
        } else {
            Text("Step on barefoot and keep both feet on the electrodes. Project Superhuman reads the scale directly over Bluetooth — the OKOK app is not required.", color = Color.White.copy(alpha = .72f), fontSize = 8.sp, lineHeight = 12.sp)
        }
        if (savedStatus.isNotBlank()) Text(savedStatus, color = Color.White.copy(alpha = .82f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ScaleMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(Color.White.copy(alpha = .10f), RoundedCornerShape(13.dp)).padding(9.dp)) {
        Text(label, color = Color.White.copy(alpha = .55f), fontSize = 7.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ScaleSexButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.background(if (selected) Color.White else Color.Transparent, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) Color(0xFF0C526E) else Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
}

private fun round1(value: Double): Double = round(value * 10.0) / 10.0
