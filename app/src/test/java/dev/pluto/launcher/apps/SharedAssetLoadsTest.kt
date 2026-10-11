package dev.pluto.launcher.apps

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SharedAssetLoadsTest {
    @Test
    fun concurrentScreensShareOneLoadAndCallerCancellationDoesNotDiscardIt() = runTest {
        val loads = SharedAssetLoads<String, String>(backgroundScope)
        val ready = CompletableDeferred<String>()
        var decodes = 0
        val first = async { loads.load("cover") { decodes++; ready.await() } }
        val second = async { loads.load("cover") { decodes++; ready.await() } }
        runCurrent()
        assertEquals(1, decodes)
        first.cancelAndJoin()
        ready.complete("art")
        assertEquals("art", second.await())
        assertEquals(1, decodes)
    }

    @Test
    fun completedLoadsAreReleasedAndOtherKeysLoadIndependently() = runTest {
        val loads = SharedAssetLoads<String, Int>(backgroundScope)
        val blocked = CompletableDeferred<Int>()
        val first = async { loads.load("first") { blocked.await() } }
        runCurrent()
        assertEquals(2, loads.load("second") { 2 })
        blocked.complete(1)
        assertEquals(1, first.await())
        assertEquals(3, loads.load("first") { 3 })
    }
    @Test
    fun lastConsumerLeavingCancelsWorkAndReturningConsumerStartsFresh() = runTest {
        val loads = SharedAssetLoads<String, Int>(backgroundScope)
        var cancelled = false
        val first = async { loads.load("cover") {
            try { awaitCancellation() } finally { cancelled = true }
        } }
        runCurrent()
        first.cancelAndJoin()
        runCurrent()
        assertTrue(cancelled)
        assertTrue(loads.keysSnapshot().isEmpty())
        assertEquals(7, loads.load("cover") { 7 })
    }

    @Test
    fun abandonedQueuedRequestNeverRunsItsDecoder() = runTest {
        // A separate worker scheduler models a saturated decode pool; caller cancellation
        // is delivered before that pool gets a chance to execute the queued decode.
        val workerScheduler = kotlinx.coroutines.test.TestCoroutineScheduler()
        val workerJob = kotlinx.coroutines.SupervisorJob()
        val workerScope = kotlinx.coroutines.CoroutineScope(workerJob +
            kotlinx.coroutines.test.StandardTestDispatcher(workerScheduler))
        try {
            val loads = SharedAssetLoads<String, Int>(workerScope)
            var decodes = 0
            val first = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                loads.load("queued") { decodes++; 1 }
            }
            first.cancelAndJoin()
            workerScheduler.runCurrent()
            assertEquals(0, decodes)
            assertTrue(loads.keysSnapshot().isEmpty())
        } finally { workerJob.cancel() }
    }
}
