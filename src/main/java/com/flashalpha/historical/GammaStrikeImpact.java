package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

/** One strike with current, excluded and remaining gross/net GEX (USD per 1% move). */
public final class GammaStrikeImpact {

    /** Strike price. Can be fractional. */
    @SerializedName("strike")
    public Double strike;

    @SerializedName("distance_from_spot_pct")
    public Double distanceFromSpotPct;

    @SerializedName("current_gross_gex_usd")
    public Double currentGrossGexUsd;

    @SerializedName("current_net_gex_usd")
    public Double currentNetGexUsd;

    @SerializedName("excluded_gross_gex_usd")
    public Double excludedGrossGexUsd;

    @SerializedName("excluded_net_gex_usd")
    public Double excludedNetGexUsd;

    @SerializedName("remaining_gross_gex_usd")
    public Double remainingGrossGexUsd;

    @SerializedName("remaining_net_gex_usd")
    public Double remainingNetGexUsd;

    @SerializedName("excluded_share_at_strike_pct")
    public Double excludedShareAtStrikePct;
}
