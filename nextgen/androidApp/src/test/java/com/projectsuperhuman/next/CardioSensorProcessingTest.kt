package com.projectsuperhuman.next

import androidx.health.connect.client.records.ExerciseSessionRecord
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

internal class CardioSensorProcessingTest {
    private val liveSource = CardioSensorProvenance(
        providerType = CardioSensorProviderType.BLE_HEART_RATE,
        sourceName = "test-ble",
        transport = CardioSensorTransport.LIVE_BLE,
        deviceName = "Test Strap",
        anonymousSensorId = "abc123"
    )

    private fun hr(second: Int, bpm: Int): CardioHeartRateSample =
        CardioHeartRateSample(second * 1_000L, bpm, liveSource)

    @Test
    fun ble8BitPacket() {
        assertEquals(72, BleHeartRateMeasurementParser.parse(byteArrayOf(0x00, 72)))
    }

    @Test
    fun ble16BitPacket() {
        assertEquals(
            200,
            BleHeartRateMeasurementParser.parse(byteArrayOf(0x01, 0xC8.toByte(), 0x00))
        )
    }

    @Test
    fun invalidHeartRateRejected() {
        assertNull(BleHeartRateMeasurementParser.parse(byteArrayOf(0x00, 5)))
        val collector = CardioSessionHeartRateCollector()
        collector.start("s", 0L)
        assertFalse(collector.accept(hr(1, 300)))
    }

    @Test
    fun averageMinMaxUseMeasuredSamples() {
        val summary = CardioHeartRateProcessor.summarise(
            listOf(hr(0, 100), hr(5, 120), hr(10, 140)),
            listOf(CardioActiveWindow(0, 15_000))
        )
        assertEquals(120, summary.averageBpm)
        assertEquals(100, summary.minBpm)
        assertEquals(140, summary.maxBpm)
    }

    @Test
    fun zoneAllocationUsesMeasuredSegments() {
        val summary = CardioHeartRateProcessor.summarise(
            listOf(hr(0, 110), hr(10, 130), hr(20, 170)),
            listOf(CardioActiveWindow(0, 30_000))
        )
        assertEquals(10, summary.zoneSeconds[2])
        assertEquals(10, summary.zoneSeconds[3])
        assertEquals(10, summary.zoneSeconds[5])
        assertEquals(0, summary.unclassifiedSeconds)
    }

    @Test
    fun sensorGapsRemainUnclassified() {
        val summary = CardioHeartRateProcessor.summarise(
            listOf(hr(0, 110), hr(30, 130)),
            listOf(CardioActiveWindow(0, 60_000))
        )
        assertEquals(20, summary.measuredSeconds)
        assertEquals(40, summary.unclassifiedSeconds)
    }

    @Test
    fun heartRateCoverageReflectsPartialSignal() {
        val summary = CardioHeartRateProcessor.summarise(
            listOf(hr(0, 110), hr(10, 115), hr(20, 120)),
            listOf(CardioActiveWindow(0, 60_000))
        )
        assertTrue(abs(summary.heartRateCoveragePct - 50.0) < 0.01)
    }

