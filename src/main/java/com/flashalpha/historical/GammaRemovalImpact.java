package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** {@link ExpiryImpactResponse#impact}: what excluding the selected expiry changes. */
public final class GammaRemovalImpact {

    /** {@code 100 × excluded gross / current gross}. Null when current gross is zero. */
    @SerializedName("removed_gross_share_pct")
    public Double removedGrossSharePct;

    @SerializedName("retained_gross_share_pct")
    public Double retainedGrossSharePct;

    /** {@code remaining net − current net} (minus the excluded net). */
    @SerializedName("net_gex_change_usd")
    public Double netGexChangeUsd;

    /** Sign of the modeled net total before exclusion. */
    @SerializedName("net_sign_before")
    public String netSignBefore;

    /** Sign of the modeled net total after exclusion. */
    @SerializedName("net_sign_after")
    public String netSignAfter;

    @SerializedName("net_sign_changed")
    public Boolean netSignChanged;

    /** Top {@code limit} strikes by gross removed. */
    @SerializedName("largest_gross_reductions")
    public List<GammaStrikeImpact> largestGrossReductions;

    /** Top {@code limit} strikes by gross remaining. */
    @SerializedName("strongest_remaining_concentrations")
    public List<GammaStrikeImpact> strongestRemainingConcentrations;
}
