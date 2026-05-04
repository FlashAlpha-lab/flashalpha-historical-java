package com.flashalpha.historical;

import com.google.gson.JsonObject;

/** Base exception for the FlashAlpha Historical SDK. */
public class FlashAlphaHistoricalException extends RuntimeException {
    private final int statusCode;
    private final JsonObject response;

    public FlashAlphaHistoricalException(String message, int statusCode, JsonObject response) {
        super(message);
        this.statusCode = statusCode;
        this.response = response;
    }

    public int getStatusCode() { return statusCode; }
    public JsonObject getResponse() { return response; }
}
