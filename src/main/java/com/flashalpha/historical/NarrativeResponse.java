package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Typed response model for {@code GET /v1/exposure/narrative/{symbol}?at=...} (Alpha+).
 *
 * <p>Point-in-time replay of FlashAlpha's <b>LLM-friendly verbal
 * output</b>. Every string in {@link #narrative} is the historical,
 * server-generated explanation of dealer flow as of the {@code at}
 * timestamp — safe to surface verbatim in agent transcripts or
 * backtest reports. {@link Narrative#data} carries the raw numbers
 * the strings were generated from.
 *
 * <p>See <a href="https://lab.flashalpha.com">https://lab.flashalpha.com</a>.
 */
public final class NarrativeResponse {

    @SerializedName("symbol")
    public String symbol;

    @SerializedName("underlying_price")
    public Double underlyingPrice;

    /** ET timestamp the snapshot was generated for (snapped to nearest available minute). */
    @SerializedName("as_of")
    public String asOf;

    @SerializedName("narrative")
    public Narrative narrative;

    /** Verbal-output block. Each string is safe to render verbatim. */
    public static final class Narrative {
        @SerializedName("regime") public String regime;
        @SerializedName("gex_change") public String gexChange;
        @SerializedName("key_levels") public String keyLevels;
        @SerializedName("flow") public String flow;
        @SerializedName("vanna") public String vanna;
        @SerializedName("charm") public String charm;
        @SerializedName("zero_dte") public String zeroDte;
        @SerializedName("outlook") public String outlook;

        /** Raw numbers backing each narrative string above. */
        @SerializedName("data") public NarrativeData data;
    }

    /** Numeric backing for each narrative string. */
    public static final class NarrativeData {
        @SerializedName("net_gex") public Double netGex;
        @SerializedName("net_gex_prior") public Double netGexPrior;
        @SerializedName("net_gex_change_pct") public Double netGexChangePct;
        @SerializedName("vix") public Double vix;
        @SerializedName("gamma_flip") public Double gammaFlip;
        @SerializedName("call_wall") public Double callWall;
        @SerializedName("put_wall") public Double putWall;
        /** {@code "positive_gamma"} | {@code "negative_gamma"} | {@code "undetermined"}. */
        @SerializedName("regime") public String regime;
        @SerializedName("zero_dte_pct") public Double zeroDtePct;
        @SerializedName("top_oi_changes") public List<OiChangeRow> topOiChanges;
    }

    /** One row of the top-OI-changes list. */
    public static final class OiChangeRow {
        @SerializedName("strike") public Double strike;
        @SerializedName("type") public String type;
        @SerializedName("oi_change") public Long oiChange;
        @SerializedName("volume") public Long volume;
    }
}
