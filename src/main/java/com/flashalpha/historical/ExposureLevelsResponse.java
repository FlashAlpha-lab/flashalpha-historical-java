package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

/**
 * Typed response model for {@code GET /v1/exposure/levels/{symbol}?at=...}.
 *
 * <p>Point-in-time replay of FlashAlpha's "key levels" view — the
 * canonical dealer-derived strikes (gamma flip, max positive / negative
 * gamma, call wall, put wall, highest-OI strike, 0DTE magnet) as they
 * stood at {@code at}. Cheapest endpoint when you only need the strikes
 * a trader would have drawn on a chart at that minute.
 *
 * <p>See <a href="https://lab.flashalpha.com">https://lab.flashalpha.com</a>.
 */
public final class ExposureLevelsResponse {

    @SerializedName("symbol")
    public String symbol;

    @SerializedName("underlying_price")
    public Double underlyingPrice;

    /** ET timestamp the snapshot was computed for (snapped to nearest available minute). */
    @SerializedName("as_of")
    public String asOf;

    @SerializedName("levels")
    public Levels levels;

    /** Canonical dealer-derived strikes. */
    public static final class Levels {
        /**
         * Strike where net dealer gamma crossed zero at {@code at}.
         * Spot above = positive_gamma regime, below = negative_gamma.
         */
        @SerializedName("gamma_flip") public Double gammaFlip;

        @SerializedName("max_positive_gamma") public Double maxPositiveGamma;
        @SerializedName("max_negative_gamma") public Double maxNegativeGamma;

        /** Resistance strike — highest absolute call GEX. */
        @SerializedName("call_wall") public Double callWall;

        /** Support strike — highest absolute put GEX. */
        @SerializedName("put_wall") public Double putWall;

        @SerializedName("highest_oi_strike") public Double highestOiStrike;

        /** Same-day-expiration magnet strike — intraday price magnet. */
        @SerializedName("zero_dte_magnet") public Double zeroDteMagnet;
    }
}
