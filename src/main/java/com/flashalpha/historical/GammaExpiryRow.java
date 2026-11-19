package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

/** One expiry's share of the gamma at a strike, in {@link StrikeDependencyResponse#byExpiry}. */
public final class GammaExpiryRow extends GammaTotals {

    /** {@code yyyy-MM-dd}. */
    @SerializedName("expiry")
    public String expiry;

    @SerializedName("valuation_cutoff_at")
    public String valuationCutoffAt;

    @SerializedName("last_trading_at")
    public String lastTradingAt;

    @SerializedName("last_trading_status")
    public String lastTradingStatus;

    @SerializedName("settlement_at")
    public String settlementAt;

    @SerializedName("settlement_status")
    public String settlementStatus;

    @SerializedName("calendar_days_to_expiry")
    public Integer calendarDaysToExpiry;

    @SerializedName("gross_share_pct")
    public Double grossSharePct;

    @SerializedName("share_of_strike_gross_pct")
    public Double shareOfStrikeGrossPct;
}
