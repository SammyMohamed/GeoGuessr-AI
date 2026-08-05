package com.geoguessr.inference

/** Base type for anything that can go wrong talking to the inference service. */
sealed class InferenceException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** The uploaded file wasn't a valid image (HTTP 400). Not worth retrying —
 * the image itself is the problem, not a transient failure. */
class InvalidImageException(message: String) : InferenceException(message)

/** The service is up but not ready yet, e.g. models still loading (HTTP 503). */
class InferenceServiceUnavailableException(message: String) : InferenceException(message)

/** The request didn't complete within the configured timeout. */
class InferenceTimeoutException(message: String, cause: Throwable? = null) :
    InferenceException(message, cause)

/** Catch-all for anything else unexpected — non-2xx responses we don't have
 * a specific case for, or retries exhausted after transient failures. */
class InferenceServiceException(message: String, val statusCode: Int? = null) :
    InferenceException(message)
