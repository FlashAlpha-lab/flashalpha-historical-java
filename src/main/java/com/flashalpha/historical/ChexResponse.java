package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Typed response model for {@code GET /v1/exposure/chex/{symbol}?at=...}.
 *
 * <p>Strike-by-strike net dealer charm exposure (CHEX) plus the headline
 * {@link #netChex} scalar and a plain-English
 * {@link #chexInterpretation} of the prevailing decay-driven hedging
 * pressure.
 */
public final class ChexResponse extends FlashAlphaResponse {

    @SerializedName("symbol")
    public String symbol;

    @SerializedName("underlying_price")
    public Double underlyingPrice;

    @SerializedName("as_of")
    public String asOf;

    /** Sum of {@code ChexStrikeRow.netChex} across all strikes. */
    @SerializedName("net_chex")
    public Double netChex;

    /** Plain-English interpretation of the net charm regime. */
    @SerializedName("chex_interpretation")
    public String chexInterpretation;

    /** Per-strike CHEX breakdown rows. */
    @SerializedName("strikes")
    public List<ChexStrikeRow> strikes;

    /** One strike of the CHEX breakdown. */
    public static final class ChexStrikeRow {
        @SerializedName("strike") public Double strike;
        @SerializedName("call_chex") public Double callChex;
        @SerializedName("put_chex") public Double putChex;
        /** {@code call_chex + put_chex}. */
        @SerializedName("net_chex") public Double netChex;
    }
}
