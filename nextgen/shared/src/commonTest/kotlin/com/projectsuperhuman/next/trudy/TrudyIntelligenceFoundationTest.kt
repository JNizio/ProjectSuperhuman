package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class TrudyIntelligenceFoundationTest {
    @Test fun pearsonFindsKnownPositiveAssociation() {
        val pairs=(1..10).map { n -> TrudyStatistics.AlignedPair(metric(HealthDomain.EXERCISE,"load",n.toDouble(),n.toLong()),metric(HealthDomain.SLEEP,"score",n*2.0,n.toLong())) }
        assertTrue(TrudyStatistics.pearson(pairs)!! > .99)
    }

    @Test fun noCorrelationCaseIsNearZero() {
        val ys=listOf(1.0,-1.0,1.0,-1.0,1.0,-1.0,1.0,-1.0)
        val pairs=ys.indices.map { i -> TrudyStatistics.AlignedPair(metric(HealthDomain.EXERCISE,"load",(i+1).toDouble(),i.toLong()),metric(HealthDomain.SLEEP,"score",ys[i],i.toLong())) }
        assertTrue(abs(TrudyStatistics.pearson(pairs)!!) < .25)
    }

    @Test fun sparseAssociationIsRejected() = runBlocking {
        val source=FakeSource(mapOf(key(HealthDomain.EXERCISE,"load") to (1..4).map { metric(HealthDomain.EXERCISE,"load",it.toDouble(),it.toLong()) },key(HealthDomain.SLEEP,"score") to (1..4).map { metric(HealthDomain.SLEEP,"score",it.toDouble(),it.toLong()) }))
        val r=TrudyPersonalEvidenceLibrary(source).association(HealthDomain.EXERCISE,"load",HealthDomain.SLEEP,"score",alignmentWindowMs=0)
        assertNull(r.coefficient); assertEquals(TrudyConfidence.INSUFFICIENT,r.confidence); assertEquals(PersonalEvidenceType.INSUFFICIENT_EVIDENCE,r.evidence.evidenceType)
    }

    @Test fun missingDataAndLagAlignmentAreBounded() {
        val left=(1..6).map { metric(HealthDomain.EXERCISE,"load",it.toDouble(),it*1000L) }
        val missing=listOf(1,2,5,6).map { metric(HealthDomain.SLEEP,"score",it.toDouble(),it*1000L) }
        assertEquals(4,TrudyStatistics.align(left,missing,alignmentWindowMs=0).size)
        val lagged=(1..6).map { metric(HealthDomain.SLEEP,"score",it.toDouble(),it*1000L+500) }
        assertEquals(0,TrudyStatistics.align(left,lagged,lagMs=0,alignmentWindowMs=0).size)
        assertEquals(6,TrudyStatistics.align(left,lagged,lagMs=500,alignmentWindowMs=0).size)
    }

    @Test fun baselineComparisonCalculatesDelta() = runBlocking {
        val d=HealthDomain.SLEEP
        val values=listOf(metric(d,"sleep_score",60.0,100),metric(d,"sleep_score",70.0,200),metric(d,"sleep_score",80.0,300),metric(d,"sleep_score",90.0,400))
        val r=TrudyPersonalEvidenceLibrary(FakeSource(mapOf(key(d,"sleep_score") to values))).compareBaseline(d,"sleep_score",TrudyTimeRange(300,400),TrudyTimeRange(100,200))
        assertEquals(85.0,r.observationMean); assertEquals(65.0,r.baselineMean); assertEquals(20.0,r.absoluteDelta); assertEquals(TrudyEffectDirection.INCREASE,r.direction)
    }

    @Test fun confidenceThresholdsAreTransparent() {
        assertEquals(TrudyConfidence.INSUFFICIENT,TrudyConfidenceModel.classify(4))
        assertEquals(TrudyConfidence.LOW,TrudyConfidenceModel.classify(7,signalMagnitude=.6))
        assertTrue(TrudyConfidenceModel.classify(30,signalMagnitude=.8,repeated=true).ordinal >= TrudyConfidence.MODERATE.ordinal)
    }

    @Test fun associationNeverBecomesCausalAndSameMetricNamesStaySeparate() = runBlocking {
        val left=(1..8).map { metric(HealthDomain.EXERCISE,"score",it.toDouble(),it.toLong()) }
        val right=(1..8).map { metric(HealthDomain.SLEEP,"score",it.toDouble(),it.toLong()) }
        val r=TrudyPersonalEvidenceLibrary(FakeSource(mapOf(key(HealthDomain.EXERCISE,"score") to left,key(HealthDomain.SLEEP,"score") to right))).association(HealthDomain.EXERCISE,"score",HealthDomain.SLEEP,"score",alignmentWindowMs=0)
        assertEquals(PersonalEvidenceType.ASSOCIATION,r.evidence.evidenceType)
        assertTrue(r.caveats.any { "causation" in it.lowercase() }); assertFalse(r.caveats.any { "caused" in it.lowercase() })
        assertTrue(r.evidence.supportingEvidenceReferences.any { it.domain==HealthDomain.EXERCISE && it.metricId=="score" })
        assertTrue(r.evidence.supportingEvidenceReferences.any { it.domain==HealthDomain.SLEEP && it.metricId=="score" })
    }

    @Test fun experimentResultAndInsufficientSamples() {
        val engine=TrudyExperimentEngine(); val h=engine.plan(TrudyExperimentKind.SLEEP_SCHEDULE_CONSISTENCY,HealthDomain.SLEEP,"sleep_score")
        val b=(1..7).map { metric(HealthDomain.SLEEP,"sleep_score",60.0+it,it.toLong()) }; val i=(8..14).map { metric(HealthDomain.SLEEP,"sleep_score",70.0+it,it.toLong()) }
        val r=engine.evaluate(h,b,i,1.0); assertTrue(r.absoluteChange!! > 0); assertTrue("consistent with" in r.summary.lowercase())
        val sparse=engine.evaluate(h,b.take(3),i.take(3),1.0); assertEquals(TrudyConfidence.INSUFFICIENT,sparse.confidence); assertEquals(TrudyExperimentConclusion.INCONCLUSIVE,sparse.conclusion)
    }

    @Test fun unsafeExperimentRequestsRejected() {
        val e=TrudyExperimentEngine(); assertTrue(e.rejectUnsafeFreeformIntervention("stop taking prescription medication")!=null); assertTrue(e.rejectUnsafeFreeformIntervention("change insulin dose")!=null); assertNull(e.rejectUnsafeFreeformIntervention("keep bedtime consistent"))
    }

    @Test fun sourceDomainLeakageIsRejected() = runBlocking {
        val source=object:TrudyPersonalEvidenceSource { override suspend fun metricHistory(domain:HealthDomain,metricId:String,limit:Int)=listOf(metric(HealthDomain.BODY,metricId,1.0,1)); override suspend fun dataQuality(domain:HealthDomain)=quality(domain) }
        var failed=false; try { TrudyPersonalEvidenceLibrary(source).association(HealthDomain.SLEEP,"score",HealthDomain.BODY,"score",alignmentWindowMs=0) } catch(_:IllegalArgumentException){ failed=true }; assertTrue(failed)
    }

    private class FakeSource(private val values:Map<String,List<TrudyMetricEvidence>>):TrudyPersonalEvidenceSource { override suspend fun metricHistory(domain:HealthDomain,metricId:String,limit:Int)=values[key(domain,metricId)].orEmpty().take(limit); override suspend fun dataQuality(domain:HealthDomain)=quality(domain) }
    companion object {
        private fun key(d:HealthDomain,m:String)="${d.name}/$m"
        private fun metric(d:HealthDomain,m:String,v:Double,t:Long)=TrudyMetricEvidence(d,m,v,"unit",t,source="test")
        private fun quality(d:HealthDomain)=TrudyDataQualityEvidence(d,90,100,2,1L,0.0,false,emptyList())
    }
}
