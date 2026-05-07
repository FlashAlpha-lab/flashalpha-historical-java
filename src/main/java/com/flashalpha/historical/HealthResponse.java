package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

/**
 * Typed response model for {@code GET /health} on the historical API.
 *
 * <p>Public liveness probe. Returns a single {@link #status} string —
 * typically {@code "ok"} when the API is healthy.
 */
public final class HealthResponse {

    /** Health status string — typically {@code "ok"}. */
    @SerializedName("status")
    public String status;
}
