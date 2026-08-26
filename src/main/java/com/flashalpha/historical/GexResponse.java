package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Typed response model for {@code GET /v1/exposure/gex/{symbol}?at=...}.
 *
 * <p>Strike-by-strike net dealer gamma exposure (GEX) plus the
 * regime-defining {@link #gammaFlip}, the headline {@link #netGex}
 * scalar, and a {@link #netGexLabel} regime classification.
 */
public final class GexResponse extends FlashAlphaResponse {

    @SerializedName("symbol")
    public String symbol;

    @SerializedName("underlying_price")
    public Double underlyingPrice;

    @SerializedName("as_of")
    public String asOf;

    /** Strike where net dealer gamma crosses zero — the regime boundary. */
    @SerializedName("gamma_flip")
    public Double gammaFlip;

    /** Sum of {@code GexStrikeRow.netGex} across all strikes. */
    @SerializedName("net_gex")
    public Double netGex;

    /**
     * Regime classification, e.g. {@code "positive_gamma"} |
     * {@code "negative_gamma"} | {@code "neutral_gamma"}.
     */
    @SerializedName("net_gex_label")
    public String netGexLabel;

    /** Per-strike GEX breakdown rows. */
    @SerializedName("strikes")
    public List<GexStrikeRow> strikes;

    /** One strike of the GEX breakdown. */
    public static final class GexStrikeRow {
        @SerializedName("strike") public Double strike;
        @SerializedName("call_gex") public Double callGex;
        @SerializedName("put_gex") public Double putGex;
        /** {@code call_gex + put_gex}. */
        @SerializedName("net_gex") public Double netGex;
        @SerializedName("call_oi") public Long callOi;
        @SerializedName("put_oi") public Long putOi;
        @SerializedName("call_volume") public Long callVolume;
        @SerializedName("put_volume") public Long putVolume;
        @SerializedName("call_oi_change") public Long callOiChange;
        @SerializedName("put_oi_change") public Long putOiChange;
    }
}
