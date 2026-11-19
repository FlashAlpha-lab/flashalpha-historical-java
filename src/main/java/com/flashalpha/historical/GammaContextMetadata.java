package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Metadata shared by {@link ExpiryImpactResponse} and {@link StrikeDependencyResponse}
 * (SPY and QQQ gamma context). Extends {@link FlashAlphaResponse}, so both responses also
 * carry {@code endpoint_version}, {@code data_as_of} and {@code archive_as_of}.
 *
 * <p>Timestamps are ISO 8601 UTC strings. Numbers are boxed, so a missing value stays null.
 */
public abstract class GammaContextMetadata extends FlashAlphaResponse {

    /** {@code SPY} or {@code QQQ}. */
    @SerializedName("symbol")
    public String symbol;

    /** Underlying price the calculation used. */
    @SerializedName("underlying_price")
    public Double underlyingPrice;

    /** SHA-256 fingerprint of the captured inputs; identical inputs give identical IDs. */
    @SerializedName("snapshot_id")
    public String snapshotId;

    @SerializedName("generated_at")
    public String generatedAt;

    @SerializedName("quote_at")
    public String quoteAt;

    @SerializedName("greeks_oldest_at")
    public String greeksOldestAt;

    @SerializedName("greeks_newest_at")
    public String greeksNewestAt;

    /** For example {@code gamma-context-v2}. */
    @SerializedName("calculation_version")
    public String calculationVersion;

    /** How the Greeks were priced, for example {@code historical_quote_repriced_dated_inputs}. */
    @SerializedName("pricing_basis")
    public String pricingBasis;

    /**
     * {@code partial} in normal operation (open-interest vintage is unknown), {@code stale}
     * when an input predates the market-session freshness cutoff, {@code unavailable} when no
     * contract qualifies.
     */
    @SerializedName("data_status")
    public String dataStatus;

    /** Machine codes and excluded-contract counts behind {@link #dataStatus}. */
    @SerializedName("reasons")
    public List<GammaContextReason> reasons;

    @SerializedName("oi_as_of")
    public String oiAsOf;

    @SerializedName("oi_vintage_status")
    public String oiVintageStatus;

    @SerializedName("eligible_contract_count")
    public Integer eligibleContractCount;

    @SerializedName("rejected_contract_count")
    public Integer rejectedContractCount;

    @SerializedName("observed_min_strike")
    public Double observedMinStrike;

    @SerializedName("observed_max_strike")
    public Double observedMaxStrike;

    /** {@code usd_delta_notional_per_1pct_move}. */
    @SerializedName("gex_unit")
    public String gexUnit;

    /** {@code calls_positive_puts_negative}. */
    @SerializedName("sign_convention")
    public String signConvention;

    /** {@code expiry_removal_sensitivity} or {@code strike_attribution}. */
    @SerializedName("analysis_type")
    public String analysisType;
}
