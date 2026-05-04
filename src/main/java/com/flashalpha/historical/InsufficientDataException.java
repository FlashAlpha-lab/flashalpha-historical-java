package com.flashalpha.historical;

import com.google.gson.JsonObject;

/** HTTP 404 with error="insufficient_data" — surface grid can't be built. */
public class InsufficientDataException extends FlashAlphaHistoricalException {
    public InsufficientDataException(String message, JsonObject response) {
        super(message, 404, response);
    }
}
