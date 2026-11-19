package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Typed response for {@code GET /v1/exposure/expiry-impact/{symbol}?at=...} (Alpha+, SPY and
 * QQQ).
 *
 * <p>Compares the current SPY or QQQ gamma distribution with the same captured inputs after
 * excluding one expiry. Spot, Greeks and open interest are held fixed, so this measures how
 * much of today's concentration depends on one maturity. It is not a forecast of post-expiry
 * prices, dealer hedging or the next session's chain.
 *
 * <p>Replays the same calculation at {@code at}. Results are model reconstructions from
 * archived quotes; original publication and OI revision times are unverified.
 *
 * <p>Obtain via {@link FlashAlphaHistoricalClient#expiryImpactTyped(String, String)}. Shared
 * metadata ({@code snapshot_id}, {@code data_status}, {@code reasons}, timestamps, units) is
 * on {@link GammaContextMetadata}.
 */
public final class ExpiryImpactResponse extends GammaContextMetadata {

    /** {@code yyyy-MM-dd}. The expiry that was excluded. */
    @SerializedName("selected_expiry")
    public String selectedExpiry;

    /** How the expiry was chosen, for example {@code nearest_active}. */
    @SerializedName("selection_source")
    public String selectionSource;

    /** {@code yyyy-MM-dd} values accepted by the {@code expiry} parameter. */
    @SerializedName("available_expiries")
    public List<String> availableExpiries;

    /** The whole eligible chain. */
    @SerializedName("current")
    public GammaTotals current;

    /** The selected expiry alone. */
    @SerializedName("excluded_expiry")
    public GammaTotals excludedExpiry;

    /**
     * The chain without the selected expiry. {@link GammaTotals#status} is {@code available},
     * {@code zero_gross_exposure} or {@code no_remaining_contracts}.
     */
    @SerializedName("excluding_expiry")
    public GammaTotals excludingExpiry;

    @SerializedName("impact")
    public GammaRemovalImpact impact;

    /** Every eligible strike with current, excluded and remaining gross/net GEX. */
    @SerializedName("strikes")
    public List<GammaStrikeImpact> strikes;

    /** Plain-language statements with their supporting values. */
    @SerializedName("facts")
    public List<GammaContextFact> facts;
}
