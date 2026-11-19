package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Typed response for {@code GET /v1/exposure/strike-dependency/{symbol}?at=...} (Alpha+, SPY
 * and QQQ).
 *
 * <p>Attributes the gamma at one exact observed SPY or QQQ strike to its expiries: does the
 * level depend on a single maturity? It reports concentration at that strike, not a
 * support/resistance probability.
 *
 * <p>Replays the same calculation at {@code at}. Results are model reconstructions from
 * archived quotes; original publication and OI revision times are unverified.
 *
 * <p>Obtain via {@link FlashAlphaHistoricalClient#strikeDependencyTyped(String, String, double)}. Shared
 * metadata ({@code snapshot_id}, {@code data_status}, {@code reasons}, timestamps, units) is
 * on {@link GammaContextMetadata}.
 */
public final class StrikeDependencyResponse extends GammaContextMetadata {

    /** The requested strike. Can be fractional. */
    @SerializedName("strike")
    public Double strike;

    @SerializedName("distance_from_spot_pct")
    public Double distanceFromSpotPct;

    @SerializedName("gross_gex_usd")
    public Double grossGexUsd;

    @SerializedName("net_gex_usd")
    public Double netGexUsd;

    @SerializedName("call_gex_usd")
    public Double callGexUsd;

    @SerializedName("put_gex_usd")
    public Double putGexUsd;

    @SerializedName("contract_count")
    public Integer contractCount;

    /** The strike's gamma split by expiry. */
    @SerializedName("by_expiry")
    public List<GammaExpiryRow> byExpiry;

    /** {@code yyyy-MM-dd}. */
    @SerializedName("dominant_expiry")
    public String dominantExpiry;

    @SerializedName("dominant_expiry_share_pct")
    public Double dominantExpirySharePct;
}
