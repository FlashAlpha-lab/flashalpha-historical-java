package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

/**
 * Typed response model for {@code GET /v1/account} on the historical API.
 *
 * <p>Account identity and current quota usage for the authenticated key.
 * Use {@link #remaining} to gate workload before the next reset, and
 * {@link #resetsAt} (ET wall-clock string) to schedule replenished work.
 */
public final class AccountResponse {

    /** Stable opaque user identifier. */
    @SerializedName("user_id")
    public String userId;

    /** Account email address. */
    @SerializedName("email")
    public String email;

    /** Plan tier — e.g. {@code "free"}, {@code "growth"}, {@code "alpha"}. */
    @SerializedName("plan")
    public String plan;

    /**
     * Daily request quota for {@link #plan}. <b>String, not number</b>:
     * numeric (e.g. {@code "1000"}) on bounded plans, literal
     * {@code "unlimited"} on Alpha / Enterprise tiers.
     */
    @SerializedName("daily_limit")
    public String dailyLimit;

    /** Requests already consumed in the current quota window. */
    @SerializedName("usage_today")
    public Integer usageToday;

    /**
     * Requests remaining before the next reset. Numeric string on
     * bounded plans; literal {@code "unlimited"} on uncapped tiers.
     */
    @SerializedName("remaining")
    public String remaining;

    /** Timestamp at which the quota window resets (ET wall-clock string). */
    @SerializedName("resets_at")
    public String resetsAt;
}
