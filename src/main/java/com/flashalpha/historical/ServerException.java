package com.flashalpha.historical;

import com.google.gson.JsonObject;

/** HTTP 5xx server errors. */
public class ServerException extends FlashAlphaHistoricalException {
    public ServerException(String message, int statusCode, JsonObject response) {
        super(message, statusCode, response);
    }
}
