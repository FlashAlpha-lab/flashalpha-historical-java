package com.flashalpha.historical;

import com.google.gson.JsonObject;

/** HTTP 403 — user tier below Alpha (every Historical endpoint requires Alpha+). */
public class TierRestrictedException extends FlashAlphaHistoricalException {
    private final String currentPlan;
    private final String requiredPlan;

    public TierRestrictedException(String message, JsonObject response, String currentPlan, String requiredPlan) {
        super(message, 403, response);
        this.currentPlan = currentPlan;
        this.requiredPlan = requiredPlan;
    }

    public String getCurrentPlan() { return currentPlan; }
    public String getRequiredPlan() { return requiredPlan; }
}
