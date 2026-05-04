package com.flashalpha.historical;

import com.google.gson.JsonObject;

/** HTTP 404 with error="symbol_not_found" — symbol has no data at the requested {@code at}. */
public class SymbolNotFoundException extends FlashAlphaHistoricalException {
    public SymbolNotFoundException(String message, JsonObject response) {
        super(message, 404, response);
    }
}
