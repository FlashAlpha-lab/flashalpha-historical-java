package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Typed response model for {@code GET /v1/exposure/dex/{symbol}?at=...}.
 *
 * <p>Strike-by-strike net dealer delta exposure (DEX) plus the headline
 * {@link #netDex} scalar.
 */
public final class DexResponse extends FlashAlphaResponse {

    @SerializedName("symbol")
    public String symbol;

    @SerializedName("underlying_price")
    public Double underlyingPrice;

    @SerializedName("as_of")
    public String asOf;

    /** Sum of {@code DexStrikeRow.netDex} across all strikes. */
    @SerializedName("net_dex")
    public Double netDex;

    /** Per-strike DEX breakdown rows. */
    @SerializedName("strikes")
    public List<DexStrikeRow> strikes;

    /** One strike of the DEX breakdown. */
    public static final class DexStrikeRow {
        @SerializedName("strike") public Double strike;
        @SerializedName("call_dex") public Double callDex;
        @SerializedName("put_dex") public Double putDex;
        /** {@code call_dex + put_dex}. */
        @SerializedName("net_dex") public Double netDex;
    }
}
