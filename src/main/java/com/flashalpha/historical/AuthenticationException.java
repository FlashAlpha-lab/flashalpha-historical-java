package com.flashalpha.historical;

import com.google.gson.JsonObject;

/** HTTP 401 — invalid or missing API key. */
public class AuthenticationException extends FlashAlphaHistoricalException {
    public AuthenticationException(String message, JsonObject response) {
        super(message, 401, response);
    }
}
