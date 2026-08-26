package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Typed response model for {@code GET /v1/exposure/vex/{symbol}?at=...}.
 *
 * <p>Strike-by-strike net dealer vanna exposure (VEX) plus the headline
 * {@link #netVex} scalar and a plain-English
 * {@link #vexInterpretation} of the prevailing dealer-vol cross-effect.
 */
public final class VexResponse extends FlashAlphaResponse {

    @SerializedName("symbol")
    public String symbol;

    @SerializedName("underlying_price")
    public Double underlyingPrice;

    @SerializedName("as_of")
    public String asOf;

    /** Sum of {@code VexStrikeRow.netVex} across all strikes. */
    @SerializedName("net_vex")
    public Double netVex;

    /** Plain-English interpretation of the net vanna regime. */
    @SerializedName("vex_interpretation")
    public String vexInterpretation;

    /** Per-strike VEX breakdown rows. */
    @SerializedName("strikes")
    public List<VexStrikeRow> strikes;

    /** One strike of the VEX breakdown. */
    public static final class VexStrikeRow {
        @SerializedName("strike") public Double strike;
        @SerializedName("call_vex") public Double callVex;
        @SerializedName("put_vex") public Double putVex;
        /** {@code call_vex + put_vex}. */
        @SerializedName("net_vex") public Double netVex;
    }
}
