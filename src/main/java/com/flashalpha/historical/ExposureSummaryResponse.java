package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

/**
 * Typed response model for {@code GET /v1/exposure/summary/{symbol}?at=...}.
 *
 * <p>The Historical API returns the same response shape as the live API; the
 * only difference is every analytics endpoint requires an {@code at} query
 * parameter (string, {@link java.time.LocalDateTime}, or
 * {@link java.time.LocalDate}).
 *
 * <p>All numeric fields are boxed wrappers ({@link Double}) so that
 * {@code null} can represent values the API could not compute (insufficient
 * data, market closed, "backtest_mode" gaps, etc.).
 *
 * <p><b>Direction casing:</b> confirmed via live probe — both
 * {@code /v1/exposure/summary/} and {@code /v1/exposure/zero-dte/} return
 * lowercase {@code "buy"}/{@code "sell"}. Casing is consistent across
 * summary and zero-DTE endpoints.
 */
public final class ExposureSummaryResponse extends FlashAlphaResponse {

    @SerializedName("symbol")
    public String symbol;

    @SerializedName("underlying_price")
    public Double underlyingPrice;

    /** The as-of stamp the API actually used (snapped to the available minute). */
    @SerializedName("as_of")
    public String asOf;

    // Note: as_of_requested exists on /v1/exposure/{gex,dex,narrative} but
    // NOT on /v1/exposure/summary. Don't add it to this class even though it
    // would be defensive — the field genuinely isn't returned for this endpoint.

    @SerializedName("gamma_flip")
    public Double gammaFlip;

    /**
     * One of: positive_gamma | negative_gamma | unknown
     * (`unknown` when no usable options data / no gamma flip).
     */
    @SerializedName("regime")
    public String regime;

    @SerializedName("exposures")
    public Exposures exposures;

    @SerializedName("interpretation")
    public Interpretation interpretation;

    @SerializedName("hedging_estimate")
    public HedgingEstimate hedgingEstimate;

    @SerializedName("zero_dte")
    public ZeroDte zeroDte;

    /** Net exposure totals across the entire chain. */
    public static final class Exposures {
        @SerializedName("net_gex")  public Double netGex;
        @SerializedName("net_dex")  public Double netDex;
        @SerializedName("net_vex")  public Double netVex;
        @SerializedName("net_chex") public Double netChex;
    }

    /** Verbal interpretation of the gamma/vanna/charm regimes. */
    public static final class Interpretation {
        @SerializedName("gamma") public String gamma;
        @SerializedName("vanna") public String vanna;
        @SerializedName("charm") public String charm;
    }

    /** One side (up or down) of a dealer-hedging estimate. */
    public static final class HedgingMove {
        @SerializedName("dealer_shares_to_trade") public Double dealerSharesToTrade;
        /** "buy" or "sell" (lowercase on both this endpoint and zero-dte). */
        @SerializedName("direction")              public String direction;
        @SerializedName("notional_usd")           public Double notionalUsd;
    }

    /** Estimated dealer hedging flow at +/- 1% spot moves. */
    public static final class HedgingEstimate {
        @SerializedName("spot_up_1pct")   public HedgingMove spotUp1Pct;
        @SerializedName("spot_down_1pct") public HedgingMove spotDown1Pct;
    }

    /** Same-day-expiration contribution to total GEX. */
    public static final class ZeroDte {
        @SerializedName("net_gex")          public Double netGex;
        @SerializedName("pct_of_total_gex") public Double pctOfTotalGex;
        @SerializedName("expiration")       public String expiration;
    }
}
