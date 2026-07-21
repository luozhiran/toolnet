package com.itg.net.result

/**
 * Indicates that a request result was intercepted and consumed by business logic.
 *
 * Flow helpers can catch this exception separately so interception handling does not
 * leak into generic error handlers.
 */
class NetResultInterceptedException(
    override val message: String? = null,
    override val cause: Throwable? = null
) : RuntimeException(message, cause)