    @Test
    fun staleSampleDoesNotRemainFresh() {
        val state = CardioSensorState(
            providerType = CardioSensorProviderType.BLE_HEART_RATE,
            connection = CardioSensorConnectionState.CONNECTED,
            currentHeartRateBpm = 88,
            lastSampleEpochMs = 1_000
        )
        assertEquals(CardioSensorConnectionState.STALE, state.withFreshness(12_000).connection)
        assertNull(state.withFreshness(12_000).freshHeartRate(12_000))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun reconnectStateIsExplicit() = runTest {
        val client = FakeBleHeartRateClient()
        val provider = GenericBleHeartRateProvider(client, scope = this)
        runCurrent()
        client.emit(BleHeartRateClientEvent.Reconnecting(2))
        runCurrent()
        assertEquals(CardioSensorConnectionState.RECONNECTING, provider.state.value.connection)
        assertEquals(2, provider.state.value.reconnectAttempt)
    }

    @Test
    fun h19cAdapterMapsRuntimeState() {
        val mapped = H19cCardioMapper.map(
            H19cWearableState(
                phase = H19cConnectionPhase.READY,
                deviceName = "H19C",
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                manufacturer = "MOYOUNG",
                model = "H19C",
                heartRateBpm = 77,
                lastHeartRateEpochMs = 1_000
            ),
            nowEpochMs = 1_500
        )
        assertEquals(CardioSensorConnectionState.CONNECTED, mapped.connection)
        assertEquals(77, mapped.currentHeartRateBpm)
        assertEquals(CardioSensorProviderType.H19C, mapped.provenance?.providerType)
    }

    @Test
    fun provenanceHashesRawSensorIdentifier() {
        val raw = "AA:BB:CC:DD:EE:FF"
        val id = CardioSensorIds.anonymous(raw)
        assertTrue(id?.length == 16)
        assertNotEquals(raw, id)
        assertEquals(id, CardioSensorIds.anonymous(raw))
    }

    @Test
    fun healthConnectActivityMapping() {
        assertEquals(
            CardioActivityType.RUNNING,
            CardioHealthConnectRules.mapActivity(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING).activity
        )
        assertEquals(
            CardioActivityType.STATIONARY_BIKE,
            CardioHealthConnectRules.mapActivity(ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY).activity
        )
    }

    @Test
    fun healthConnectWorkoutDedupeUsesStableRecordVersion() {
        assertEquals(
            CardioExternalDedupeDecision.IMPORT,
            CardioHealthConnectRules.dedupeDecision(null, 100)
        )
        assertEquals(
            CardioExternalDedupeDecision.DEDUPLICATE,
            CardioHealthConnectRules.dedupeDecision(100, 100)
        )
        assertEquals(
            CardioExternalDedupeDecision.UPDATE,
            CardioHealthConnectRules.dedupeDecision(100, 101)
        )
        assertEquals(
            "hc-cardio:pkg:id",
            CardioHealthConnectRules.sourceRecordId("pkg", "id")
        )
    }

    @Test
    fun importedHeartRateWindowBoundariesAreEnforced() {
        val values = listOf(
            CardioWindowedValue(999L, 90),
            CardioWindowedValue(1_000L, 100),
            CardioWindowedValue(1_500L, 110),
            CardioWindowedValue(2_000L, 120),
            CardioWindowedValue(2_001L, 130)
        )
        assertEquals(
            listOf(100, 110, 120),
            CardioHealthConnectRules.valuesWithinWindow(values, 1_000, 2_000).map { it.value }
        )
    }

    @Test
    fun partiallyMissingMetricsAreNotFabricated() {
        val metadata = CardioHeartRateSummary(
            sampleCount = 0,
            heartRateCoveragePct = 0.0
        ).toMetadata()
        assertFalse(metadata.containsKey("avgHeartRate"))
        assertFalse(metadata.containsKey("minHeartRate"))
        assertFalse(metadata.containsKey("maxHeartRate"))
        assertEquals("0", metadata["heartRateSampleCount"])
    }

    @Test
    fun unknownActivityIsPreservedAsUnrecognisedGeneralCardio() {
        val mapping = CardioHealthConnectRules.mapActivity(9_999)
        assertEquals(CardioActivityType.GENERAL_CARDIO, mapping.activity)
        assertFalse(mapping.recognised)
        assertEquals(9_999, mapping.originalExerciseType)
    }

    @Test
    fun timelineDownsamplingIsBoundedAndPreservesEndpoints() {
        val input = (0 until 2_000).map { hr(it, 80 + (it % 120)) }
        val output = CardioHeartRateTimeline.downsample(input, maxPoints = 100)
        assertTrue(output.size <= 100)
        assertEquals(input.first().timestampEpochMs, output.first().timestampEpochMs)
        assertEquals(input.last().timestampEpochMs, output.last().timestampEpochMs)
        assertTrue(output.maxOf { it.bpm } >= 195)
    }

    @Test
    fun duplicateSamplesDoNotInflateStats() {
        val duplicate = hr(5, 120)
        val summary = CardioHeartRateProcessor.summarise(
            listOf(hr(0, 100), duplicate, duplicate, hr(10, 140)),
            listOf(CardioActiveWindow(0, 15_000))
        )
        assertEquals(3, summary.sampleCount)
        assertEquals(120, summary.averageBpm)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun sessionStopClosesSensorCollection() = runTest {
        val client = FakeBleHeartRateClient()
        val provider = GenericBleHeartRateProvider(client, scope = this)
        runCurrent()
        val collected = mutableListOf<CardioHeartRateSample>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            provider.heartRateSamples.collect { collected += it }
        }
        provider.startSession("session", 0)
        client.emit(BleHeartRateClientEvent.HeartRatePacket(byteArrayOf(0, 100), 1_000))
        runCurrent()
        provider.stopSession(2_000)
        client.emit(BleHeartRateClientEvent.HeartRatePacket(byteArrayOf(0, 110), 3_000))
        runCurrent()
        assertEquals(1, collected.size)
        assertEquals(100, collected.single().bpm)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun sensorDisconnectDoesNotCrashActiveWorkout() = runTest {
        val client = FakeBleHeartRateClient()
        val provider = GenericBleHeartRateProvider(client, scope = this)
        runCurrent()
        provider.startSession("session", 0)
        client.emit(BleHeartRateClientEvent.Connected("Chest strap", "sensor"))
        client.emit(BleHeartRateClientEvent.HeartRatePacket(byteArrayOf(0, 95), 1_000))
        client.emit(BleHeartRateClientEvent.Disconnected("link lost"))
        runCurrent()
        assertEquals(CardioSensorConnectionState.DISCONNECTED, provider.state.value.connection)
        // Session remains open at the provider layer; a later reconnect can continue emitting.
        client.emit(BleHeartRateClientEvent.Connected("Chest strap", "sensor"))
        client.emit(BleHeartRateClientEvent.HeartRatePacket(byteArrayOf(0, 96), 2_000))
        runCurrent()
        assertEquals(96, provider.state.value.currentHeartRateBpm)
    }

    @Test
    fun pausedTimeDoesNotCountTowardCoverage() {
        val collector = CardioSessionHeartRateCollector()
        collector.start("session", 0)
        collector.accept(hr(0, 100))
        collector.accept(hr(5, 105))
        collector.pause(10_000)
        collector.resume(30_000)
        collector.accept(hr(30, 110))
        collector.accept(hr(35, 115))
        val summary = collector.stop(40_000)
        assertEquals(20, summary.measuredSeconds)
        assertEquals(100.0, summary.heartRateCoveragePct)
    }

    @Test
    fun stoppedCollectorRejectsLaterSamples() {
        val collector = CardioSessionHeartRateCollector()
        collector.start("session", 0)
        assertTrue(collector.accept(hr(1, 100)))
        collector.stop(5_000)
        assertFalse(collector.accept(hr(6, 110)))
    }
}

private class FakeBleHeartRateClient : BleHeartRateClient {
    private val mutableEvents = MutableSharedFlow<BleHeartRateClientEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<BleHeartRateClientEvent> = mutableEvents.asSharedFlow()

    private val mutableDevices = MutableStateFlow<List<BleHeartRateDevice>>(emptyList())
    override val scannedDevices: StateFlow<List<BleHeartRateDevice>> = mutableDevices.asStateFlow()

    override fun requiredPermissions(): Array<String> = emptyArray()
    override fun hasPermissions(): Boolean = true
    override suspend fun scan() {
        emit(BleHeartRateClientEvent.ScanStarted)
    }
    override suspend fun connectDevice(sensorId: String) {
        emit(BleHeartRateClientEvent.Connected("Fake", sensorId))
    }
    override suspend fun reconnectSaved() {
        emit(BleHeartRateClientEvent.Reconnecting(1))
    }
    override suspend fun disconnect() {
        emit(BleHeartRateClientEvent.Disconnected("fake disconnect"))
    }

    suspend fun emit(event: BleHeartRateClientEvent) {
        mutableEvents.emit(event)
    }
}
