package com.flashalpha.historical;

import com.google.gson.JsonObject;

/** HTTP 404 with error="no_coverage" — symbol is not in the historical dataset. */
public class NoCoverageException extends FlashAlphaHistoricalException {
    public NoCoverageException(String message, JsonObject response) {
        super(message, 404, response);
    }
}
