package com.flashalpha.historical;

import com.google.gson.JsonObject;

/** HTTP 404 with error="no_data" — (symbol, at) outside coverage or in a gap. */
public class NoDataException extends FlashAlphaHistoricalException {
    public NoDataException(String message, JsonObject response) {
        super(message, 404, response);
    }
}
