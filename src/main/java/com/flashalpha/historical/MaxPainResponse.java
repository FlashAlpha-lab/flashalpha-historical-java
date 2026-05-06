package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Typed response model for {@code GET /v1/maxpain/{symbol}?at=...} (Basic+).
 *
 * <p>Same response shape as the live API with one operational diff:
 * {@link MaxPainOiRow#callVolume} and {@link MaxPainOiRow#putVolume} are
 * always {@code 0} on historical (the minute-resolution options table
 * doesn't carry intraday volume). Use {@code callOi} / {@code putOi} for
 * the historical positioning view; the volume fields are placeholders for
 * shape parity.
 *
 * <p>Max pain is the strike where total option-holder intrinsic value
 * across all OI in the chain is minimized — equivalently, the strike at
 * which dealers (the counterparty) lose the least to expiring contracts.
 *
 * <p>The endpoint accepts an optional {@code expiration} query filter
 * ({@code yyyy-MM-dd}). When present, the response is scoped to that single
 * expiry and {@link #maxPainByExpiration} is {@code null}.
 *
 * <p>Returns 403 {@code tier_restricted} for Free-tier users.
 */
public class MaxPainResponse {

    @SerializedName("symbol")
    public String symbol;

    @SerializedName("underlying_price")
    public Double underlyingPrice;

    @SerializedName("as_of")
    public String asOf;

    /** The headline number. Strike where total chain pain is minimized. */
    @SerializedName("max_pain_strike")
    public Double maxPainStrike;

    /** Distance from spot to {@link #maxPainStrike} (absolute, percent, direction). */
    @SerializedName("distance")
    public MaxPainDistance distance;

    /**
     * {@code "bullish"} (spot >= 5% below max_pain — pin attracts upside),
     * {@code "bearish"} (>= 5% above), or {@code "neutral"} (within 5%).
     */
    @SerializedName("signal")
    public String signal;

    /** Expiration this view is scoped to. */
    @SerializedName("expiration")
    public String expiration;

    /** Total put OI / total call OI. {@code > 1.0} = put-heavy chain. */
    @SerializedName("put_call_oi_ratio")
    public Double putCallOiRatio;

    /** Strike-by-strike pain curve. Minimum is at {@link #maxPainStrike}. */
    @SerializedName("pain_curve")
    public List<MaxPainCurveRow> painCurve;

    /** Per-strike OI + volume breakdown. Same strike grid as {@link #painCurve}. */
    @SerializedName("oi_by_strike")
    public List<MaxPainOiRow> oiByStrike;

    /** Per-expiry calendar. {@code null} when the request specified an expiry. */
    @SerializedName("max_pain_by_expiration")
    public List<MaxPainByExpirationRow> maxPainByExpiration;

    /** GEX-based dealer alignment overlay. */
    @SerializedName("dealer_alignment")
    public MaxPainDealerAlignment dealerAlignment;

    /**
     * {@code "positive_gamma"} | {@code "negative_gamma"} |
     * {@code "unknown"}.
     */
    @SerializedName("regime")
    public String regime;

    /** Expected move from the ATM straddle, contextualized vs max pain. */
    @SerializedName("expected_move")
    public MaxPainExpectedMove expectedMove;

    /**
     * 0-100 composite — likelihood of pinning to {@link #maxPainStrike}.
     * Most meaningful for near-term expiries.
     */
    @SerializedName("pin_probability")
    public Integer pinProbability;

    /** Distance from spot to the max-pain strike. */
    public static class MaxPainDistance {
        /** Dollar distance: {@code |underlying_price - max_pain_strike|}. */
        @SerializedName("absolute")
        public Double absolute;

        /** Percent of spot: {@code absolute / underlying_price * 100}. */
        @SerializedName("percent")
        public Double percent;

        /** {@code "above"}, {@code "below"}, or {@code "at"} — spot vs max-pain. */
        @SerializedName("direction")
        public String direction;
    }

    /**
     * One row of the strike-by-strike pain curve.
     *
     * <p>Each row is the dollar pain (intrinsic value × OI × 100 contract
     * multiplier) summed across all expirations at that strike. The strike
     * where {@code totalPain} is minimized is the max-pain strike.
     */
    public static class MaxPainCurveRow {
        @SerializedName("strike")
        public Double strike;

        @SerializedName("call_pain")
        public Double callPain;

        @SerializedName("put_pain")
        public Double putPain;

        @SerializedName("total_pain")
        public Double totalPain;
    }

    /**
     * One row of the OI-by-strike breakdown.
     *
     * <p>Note: on the Historical API, {@code callVolume} and {@code putVolume}
     * are always {@code 0} (placeholder fields — the minute table doesn't
     * carry intraday volume).
     */
    public static class MaxPainOiRow {
        @SerializedName("strike")
        public Double strike;

        @SerializedName("call_oi")
        public Integer callOi;

        @SerializedName("put_oi")
        public Integer putOi;

        @SerializedName("total_oi")
        public Integer totalOi;

        @SerializedName("call_volume")
        public Integer callVolume;

        @SerializedName("put_volume")
        public Integer putVolume;
    }

    /** Per-expiry max-pain breakdown when no {@code expiration} filter is applied. */
    public static class MaxPainByExpirationRow {
        @SerializedName("expiration")
        public String expiration;

        @SerializedName("max_pain_strike")
        public Double maxPainStrike;

        /** Days to expiry (counting from {@code asOf}). */
        @SerializedName("dte")
        public Integer dte;

        @SerializedName("total_oi")
        public Integer totalOi;
    }

    /**
     * GEX-based dealer-alignment overlay on the max-pain view.
     *
     * <p>{@link #alignment}: {@code "converging"} | {@code "moderate"} |
     * {@code "diverging"} | {@code "unknown"}.
     */
    public static class MaxPainDealerAlignment {
        @SerializedName("alignment")
        public String alignment;

        /** Plain-English explanation. Safe to surface verbatim. */
        @SerializedName("description")
        public String description;

        /** Strike where net dealer gamma crosses zero. */
        @SerializedName("gamma_flip")
        public Double gammaFlip;

        /** Strike with highest absolute call GEX (dealer-side resistance). */
        @SerializedName("call_wall")
        public Double callWall;

        /** Strike with highest absolute put GEX (dealer-side support). */
        @SerializedName("put_wall")
        public Double putWall;
    }

    /** Implied move from the ATM straddle, contextualized vs max pain. */
    public static class MaxPainExpectedMove {
        /** ATM straddle mid in dollars. Rough proxy for the 1σ implied move. */
        @SerializedName("straddle_price")
        public Double straddlePrice;

        /** ATM implied volatility (annualised %, e.g. 18.5 = 18.5%). */
        @SerializedName("atm_iv")
        public Double atmIv;

        /** {@code true} when {@code |spot - max_pain_strike| <= straddle_price}. */
        @SerializedName("max_pain_within_expected_range")
        public Boolean maxPainWithinExpectedRange;
    }
}
