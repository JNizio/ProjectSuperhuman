package com.projectsuperhuman.next.trudy

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TrudyOutputTest {
    @Test
    fun nonEmotionalRequestsPassThroughDecoratorUnchanged() = runTest {
        var calls = 0
        val delegate = object : TrudyModelClient {
            override suspend fun complete(request: TrudyModelRequest): TrudyModelResult {
                calls += 1
                return TrudyModelResult(responseText = "delegate")
            }
        }
        val result = TrudyEmotionalModelClient(delegate).complete(TrudyModelRequest(userRequest = "How was my sleep?"))
        assertEquals("delegate", result.responseText)
        assertEquals(1, calls)
    }
}
