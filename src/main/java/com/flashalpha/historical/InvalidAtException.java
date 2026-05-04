package com.flashalpha.historical;

import com.google.gson.JsonObject;

/** HTTP 400 with error="invalid_at" — the {@code at} parameter is missing or malformed. */
public class InvalidAtException extends FlashAlphaHistoricalException {
    public InvalidAtException(String message, JsonObject response) {
        super(message, 400, response);
    }
}
