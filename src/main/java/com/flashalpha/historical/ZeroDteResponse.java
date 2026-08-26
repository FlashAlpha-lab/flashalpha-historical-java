package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Typed response model for {@code GET /v1/exposure/zero-dte/{symbol}?at=...}
 * (historical mode, point-in-time replay).
 *
 * <p>Use {@link FlashAlphaHistoricalClient#zeroDteTyped(String, String)} or
 * {@link FlashAlphaHistoricalClient#zeroDteTyped(String, java.time.LocalDateTime)}
 * to obtain a populated instance. The original untyped
 * {@link FlashAlphaHistoricalClient#zeroDte(String, String)} method is
 * unchanged and continues to return {@link com.google.gson.JsonObject}.
 *
 * <p><b>Historical mode notes:</b>
 * <ul>
 *   <li>The {@code at=} parameter is required (ET wall-clock minute string,
 *       e.g. {@code "2024-08-05T15:30:00"}, or a {@link java.time.LocalDateTime}).</li>
 *   <li>{@link #asOf} echoes the requested {@code at} after snapping to the
 *       nearest available minute on the historical grid.</li>
 *   <li>On weekends/holidays or symbols with no 0DTE that day, {@link #noZeroDte}
 *       is {@code true} and most fields are {@code null} — only {@link #symbol},
 *       {@link #asOf}, {@link #message}, and {@link #nextZeroDteExpiry} are
 *       populated.</li>
 *   <li>Some intraday-only fields (e.g. live volume / spreads / liquidity
 *       composites) may be {@code null} on the historical grid where the
 *       upstream snapshot did not capture them.</li>
 * </ul>
 *
 * <p>All numeric fields are boxed wrappers ({@link Double}, {@link Long},
 * {@link Integer}) so that {@code null} can represent values the API could
 * not compute (insufficient data, market closed, etc.).
 *
 * <p><b>About FlashAlpha:</b> real-time options dealer-flow analytics
 * (gamma/delta/vanna/charm exposure, key levels, dealer hedging estimates,
 * 0DTE specifics, max pain, VRP). This endpoint is the same-day-expiration
 * deep dive — the most actionable view for intraday SPX/SPY/QQQ flow
 * traders. See <a href="https://lab.flashalpha.com">https://lab.flashalpha.com</a>
 * and <a href="https://flashalpha.com">https://flashalpha.com</a>.
 */
public final class ZeroDteResponse extends FlashAlphaResponse {

    /** Echoed from the request path (e.g. {@code "SPY"}). */
    @SerializedName("symbol")
    public String symbol;

    /** Spot mid at {@link #asOf}. Used as the reference price for every distance/percent field. */
    @SerializedName("underlying_price")
    public Double underlyingPrice;

    /** ISO date of the 0DTE expiration this snapshot describes (the same-day expiration on {@link #asOf}'s session). {@code null} on the no-0DTE fallback path. */
    @SerializedName("expiration")
    public String expiration;

    /** ET wall-clock timestamp this snapshot was computed for. Echoes the requested {@code at} after grid snapping. Always populated. */
    @SerializedName("as_of")
    public String asOf;

    /** {@code true} if NYSE was open at {@link #asOf}. */
    @SerializedName("market_open")
    public Boolean marketOpen;

    /** Hours remaining until the 4:00pm ET cash-equity close on {@link #asOf}'s session. Used as the denominator in {@link Decay#thetaPerHourRemaining}. */
    @SerializedName("time_to_close_hours")
    public Double timeToCloseHours;

    /** Percent of the regular trading day <b>elapsed</b> at {@link #asOf} (0 = at the open, 100 = at the close). Range 0–100. */
    @SerializedName("time_to_close_pct")
    public Double timeToClosePct;

    /** Dealer gamma regime block — positive vs negative gamma, gamma flip strike, distance to flip. See {@link Regime}. */
    @SerializedName("regime")
    public Regime regime;

    /** Net dealer Greek totals across the 0DTE expiration (GEX/DEX/VEX/CHEX) plus the 0DTE share of the full chain. See {@link Exposures}. */
    @SerializedName("exposures")
    public Exposures exposures;

    /** 1-sigma implied move from ATM IV plus the time-decayed remaining-day move and ATM straddle price. See {@link ExpectedMove}. */
    @SerializedName("expected_move")
    public ExpectedMove expectedMove;

    /** Magnet strike, 0-100 pin-probability composite, and its sub-component scores. See {@link PinRisk}. */
    @SerializedName("pin_risk")
    public PinRisk pinRisk;

    /** Estimated dealer hedging flow at ±10bp / ±25bp / ±0.50% / ±1.00% spot moves, plus convexity at spot. See {@link Hedging}. */
    @SerializedName("hedging")
    public Hedging hedging;

    /** Theta and charm decay metrics, including the {@link Decay#gammaAcceleration} 0DTE-vs-7DTE multiplier. See {@link Decay}. */
    @SerializedName("decay")
    public Decay decay;

    /** Volatility context — 0DTE vs 7DTE ATM IV, VIX, vanna exposure, and term-structure interpretation. See {@link VolContext}. */
    @SerializedName("vol_context")
    public VolContext volContext;

    /** Volume / OI breakdown, put-call ratios, ATM volume share, and the {@code volume_to_oi_ratio} day-trading proxy. See {@link Flow}. Volume-derived fields may be {@code null} in historical mode where the upstream snapshot did not capture intraday volumes. */
    @SerializedName("flow")
    public Flow flow;

    /** Key levels — call/put walls, max-pos/neg gamma, highest-OI strike, and the {@link Levels#levelClusterScore} 0-100 cluster tightness composite. See {@link Levels}. */
    @SerializedName("levels")
    public Levels levels;

    /** ATM and weighted bid-ask spread plus a 0-100 {@link Liquidity#executionScore} (70% spread + 30% ATM OI depth). See {@link Liquidity}. May be {@code null} in historical mode where quote spreads are not available on the grid. */
    @SerializedName("liquidity")
    public Liquidity liquidity;

    /** Snapshot age, contract count, and 0-100 data-quality / greek-smoothness composites. See {@link Metadata}. */
    @SerializedName("metadata")
    public Metadata metadata;

    /** Strike-by-strike rows around spot — per-strike GEX/DEX/VEX/CHEX, OI, volume, IV, and Greeks. Always sorted ascending by {@link Strike#strike}. */
    @SerializedName("strikes")
    public List<Strike> strikes;

    /** Optional — only present near close (&lt;5 min) when greeks may be unstable. */
    @SerializedName("warnings")
    public List<String> warnings;

    // ── No-0DTE fallback ───────────────────────────────────────────────────

    /** {@code true} on weekends / holidays / symbols without a same-day expiration on the requested session. When set, every analytics block above is {@code null}. */
    @SerializedName("no_zero_dte")
    public Boolean noZeroDte;

    /** Human-readable explanation when {@link #noZeroDte} is {@code true}. */
    @SerializedName("message")
    public String message;

    /** ISO date of the next 0DTE expiration (relative to {@link #asOf}) when none on this session, otherwise {@code null}. */
    @SerializedName("next_zero_dte_expiry")
    public String nextZeroDteExpiry;

    // ── Nested types ───────────────────────────────────────────────────────

    /**
     * Dealer gamma regime block — answers "are dealers long or short gamma at this snapshot?".
     *
     * <p>Above the gamma flip dealers are typically long gamma (vol-suppressing,
     * mean-reverting tape). Below the flip they are short gamma (trend-amplifying,
     * vol-expanding tape). The flip strike is the single most-watched intraday
     * pivot in 0DTE flow analysis.
     */
    public static final class Regime {
        /**
         * Dealer gamma regime label — {@code "positive_gamma"} (spot &gt;
         * flip — dealers long gamma, hedge-the-tape, vol-dampening) or
         * {@code "negative_gamma"} (spot &lt; flip — dealers short gamma,
         * hedge-with-the-tape, trend-amplifying).
         */
        @SerializedName("label") public String label;

        /** Plain-English narrative explaining the regime — safe to surface verbatim in UIs. */
        @SerializedName("description") public String description;

        /** Strike where net dealer gamma crosses zero (the regime boundary). */
        @SerializedName("gamma_flip") public Double gammaFlip;

        /** {@code "above"} or {@code "below"} — spot relative to {@link #gammaFlip}. */
        @SerializedName("spot_vs_flip") public String spotVsFlip;

        /** Signed percent: {@code (spot - gamma_flip) / spot * 100}. Positive when above the flip. */
        @SerializedName("spot_to_flip_pct") public Double spotToFlipPct;

        /** Absolute dollar distance: {@code |spot - gamma_flip|}. */
        @SerializedName("distance_to_flip_dollars") public Double distanceToFlipDollars;

        /** Distance to flip expressed in 1-sigma implied-move units. Useful for cross-symbol comparison. */
        @SerializedName("distance_to_flip_sigmas") public Double distanceToFlipSigmas;
    }

    /**
     * Net dealer Greek totals across the 0DTE expiration only.
     *
     * <p>For full-chain totals see {@code /v1/exposure/summary/{symbol}};
     * {@link #pctOfTotalGex} reports what fraction of the full-chain gamma
     * lives at this expiration.
     */
    public static final class Exposures {
        /** Net dealer gamma exposure ($ per 1% spot move) for the 0DTE expiration. Sign indicates the regime. */
        @SerializedName("net_gex") public Double netGex;

        /** Net dealer delta exposure (shares-equivalent) for the 0DTE expiration. */
        @SerializedName("net_dex") public Double netDex;

        /** Net dealer vanna exposure for the 0DTE expiration — sensitivity of dealer delta to a 1% IV change. */
        @SerializedName("net_vex") public Double netVex;

        /** Net dealer charm exposure for the 0DTE expiration — dealer delta drift per day. Dominates near close. */
        @SerializedName("net_chex") public Double netChex;

        /**
         * 0DTE GEX as a fraction of the full-chain net GEX (0-100). Values
         * above ~50 mean 0DTE dominates intraday hedging flow. {@code null}
         * when the chain GEX is zero or unavailable.
         */
        @SerializedName("pct_of_total_gex") public Double pctOfTotalGex;

        /** Full-chain net dealer GEX, included for the {@link #pctOfTotalGex} ratio context. */
        @SerializedName("total_chain_net_gex") public Double totalChainNetGex;
    }

    /**
     * Implied expected-move block — what the options market was pricing on
     * the requested session. Includes both the full-day 1σ move and the
     * time-decayed remaining-day move computed at {@link ZeroDteResponse#asOf}.
     */
    public static final class ExpectedMove {
        /** Full-session 1-sigma implied move in dollars, derived from {@link #atmIv}. */
        @SerializedName("implied_1sd_dollars") public Double implied1SdDollars;

        /** Full-session 1-sigma implied move as percent of spot. */
        @SerializedName("implied_1sd_pct") public Double implied1SdPct;

        /**
         * Time-decayed 1-sigma move covering only the time remaining in the
         * session — scales as {@code sqrt(time_to_close_hours / total_hours)}.
         */
        @SerializedName("remaining_1sd_dollars") public Double remaining1SdDollars;

        /** {@link #remaining1SdDollars} expressed as a percent of spot. */
        @SerializedName("remaining_1sd_pct") public Double remaining1SdPct;

        /** Spot + {@link #remaining1SdDollars} — upper edge of the remaining-day 1σ envelope. */
        @SerializedName("upper_bound") public Double upperBound;

        /** Spot − {@link #remaining1SdDollars} — lower edge of the remaining-day 1σ envelope. */
        @SerializedName("lower_bound") public Double lowerBound;

        /** ATM straddle mid (call_mid + put_mid). The market's direct, model-free expected-move quote. */
        @SerializedName("straddle_price") public Double straddlePrice;

        /** ATM implied volatility (annualised %, e.g. {@code 18.5} = 18.5%). */
        @SerializedName("atm_iv") public Double atmIv;
    }

    /**
     * Component sub-scores that feed into the composite {@link PinRisk#pinScore}.
     * Each is 0-100; the composite is a weighted blend.
     */
    public static final class PinComponents {
        /** OI concentration score (0-100). 30% weight in the composite. */
        @SerializedName("oi_score") public Integer oiScore;

        /** Magnet proximity score (0-100) — closer to magnet = higher. 25% weight. */
        @SerializedName("proximity_score") public Integer proximityScore;

        /** Time-remaining score (0-100) — less time = higher pin probability. 25% weight. */
        @SerializedName("time_score") public Integer timeScore;

        /** Gamma concentration score (0-100) — higher absolute gamma at the magnet = stronger pull. 20% weight. */
        @SerializedName("gamma_score") public Integer gammaScore;
    }

    /**
     * Pin risk block — measures the gravitational pull of the dominant
     * gamma/OI strike (the "magnet") on closing-price action.
     */
    public static final class PinRisk {
        /** Strike with the highest absolute net dealer gamma. */
        @SerializedName("magnet_strike") public Double magnetStrike;

        /** Net GEX at the magnet strike — magnitude of the pull. */
        @SerializedName("magnet_gex") public Double magnetGex;

        /** Signed distance from spot to magnet as percent of spot. Negative = magnet below spot. */
        @SerializedName("distance_to_magnet_pct") public Double distanceToMagnetPct;

        /**
         * 0-100 composite probability that price pins to {@link #magnetStrike}
         * at the close. Inputs (see {@link PinComponents}): OI concentration
         * (30%), magnet proximity (25%), time remaining (25%), gamma magnitude
         * (20%).
         */
        @SerializedName("pin_score") public Integer pinScore;

        /** Sub-scores that feed {@link #pinScore}. */
        @SerializedName("components") public PinComponents components;

        /** Max-pain strike for this expiry. */
        @SerializedName("max_pain") public Double maxPain;

        /** Share of total OI concentrated in the top-3 strikes (0-100). */
        @SerializedName("oi_concentration_top3_pct") public Double oiConcentrationTop3Pct;

        /** Plain-English explanation of the pin-risk picture — safe to surface verbatim. */
        @SerializedName("description") public String description;
    }

    /**
     * One side of an estimated dealer-hedging-flow bucket — how many shares
     * of the underlying dealers must trade and in which direction to remain
     * delta-neutral if spot moves by the bucket's amount.
     */
    public static final class HedgingBucket {
        /** Estimated absolute share count dealers must trade to re-hedge. */
        @SerializedName("dealer_shares_to_trade") public Double dealerSharesToTrade;

        /** {@code "buy"} or {@code "sell"} — direction of the dealer flow under this scenario. */
        @SerializedName("direction") public String direction;

        /** Estimated dollar notional of the dealer trade (shares × spot). */
        @SerializedName("notional_usd") public Double notionalUsd;
    }

    /**
     * Estimated dealer hedging flow at multiple spot-shift sizes. The 10/25bp
     * buckets are the most relevant intraday; the 1% bucket sets the upper
     * envelope. Compare adjacent buckets to read second-derivative effects
     * (gamma/convexity) — see {@link #convexityAtSpot}.
     */
    public static final class Hedging {
        /** Dealer hedging flow if spot rises by 0.10% (10 basis points). */
        @SerializedName("spot_up_10bp") public HedgingBucket spotUp10Bp;

        /** Dealer hedging flow if spot falls by 0.10% (10 basis points). */
        @SerializedName("spot_down_10bp") public HedgingBucket spotDown10Bp;

        /** Dealer hedging flow if spot rises by 0.25%. */
        @SerializedName("spot_up_25bp") public HedgingBucket spotUp25Bp;

        /** Dealer hedging flow if spot falls by 0.25%. */
        @SerializedName("spot_down_25bp") public HedgingBucket spotDown25Bp;

        /** Dealer hedging flow if spot rises by 0.50%. */
        @SerializedName("spot_up_half_pct") public HedgingBucket spotUpHalfPct;

        /** Dealer hedging flow if spot falls by 0.50%. */
        @SerializedName("spot_down_half_pct") public HedgingBucket spotDownHalfPct;

        /** Dealer hedging flow if spot rises by 1.00%. */
        @SerializedName("spot_up_1pct") public HedgingBucket spotUp1Pct;

        /** Dealer hedging flow if spot falls by 1.00%. */
        @SerializedName("spot_down_1pct") public HedgingBucket spotDown1Pct;

        /**
         * Local 2nd finite-difference of net GEX across the three nearest-spot
         * strikes — measures how steeply dealer hedging pressure changes
         * around the current price.
         */
        @SerializedName("convexity_at_spot") public Double convexityAtSpot;
    }

    /**
     * Theta and charm decay characteristics specific to 0DTE — both
     * accelerate dramatically intraday and dominate the closing tape.
     */
    public static final class Decay {
        /** Net dealer theta in dollars (typically negative — dealers usually short premium). Whole-day total. */
        @SerializedName("net_theta_dollars") public Double netThetaDollars;

        /**
         * Theta burn rate per remaining hour: {@code net_theta_dollars / time_to_close_hours}.
         */
        @SerializedName("theta_per_hour_remaining") public Double thetaPerHourRemaining;

        /** Charm regime label — qualitative classifier of the dealer charm flow. */
        @SerializedName("charm_regime") public String charmRegime;

        /** Plain-English explanation of the charm regime. Safe to surface verbatim. */
        @SerializedName("charm_description") public String charmDescription;

        /**
         * Ratio of 0DTE ATM gamma to 7DTE ATM gamma — how much "hotter"
         * 0DTE dealer hedging is vs the next weekly. Typically 2-5x at
         * mid-session, often 10x+ in the final 30 minutes as 0DTE gamma
         * spikes toward expiration.
         */
        @SerializedName("gamma_acceleration") public Double gammaAcceleration;

        /** Plain-English summary of the decay block. Safe to surface verbatim. */
        @SerializedName("description") public String description;
    }

    /**
     * Volatility context — places 0DTE IV in the broader term-structure
     * picture and overlays vanna exposure (sensitivity of dealer delta to
     * IV moves).
     */
    public static final class VolContext {
        /** ATM IV at the 0DTE expiration (annualised %). */
        @SerializedName("zero_dte_atm_iv") public Double zeroDteAtmIv;

        /** ATM IV at the 7DTE expiration (annualised %) — term-structure anchor. */
        @SerializedName("seven_dte_atm_iv") public Double sevenDteAtmIv;

        /**
         * {@code zero_dte_atm_iv / seven_dte_atm_iv}.
         * <ul>
         *   <li>&lt;1.0 = 0DTE cheap relative to term structure (typical, mean-reverting tape).</li>
         *   <li>&gt;1.0 = 0DTE rich, often signalling event/catalyst premium.</li>
         * </ul>
         */
        @SerializedName("iv_ratio_0dte_7dte") public Double ivRatio0Dte7Dte;

        /** CBOE VIX index level at {@link ZeroDteResponse#asOf}. */
        @SerializedName("vix") public Double vix;

        /** Net dealer vanna exposure for the 0DTE expiration — same number as {@link Exposures#netVex}, repeated here for context. */
        @SerializedName("vanna_exposure") public Double vannaExposure;

        /** Plain-English vanna interpretation. */
        @SerializedName("vanna_interpretation") public String vannaInterpretation;

        /** Plain-English summary of the vol-context block. Safe to surface verbatim. */
        @SerializedName("description") public String description;
    }

    /**
     * Volume / open-interest breakdown for the 0DTE expiration. Useful for
     * spotting day-trading dominance ({@link #volumeToOiRatio}) and
     * concentration in a few strikes. Volume-derived fields may be {@code null}
     * in historical mode where the upstream snapshot did not capture intraday volumes.
     */
    public static final class Flow {
        /** Total 0DTE option contract volume (calls + puts). */
        @SerializedName("total_volume") public Long totalVolume;

        /** 0DTE call volume. */
        @SerializedName("call_volume") public Long callVolume;

        /** 0DTE put volume. */
        @SerializedName("put_volume") public Long putVolume;

        /** {@code call_volume - put_volume}. Sign and magnitude indicate directional pressure. */
        @SerializedName("net_call_minus_put_volume") public Long netCallMinusPutVolume;

        /** Total 0DTE open interest at session start. */
        @SerializedName("total_oi") public Long totalOi;

        /** 0DTE call open interest. */
        @SerializedName("call_oi") public Long callOi;

        /** 0DTE put open interest. */
        @SerializedName("put_oi") public Long putOi;

        /** Put-call ratio by volume: {@code put_volume / call_volume}. */
        @SerializedName("pc_ratio_volume") public Double pcRatioVolume;

        /** Put-call ratio by open interest: {@code put_oi / call_oi}. */
        @SerializedName("pc_ratio_oi") public Double pcRatioOi;

        /**
         * {@code total_volume / total_oi}. &gt;1 indicates heavy day-trading.
         */
        @SerializedName("volume_to_oi_ratio") public Double volumeToOiRatio;

        /** Share of total volume concentrated within ±0.5% of spot (0-100). */
        @SerializedName("atm_volume_share_pct") public Double atmVolumeSharePct;

        /** Share of total volume concentrated in the top-3 strikes (0-100). */
        @SerializedName("top3_strike_volume_pct") public Double top3StrikeVolumePct;
    }

    /**
     * Key-level reference block — call/put walls, gamma extremes, OI
     * landmarks, and the {@link #levelClusterScore} composite that
     * measures how tightly these levels cluster.
     */
    public static final class Levels {
        /** Strike with the highest absolute call GEX — dealer-side resistance ("call wall"). */
        @SerializedName("call_wall") public Double callWall;

        /** Net GEX at the call wall. */
        @SerializedName("call_wall_gex") public Double callWallGex;

        /** Call wall GEX as a fraction (0-1) of total positive (call-side) GEX. */
        @SerializedName("call_wall_strength") public Double callWallStrength;

        /** Signed distance from spot to {@link #callWall} as percent of spot. Positive = wall above spot. */
        @SerializedName("distance_to_call_wall_pct") public Double distanceToCallWallPct;

        /** Strike with the highest absolute put GEX — dealer-side support ("put wall"). */
        @SerializedName("put_wall") public Double putWall;

        /** Net GEX at the put wall. */
        @SerializedName("put_wall_gex") public Double putWallGex;

        /** Put wall GEX as a fraction (0-1) of total negative (put-side) GEX. */
        @SerializedName("put_wall_strength") public Double putWallStrength;

        /** Signed distance from spot to {@link #putWall} as percent of spot. Negative = wall below spot. */
        @SerializedName("distance_to_put_wall_pct") public Double distanceToPutWallPct;

        /** Absolute dollar distance from spot to {@link PinRisk#magnetStrike}. */
        @SerializedName("distance_to_magnet_dollars") public Double distanceToMagnetDollars;

        /** Strike with the highest combined call+put open interest. */
        @SerializedName("highest_oi_strike") public Double highestOiStrike;

        /** Total OI at {@link #highestOiStrike}. */
        @SerializedName("highest_oi_total") public Long highestOiTotal;

        /** Strike with the highest net positive gamma. */
        @SerializedName("max_positive_gamma") public Double maxPositiveGamma;

        /** Strike with the most negative net gamma. */
        @SerializedName("max_negative_gamma") public Double maxNegativeGamma;

        /**
         * 0-100 composite measuring how tightly the key levels cluster
         * relative to the 1σ implied move. Inputs: gamma flip, magnet,
         * max pain, highest-OI strike, and the call/put walls.
         */
        @SerializedName("level_cluster_score") public Integer levelClusterScore;
    }

    /**
     * Liquidity / execution-quality block — combines bid-ask spread with
     * ATM OI depth into a single tradeability score. May be {@code null}
     * in historical mode where quote spreads are not available on the grid.
     */
    public static final class Liquidity {
        /** ATM bid-ask spread as percent of mid (e.g. {@code 0.5} = 50bp). */
        @SerializedName("atm_spread_pct") public Double atmSpreadPct;

        /** Volume-weighted spread across the strikes block (percent of mid). */
        @SerializedName("weighted_spread_pct") public Double weightedSpreadPct;

        /**
         * 0-100 execution-quality composite — 70% spread, 30% ATM OI depth heuristic.
         */
        @SerializedName("execution_score") public Integer executionScore;
    }

    /**
     * Snapshot quality and freshness metadata. Use these for staleness
     * checks and to gate downstream analytics on data quality.
     */
    public static final class Metadata {
        /** Seconds elapsed between the upstream chain snapshot and {@link ZeroDteResponse#asOf}. */
        @SerializedName("snapshot_age_seconds") public Double snapshotAgeSeconds;

        /** Number of option contracts in the 0DTE chain that contributed to this snapshot. */
        @SerializedName("chain_contract_count") public Integer chainContractCount;

        /** 0-100 overall data-quality composite. */
        @SerializedName("data_quality_score") public Integer dataQualityScore;

        /**
         * 0-100 score derived from the mean absolute consecutive-strike IV
         * difference, mapped onto 0-100 (high = smooth surface).
         */
        @SerializedName("greek_smoothness_score") public Integer greekSmoothnessScore;
    }

    /**
     * One row of the strike-by-strike breakdown — per-strike GEX, DEX,
     * VEX, CHEX, OI, volume, IV, mid, spread and option Greeks for both
     * call and put legs at that strike.
     */
    public static final class Strike {
        /** Strike price. */
        @SerializedName("strike") public Double strike;

        /** Signed percent distance from spot: {@code (strike - spot) / spot * 100}. */
        @SerializedName("distance_from_spot_pct") public Double distanceFromSpotPct;

        /** OCC option symbol for the call leg. */
        @SerializedName("call_symbol") public String callSymbol;

        /** OCC option symbol for the put leg. */
        @SerializedName("put_symbol") public String putSymbol;

        /** Dealer gamma exposure attributable to the call leg at this strike. */
        @SerializedName("call_gex") public Double callGex;

        /** Dealer gamma exposure attributable to the put leg at this strike. */
        @SerializedName("put_gex") public Double putGex;

        /** Net dealer gamma exposure at this strike ({@code call_gex + put_gex}). */
        @SerializedName("net_gex") public Double netGex;

        /** Dealer delta exposure attributable to the call leg. */
        @SerializedName("call_dex") public Double callDex;

        /** Dealer delta exposure attributable to the put leg. */
        @SerializedName("put_dex") public Double putDex;

        /** Net dealer delta exposure at this strike. */
        @SerializedName("net_dex") public Double netDex;

        /** Net dealer vanna exposure at this strike. */
        @SerializedName("net_vex") public Double netVex;

        /** Net dealer charm exposure at this strike. */
        @SerializedName("net_chex") public Double netChex;

        /** Call open interest at this strike. */
        @SerializedName("call_oi") public Long callOi;

        /** Put open interest at this strike. */
        @SerializedName("put_oi") public Long putOi;

        /** Call volume at this strike (session). */
        @SerializedName("call_volume") public Long callVolume;

        /** Put volume at this strike (session). */
        @SerializedName("put_volume") public Long putVolume;

        /** This strike's share of total chain GEX (0-100). */
        @SerializedName("gex_share_pct") public Double gexSharePct;

        /** This strike's share of total chain OI (0-100). */
        @SerializedName("oi_share_pct") public Double oiSharePct;

        /** This strike's share of total chain volume (0-100). */
        @SerializedName("volume_share_pct") public Double volumeSharePct;

        /** Call-leg implied volatility (annualised %). */
        @SerializedName("call_iv") public Double callIv;

        /** Put-leg implied volatility (annualised %). */
        @SerializedName("put_iv") public Double putIv;

        /** Call-leg delta. */
        @SerializedName("call_delta") public Double callDelta;

        /** Put-leg delta. */
        @SerializedName("put_delta") public Double putDelta;

        /** Call-leg gamma. */
        @SerializedName("call_gamma") public Double callGamma;

        /** Put-leg gamma. */
        @SerializedName("put_gamma") public Double putGamma;

        /** Call-leg theta (per day, dollars). */
        @SerializedName("call_theta") public Double callTheta;

        /** Put-leg theta (per day, dollars). */
        @SerializedName("put_theta") public Double putTheta;

        /** Call-leg bid-ask mid price. */
        @SerializedName("call_mid") public Double callMid;

        /** Put-leg bid-ask mid price. */
        @SerializedName("put_mid") public Double putMid;

        /** Call-leg bid-ask spread as percent of mid. */
        @SerializedName("call_spread_pct") public Double callSpreadPct;

        /** Put-leg bid-ask spread as percent of mid. */
        @SerializedName("put_spread_pct") public Double putSpreadPct;
    }
}
