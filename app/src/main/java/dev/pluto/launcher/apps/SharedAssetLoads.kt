package dev.pluto.launcher.apps

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** Shared loads survive individual cancellation, but not the departure of their last consumer. */
internal class SharedAssetLoads<K, V>(private val scope: CoroutineScope) {
    private class Request<V>(val work: Deferred<V>, var consumers: Int = 0)
    private val pending = mutableMapOf<K, Request<V>>()

    fun keysSnapshot(): List<K> = synchronized(pending) { pending.keys.toList() }

    suspend fun load(key: K, block: suspend () -> V): V {
        val request = synchronized(pending) {
            val request = pending[key] ?: Request(scope.async(start = CoroutineStart.LAZY) { block() }).also { request ->
                pending[key] = request
                request.work.invokeOnCompletion {
                    synchronized(pending) { if (pending[key] === request) pending.remove(key) }
                }
            }
            request.consumers++
            request
        }
        try {
            request.work.start()
            return request.work.await()
        } finally {
            synchronized(pending) {
                request.consumers--
                if (request.consumers == 0 && !request.work.isCompleted) {
                    // Remove first so a returning consumer can start fresh rather than join a cancelled job.
                    if (pending[key] === request) pending.remove(key)
                    request.work.cancel()
                }
            }
        }
    }
}
