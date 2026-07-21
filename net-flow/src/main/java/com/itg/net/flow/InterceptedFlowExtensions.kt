package com.itg.net.flow

import com.itg.net.result.NetResultInterceptedException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch

/**
 * Handles request interception errors and consumes them.
 *
 * This is useful for moving interception-specific handling, such as risk control
 * or login takeover, out of the generic Flow catch block. Once handled here,
 * downstream catch operators will not receive the intercepted exception.
 */
fun <T> Flow<T>.catchIntercepted(
    action: suspend FlowCollector<T>.(NetResultInterceptedException) -> Unit
): Flow<T> {
    return catch { e ->
        if (e is NetResultInterceptedException) {
            action(e)
            return@catch
        }
        throw e
    }
}
