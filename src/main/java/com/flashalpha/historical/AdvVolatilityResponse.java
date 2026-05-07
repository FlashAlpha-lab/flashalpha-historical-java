package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Typed response model for {@code GET /v1/adv_volatility/{symbol}?at=...} (Alpha+).
 *
 * <p>Advanced volatility analytics: per-expiry SVI parameters, forward
 * prices, the full total-variance surface, calendar / butterfly
 * arbitrage flags, variance-swap fair values, and second / third order
 * greeks surfaces (vanna, charm, volga, speed) on a strike-by-expiry
 * grid.
 */
public final class AdvVolatilityResponse {

    @SerializedName("symbol")
    public String symbol;

    @SerializedName("underlying_price")
    public Double underlyingPrice;

    @SerializedName("as_of")
    public String asOf;

    @SerializedName("market_open")
    public Boolean marketOpen;

    /** Per-expiry SVI fit parameters and ATM stats. */
    @SerializedName("svi_parameters")
    public List<SviParameters> sviParameters;

    /** Per-expiry forward prices implied from put-call parity. */
    @SerializedName("forward_prices")
    public List<ForwardPrice> forwardPrices;

    /**
     * Full total-variance surface (strike × expiry).
     *
     * <p>{@code totalVariance} and {@code impliedVol} are 2-D arrays
     * indexed {@code [strikeIdx][expiryIdx]} (rows track
     * {@link TotalVarianceSurface#moneyness} and columns track
     * {@link TotalVarianceSurface#expiries} / {@link TotalVarianceSurface#tenors}).
     */
    @SerializedName("total_variance_surface")
    public TotalVarianceSurface totalVarianceSurface;

    /** Calendar / butterfly arbitrage violations the fitter could not eliminate. */
    @SerializedName("arbitrage_flags")
    public List<ArbitrageFlag> arbitrageFlags;

    /** Per-expiry variance-swap fair values. */
    @SerializedName("variance_swap_fair_values")
    public List<VarianceSwapFairValue> varianceSwapFairValues;

    /** Second / third order greeks evaluated on a strike-by-expiry grid. */
    @SerializedName("greeks_surfaces")
    public GreeksSurfaces greeksSurfaces;

    /**
     * SVI (Stochastic Volatility Inspired) parameters for one expiry.
     * The classic Gatheral parameterisation: total variance
     * {@code w(k) = a + b * (rho * (k - m) + sqrt((k - m)^2 + sigma^2))}.
     */
    public static final class SviParameters {
        @SerializedName("expiry") public String expiry;
        @SerializedName("days_to_expiry") public Integer daysToExpiry;
        /** Forward used to define log-moneyness {@code k = ln(K / F)}. */
        @SerializedName("forward") public Double forward;
        @SerializedName("a") public Double a;
        @SerializedName("b") public Double b;
        @SerializedName("rho") public Double rho;
        @SerializedName("m") public Double m;
        @SerializedName("sigma") public Double sigma;
        /** Total variance at the money: {@code atm_iv^2 * T}. */
        @SerializedName("atm_total_variance") public Double atmTotalVariance;
        /** ATM implied volatility (annualised %). */
        @SerializedName("atm_iv") public Double atmIv;
    }

    /** Forward price (and basis vs spot) for one expiry. */
    public static final class ForwardPrice {
        @SerializedName("expiry") public String expiry;
        @SerializedName("days_to_expiry") public Integer daysToExpiry;
        @SerializedName("forward") public Double forward;
        @SerializedName("spot") public Double spot;
        /** {@code (forward - spot) / spot * 100}. */
        @SerializedName("basis_pct") public Double basisPct;
    }

    /**
     * Full total-variance surface — the headline grid.
     *
     * <p>{@link #moneyness} indexes the rows, and either {@link #expiries}
     * or {@link #tenors} indexes the columns of {@link #totalVariance} and
     * {@link #impliedVol}.
     */
    public static final class TotalVarianceSurface {
        /** Log-moneyness or {@code K/F} grid (row axis). */
        @SerializedName("moneyness") public List<Double> moneyness;
        /** Expiry strings in order (column axis). */
        @SerializedName("expiries") public List<String> expiries;
        /** Tenor in years for each expiry (column axis). */
        @SerializedName("tenors") public List<Double> tenors;
        /** Total variance {@code w = sigma^2 * T} at each grid point. */
        @SerializedName("total_variance") public double[][] totalVariance;
        /** Implied volatility (annualised %) at each grid point. */
        @SerializedName("implied_vol") public double[][] impliedVol;
    }

    /** A calendar or butterfly arbitrage violation flagged by the fitter. */
    public static final class ArbitrageFlag {
        @SerializedName("expiry") public String expiry;
        /** {@code "calendar"} or {@code "butterfly"}. */
        @SerializedName("type") public String type;
        /** Strike or log-moneyness {@code k} where the violation was detected. */
        @SerializedName("strike_or_k") public Double strikeOrK;
        @SerializedName("description") public String description;
    }

    /** Variance-swap fair values for one expiry. */
    public static final class VarianceSwapFairValue {
        @SerializedName("expiry") public String expiry;
        @SerializedName("days_to_expiry") public Integer daysToExpiry;
        @SerializedName("fair_variance") public Double fairVariance;
        /** {@code sqrt(fair_variance)} — fair variance-swap vol. */
        @SerializedName("fair_vol") public Double fairVol;
        @SerializedName("atm_iv") public Double atmIv;
        /** {@code fair_vol - atm_iv} — the convexity premium. */
        @SerializedName("convexity_adjustment") public Double convexityAdjustment;
    }

    /**
     * Second / third order greeks surfaces. Each block is a strike ×
     * expiry grid where {@code values[i][j]} corresponds to
     * {@code strikes[i]} and {@code expiries[j]}.
     */
    public static final class GreeksSurfaces {
        @SerializedName("vanna") public GreekSurface vanna;
        @SerializedName("charm") public GreekSurface charm;
        @SerializedName("volga") public GreekSurface volga;
        @SerializedName("speed") public GreekSurface speed;
    }

    /** A single greek surface on a strike-by-expiry grid. */
    public static final class GreekSurface {
        @SerializedName("strikes") public List<Double> strikes;
        @SerializedName("expiries") public List<String> expiries;
        /** Greek values, indexed {@code [strikeIdx][expiryIdx]}. */
        @SerializedName("values") public double[][] values;
    }
}
