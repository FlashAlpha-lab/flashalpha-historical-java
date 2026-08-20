package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Typed response model for {@code GET /v1/stock/{symbol}/summary?at=...}.
 *
 * <p>Point-in-time replay of the live stock-summary endpoint — same
 * response shape, but the snapshot is the historical view of price,
 * volatility, options flow, dealer exposure, and macro context as of
 * the {@code at} timestamp. {@link #asOf} is snapped to the nearest
 * available minute in the historical dataset (closest minute back to
 * 2017-01-03; date-only {@code at} values default to 16:00 ET).
 *
 * <p>About FlashAlpha: real-time + historical options dealer-flow
 * analytics. See <a href="https://lab.flashalpha.com">https://lab.flashalpha.com</a>.
 *
 * <p>All numeric fields are boxed wrappers ({@link Double}, {@link Long},
 * {@link Integer}, {@link Boolean}) so {@code null} can represent values
 * the API could not compute.
 */
public final class StockSummaryResponse {

    @SerializedName("symbol")
    public String symbol;

    /** ET timestamp of the snapshot — snapped to the nearest available minute. */
    @SerializedName("as_of")
    public String asOf;

    /** {@code true} if NYSE was open at {@link #asOf}. */
    @SerializedName("market_open")
    public Boolean marketOpen;

    @SerializedName("price")
    public Price price;

    @SerializedName("volatility")
    public Volatility volatility;

    @SerializedName("options_flow")
    public OptionsFlow optionsFlow;

    /** {@code null} when no options/greeks data is loaded for this {@code at}. */
    @SerializedName("exposure")
    public Exposure exposure;

    /** Macro overlay. Sub-fields may be {@code null} when the source was unavailable at {@code at}. */
    @SerializedName("macro")
    public Macro macro;

    public static final class Price {
        @SerializedName("bid") public Double bid;
        @SerializedName("ask") public Double ask;
        @SerializedName("mid") public Double mid;
        @SerializedName("last") public Double last;
        @SerializedName("last_update") public String lastUpdate;
    }

    /**
     * Volatility block. All vol numbers are PERCENTS (e.g. {@code 18.45}
     * = 18.45%). The IV term structure filters out points with IV
     * &lt; 5% or &gt; 200% (bad SVI fits).
     */
    public static final class Volatility {
        /** ATM implied volatility (annualised %). */
        @SerializedName("atm_iv") public Double atmIv;
        /** 20-day realized volatility (annualised %). */
        @SerializedName("hv_20") public Double hv20;
        /** 60-day realized volatility (annualised %). */
        @SerializedName("hv_60") public Double hv60;
        /** Variance risk premium: {@code atm_iv - hv_20} in percentage points. */
        @SerializedName("vrp") public Double vrp;
        @SerializedName("skew_25d") public Skew25d skew25d;
        /** IV term structure rows. Bad fits (IV &lt; 5% or &gt; 200%) are filtered server-side. */
        @SerializedName("iv_term_structure") public List<TermStructureRow> ivTermStructure;
    }

    public static final class Skew25d {
        /** ISO expiration date this skew row was measured at. */
        @SerializedName("expiry") public String expiry;
        /** Days to expiry from {@link StockSummaryResponse#asOf}. */
        @SerializedName("days_to_expiry") public Integer daysToExpiry;
        /** 25-delta put-wing IV (annualised %). */
        @SerializedName("put_25d_iv") public Double put25dIv;
        /** ATM IV at this tenor (annualised %) — the skew anchor. */
        @SerializedName("atm_iv") public Double atmIv;
        /** 25-delta call-wing IV (annualised %). */
        @SerializedName("call_25d_iv") public Double call25dIv;
        /** {@code put_25d_iv - call_25d_iv} — positive = puts richer than calls. */
        @SerializedName("skew_25d") public Double skew25d;
        /** {@code (put_25d_iv + call_25d_iv) / (2 * atm_iv)} — wing-vs-ATM smile shape. */
        @SerializedName("smile_ratio") public Double smileRatio;
    }

    public static final class TermStructureRow {
        @SerializedName("expiry") public String expiry;
        @SerializedName("days_to_expiry") public Integer daysToExpiry;
        @SerializedName("iv") public Double iv;
    }

    public static final class OptionsFlow {
        @SerializedName("total_call_oi") public Long callOi;
        @SerializedName("total_put_oi") public Long putOi;
        @SerializedName("total_call_volume") public Long callVolume;
        @SerializedName("total_put_volume") public Long putVolume;
        @SerializedName("pc_ratio_oi") public Double pcRatioOi;
        @SerializedName("pc_ratio_volume") public Double pcRatioVolume;
        @SerializedName("active_expirations") public Integer activeExpirations;
    }

    /**
     * Dealer-exposure block. {@code null} when no options/greeks data is
     * loaded for this symbol at {@code at}.
     */
    public static final class Exposure {
        @SerializedName("net_gex") public Double netGex;
        @SerializedName("net_dex") public Double netDex;
        @SerializedName("net_vex") public Double netVex;
        @SerializedName("net_chex") public Double netChex;
        @SerializedName("gamma_flip") public Double gammaFlip;
        @SerializedName("call_wall") public Double callWall;
        @SerializedName("put_wall") public Double putWall;
        @SerializedName("max_pain") public Double maxPain;
        @SerializedName("highest_oi_strike") public Double highestOiStrike;

        /** {@code "positive_gamma"} | {@code "negative_gamma"} | {@code "unknown"}. */
        @SerializedName("regime") public String regime;

        @SerializedName("interpretation") public Interpretation interpretation;
        @SerializedName("hedging_estimate") public HedgingEstimate hedgingEstimate;
        @SerializedName("zero_dte") public ZeroDteShare zeroDte;
        @SerializedName("top_strikes") public List<TopStrikeRow> topStrikes;
        @SerializedName("oi_weighted_dte") public Double oiWeightedDte;
    }

    public static final class Interpretation {
        @SerializedName("gamma") public String gamma;
        @SerializedName("vanna") public String vanna;
        @SerializedName("charm") public String charm;
    }

    /**
     * Estimated dealer hedging flow at +/- 1% spot moves.
     *
     * <p><b>Sign convention:</b> {@link HedgingMove#dealerShares} is a
     * MAGNITUDE on this endpoint and the {@link HedgingMove#direction}
     * string carries the sign — different from the zero-DTE endpoint
     * which uses signed values.
     */
    public static final class HedgingEstimate {
        @SerializedName("spot_up_1pct")   public HedgingMove spotUp1Pct;
        @SerializedName("spot_down_1pct") public HedgingMove spotDown1Pct;
    }

    public static final class HedgingMove {
        /** Dealer shares to trade — MAGNITUDE; sign carried by {@link #direction}. */
        @SerializedName("dealer_shares") public Double dealerShares;
        @SerializedName("direction") public String direction;
        @SerializedName("notional_usd") public Double notionalUsd;
    }

    public static final class ZeroDteShare {
        @SerializedName("net_gex") public Double netGex;
        @SerializedName("pct_of_total") public Double pctOfTotal;
        @SerializedName("expiration") public String expiration;
    }

    public static final class TopStrikeRow {
        @SerializedName("strike") public Double strike;
        @SerializedName("net_gex") public Double netGex;
        @SerializedName("call_oi") public Long callOi;
        @SerializedName("put_oi") public Long putOi;
        /** Combined call+put OI at this strike. */
        @SerializedName("total_oi") public Long totalOi;
    }

    public static final class Macro {
        @SerializedName("vix") public Quote vix;
        @SerializedName("vvix") public Quote vvix;
        @SerializedName("skew") public Quote skew;
        @SerializedName("spx") public Quote spx;
        @SerializedName("move") public Quote move;

        @SerializedName("vix_term_structure") public VixTermStructure vixTermStructure;

        /** {@link VixFutures#basis} is approximated from VIX3M vs VIX spot, NOT actual futures prices. */
        @SerializedName("vix_futures") public VixFutures vixFutures;

        @SerializedName("fear_and_greed") public FearAndGreed fearAndGreed;
    }

    public static final class Quote {
        @SerializedName("value") public Double value;
        @SerializedName("change") public Double change;
        @SerializedName("change_pct") public Double changePct;
    }

    public static final class VixTermStructure {
        @SerializedName("levels") public VixTermLevels levels;
        /** Near-tenor slope: {@code (vix3m - vix) / vix * 100}. Positive = contango. */
        @SerializedName("near_slope_pct") public Double nearSlopePct;
        /** {@code "contango"} or {@code "backwardation"}. */
        @SerializedName("structure") public String structure;
    }

    public static final class VixTermLevels {
        /** VIX9D (9-day VIX). */
        @SerializedName("vix9d") public Double vix9d;
        /** VIX (30-day, the standard headline index). */
        @SerializedName("vix")   public Double vix;
        /** VIX3M (3-month VIX). */
        @SerializedName("vix3m") public Double vix3m;
        /** VIX6M (6-month VIX). */
        @SerializedName("vix6m") public Double vix6m;
    }

    public static final class VixFutures {
        /** Front-month VIX futures price (proxied from VIX3M). */
        @SerializedName("front_month") public Double frontMonth;
        /** Spot VIX. */
        @SerializedName("spot") public Double spot;
        /** {@code front_month - spot}. */
        @SerializedName("spread") public Double spread;
        /** Basis as a percent of spot. */
        @SerializedName("basis_pct") public Double basisPct;
        /** {@code "contango"} or {@code "backwardation"}. */
        @SerializedName("basis") public String basis;
    }

    public static final class FearAndGreed {
        /** 0-100 composite (0 = extreme fear, 100 = extreme greed). */
        @SerializedName("score") public Integer score;
        /** Bucket label (e.g. {@code "Extreme Fear"}, {@code "Neutral"}, {@code "Extreme Greed"}). */
        @SerializedName("rating") public String rating;
    }
}
