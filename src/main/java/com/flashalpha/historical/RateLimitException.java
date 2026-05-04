package com.flashalpha.historical;

import com.google.gson.JsonObject;

/** HTTP 429 — daily quota exhausted (shared with the live API). */
public class RateLimitException extends FlashAlphaHistoricalException {
    private final Integer retryAfter;

    public RateLimitException(String message, JsonObject response, Integer retryAfter) {
        super(message, 429, response);
        this.retryAfter = retryAfter;
    }

    public Integer getRetryAfter() { return retryAfter; }
}
