package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Typed response model for {@code GET /v1/vrp/{symbol}?at=...} (Alpha+).
 *
 * <p>Same shape as the live API with two macro diffs:
 * <ul>
 *   <li>{@code macro.hySpread} is populated here; live currently returns {@code null}.</li>
 *   <li>{@code macro.fedFunds} is absent here; live includes it (so this class
 *     omits the field).</li>
 * </ul>
 *
 * <p><b>Common silent-null traps</b> (now type-checked at the SDK boundary):
 * <ul>
 *   <li>{@code response.zScore} ✗ → use {@code response.vrp.zScore}</li>
 *   <li>{@code response.percentile} ✗ → use {@code response.vrp.percentile}</li>
 *   <li>{@code response.putVrp} ✗ → use {@code response.directional.downsideVrp}</li>
 *   <li>{@code response.netGex} ✗ → use {@code response.regime.netGex}</li>
 * </ul>
 *
 * <p>On historical responses with insufficient warm-up ({@code at} near
 * 2018-04-16), {@code vrp.zScore}, {@code vrp.percentile},
 * {@code regime.vrpRegime}, {@code strategyScores}, and
 * {@code netHarvestScore} are all {@code null}. {@code warnings} will explain.
 *
 * <p>Returns 403 {@code tier_restricted} for anything below Alpha plan.
 */
public final class VrpResponse {

    @SerializedName("symbol")
    public String symbol;

    @SerializedName("underlying_price")
    public Double underlyingPrice;

    @SerializedName("as_of")
    public String asOf;

    @SerializedName("market_open")
    public Boolean marketOpen;

    /** Core VRP metrics block. See {@link VrpCore}. */
    @SerializedName("vrp")
    public VrpCore vrp;

    @SerializedName("variance_risk_premium")
    public Double varianceRiskPremium;

    @SerializedName("convexity_premium")
    public Double convexityPremium;

    @SerializedName("fair_vol")
    public Double fairVol;

    @SerializedName("directional")
    public VrpDirectional directional;

    @SerializedName("term_vrp")
    public List<VrpTermItem> termVrp;

    @SerializedName("gex_conditioned")
    public VrpGexConditioned gexConditioned;

    @SerializedName("vanna_conditioned")
    public VrpVannaConditioned vannaConditioned;

    /** Regime snapshot. {@code netGex} lives HERE, not at the top level. */
    @SerializedName("regime")
    public VrpRegime regime;

    /** {@code null} on early historical timestamps with insufficient warmup. */
    @SerializedName("strategy_scores")
    public VrpStrategyScores strategyScores;

    /** {@code null} on early historical timestamps with insufficient warmup. */
    @SerializedName("net_harvest_score")
    public Integer netHarvestScore;

    @SerializedName("dealer_flow_risk")
    public Integer dealerFlowRisk;

    /** Server-side warnings about data quality. Always present (possibly empty). */
    @SerializedName("warnings")
    public List<String> warnings;

    @SerializedName("macro")
    public VrpMacro macro;

    /** Core VRP metrics block — heart of the response. Nested under {@code response.vrp}. */
    public static final class VrpCore {
        @SerializedName("atm_iv") public Double atmIv;
        @SerializedName("rv_5d") public Double rv5d;
        @SerializedName("rv_10d") public Double rv10d;
        @SerializedName("rv_20d") public Double rv20d;
        @SerializedName("rv_30d") public Double rv30d;
        @SerializedName("vrp_5d") public Double vrp5d;
        @SerializedName("vrp_10d") public Double vrp10d;
        @SerializedName("vrp_20d") public Double vrp20d;
        @SerializedName("vrp_30d") public Double vrp30d;
        /** {@code null} when warmup is insufficient (close to 2018-04-16). */
        @SerializedName("z_score") public Double zScore;
        /** {@code null} when warmup is short. */
        @SerializedName("percentile") public Integer percentile;
        @SerializedName("history_days") public Integer historyDays;
    }

    /** Directional VRP skew. Use {@code downsideVrp}/{@code upsideVrp}, NOT {@code putVrp}/{@code callVrp}. */
    public static final class VrpDirectional {
        @SerializedName("put_wing_iv_25d") public Double putWingIv25d;
        @SerializedName("call_wing_iv_25d") public Double callWingIv25d;
        @SerializedName("downside_rv_20d") public Double downsideRv20d;
        @SerializedName("upside_rv_20d") public Double upsideRv20d;
        @SerializedName("downside_vrp") public Double downsideVrp;
        @SerializedName("upside_vrp") public Double upsideVrp;
    }

    /** One row of the VRP term structure. */
    public static final class VrpTermItem {
        @SerializedName("dte") public Integer dte;
        @SerializedName("iv") public Double iv;
        @SerializedName("rv") public Double rv;
        @SerializedName("vrp") public Double vrp;
    }

    /** VRP harvest score conditioned on the prevailing dealer-gamma regime. */
    public static final class VrpGexConditioned {
        @SerializedName("regime") public String regime;
        /** 0-100 composite. >70 = strong harvest signal. */
        @SerializedName("harvest_score") public Double harvestScore;
        @SerializedName("interpretation") public String interpretation;
    }

    /** VRP outlook conditioned on net dealer vanna exposure. */
    public static final class VrpVannaConditioned {
        @SerializedName("outlook") public String outlook;
        @SerializedName("interpretation") public String interpretation;
    }

    /** Regime snapshot. {@code netGex} lives HERE, not top-level. */
    public static final class VrpRegime {
        @SerializedName("gamma") public String gamma;
        /** {@code null} on historical with insufficient warmup. */
        @SerializedName("vrp_regime") public String vrpRegime;
        @SerializedName("net_gex") public Double netGex;
        @SerializedName("gamma_flip") public Double gammaFlip;
    }

    /** 0-100 strategy suitability scores. Any field can be {@code null} on historical. */
    public static final class VrpStrategyScores {
        @SerializedName("short_put_spread") public Integer shortPutSpread;
        @SerializedName("short_strangle") public Integer shortStrangle;
        @SerializedName("iron_condor") public Integer ironCondor;
        @SerializedName("calendar_spread") public Integer calendarSpread;
    }

    /**
     * Macro-context snapshot used to condition the VRP outlook.
     *
     * <p>Historical-specific: {@code fedFunds} is absent (live-only).
     * {@code hySpread} is populated here (live returns null currently).
     */
    public static final class VrpMacro {
        @SerializedName("vix") public Double vix;
        @SerializedName("vix_3m") public Double vix3m;
        @SerializedName("vix_term_slope") public Double vixTermSlope;
        @SerializedName("dgs10") public Double dgs10;
        @SerializedName("hy_spread") public Double hySpread;
    }
}
