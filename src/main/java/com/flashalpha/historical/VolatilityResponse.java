package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Typed response model for {@code GET /v1/volatility/{symbol}?at=...} (Growth+).
 *
 * <p>Comprehensive volatility analytics: realized vol ladder, ATM IV,
 * IV-RV spreads (variance risk premium), per-expiry skew profiles, term
 * structure, GEX / theta by DTE buckets, put-call profile, OI
 * concentration, hedging scenarios, and quote liquidity.
 *
 * <p>All numeric fields are boxed wrappers ({@link Double},
 * {@link Integer}) so {@code null} can represent values the API could not
 * compute (sparse history, bad chain, fitter failure, etc.).
 */
public final class VolatilityResponse {

    @SerializedName("symbol")
    public String symbol;

    @SerializedName("underlying_price")
    public Double underlyingPrice;

    @SerializedName("as_of")
    public String asOf;

    @SerializedName("market_open")
    public Boolean marketOpen;

    /** Realized vol ladder (5/10/20/30/60-day, annualised %). */
    @SerializedName("realized_vol")
    public RealizedVol realizedVol;

    /** At-the-money implied volatility (annualised %). */
    @SerializedName("atm_iv")
    public Double atmIv;

    /** IV minus RV at each tenor, plus a regime assessment string. */
    @SerializedName("iv_rv_spreads")
    public IvRvSpreads ivRvSpreads;

    /** Per-expiry skew profile rows. */
    @SerializedName("skew_profiles")
    public List<SkewProfile> skewProfiles;

    /** Term-structure slopes and state classification. */
    @SerializedName("term_structure")
    public TermStructure termStructure;

    /** IV dispersion — cross-expiry and cross-strike variance. */
    @SerializedName("iv_dispersion")
    public IvDispersion ivDispersion;

    /** Net dealer GEX bucketed by DTE band. */
    @SerializedName("gex_by_dte")
    public List<GexByDteRow> gexByDte;

    /** Net dealer theta bucketed by DTE band. */
    @SerializedName("theta_by_dte")
    public List<ThetaByDteRow> thetaByDte;

    /** Put / call breakdown by expiry and by moneyness. */
    @SerializedName("put_call_profile")
    public PutCallProfile putCallProfile;

    /** OI concentration by top-N strikes (and Herfindahl index). */
    @SerializedName("oi_concentration")
    public OiConcentration oiConcentration;

    /** Dealer hedging scenarios for canonical spot moves. */
    @SerializedName("hedging_scenarios")
    public List<HedgingScenario> hedgingScenarios;

    /** Quote liquidity / spread metrics for ATM and wing strikes. */
    @SerializedName("liquidity")
    public Liquidity liquidity;

    /** Realized vol over rolling windows (annualised %). */
    public static final class RealizedVol {
        @SerializedName("rv_5d")  public Double rv5d;
        @SerializedName("rv_10d") public Double rv10d;
        @SerializedName("rv_20d") public Double rv20d;
        @SerializedName("rv_30d") public Double rv30d;
        @SerializedName("rv_60d") public Double rv60d;
    }

    /**
     * IV minus RV at each tenor — the variance risk premium ladder. The
     * {@code assessment} string is a regime label
     * (e.g. {@code "implied_above_realized"}).
     */
    public static final class IvRvSpreads {
        @SerializedName("vrp_5d")  public Double vrp5d;
        @SerializedName("vrp_10d") public Double vrp10d;
        @SerializedName("vrp_20d") public Double vrp20d;
        @SerializedName("vrp_30d") public Double vrp30d;
        /** Regime label, e.g. {@code "implied_above_realized"}. */
        @SerializedName("assessment") public String assessment;
    }

    /** Skew profile for a single expiry. */
    public static final class SkewProfile {
        @SerializedName("expiry") public String expiry;
        @SerializedName("days_to_expiry") public Integer daysToExpiry;
        @SerializedName("put_10d_iv") public Double put10dIv;
        @SerializedName("put_25d_iv") public Double put25dIv;
        @SerializedName("atm_iv") public Double atmIv;
        @SerializedName("call_25d_iv") public Double call25dIv;
        @SerializedName("call_10d_iv") public Double call10dIv;
        /** {@code put_25d_iv - call_25d_iv}. Positive = downside skew. */
        @SerializedName("skew_25d") public Double skew25d;
        @SerializedName("smile_ratio") public Double smileRatio;
        @SerializedName("tail_convexity") public Double tailConvexity;
    }

    /** Term-structure slopes (near and far) and classification. */
    public static final class TermStructure {
        @SerializedName("near_slope_pct") public Double nearSlopePct;
        @SerializedName("far_slope_pct") public Double farSlopePct;
        /** {@code "contango"} | {@code "backwardation"} | {@code "flat"}. */
        @SerializedName("state") public String state;
    }

    /** Cross-expiry and cross-strike IV dispersion. */
    public static final class IvDispersion {
        @SerializedName("cross_expiry") public Double crossExpiry;
        @SerializedName("cross_strike") public Double crossStrike;
    }

    /** One DTE bucket of net dealer GEX. */
    public static final class GexByDteRow {
        /** Bucket label, e.g. {@code "0-7d"}. */
        @SerializedName("bucket") public String bucket;
        @SerializedName("net_gex") public Double netGex;
        @SerializedName("pct_of_total") public Double pctOfTotal;
        @SerializedName("contract_count") public Integer contractCount;
    }

    /** One DTE bucket of net dealer theta. */
    public static final class ThetaByDteRow {
        @SerializedName("bucket") public String bucket;
        @SerializedName("net_theta") public Double netTheta;
        @SerializedName("contract_count") public Integer contractCount;
    }

    /** Put / call OI and volume sliced by expiry and by moneyness. */
    public static final class PutCallProfile {
        @SerializedName("by_expiry") public List<ByExpiryRow> byExpiry;
        @SerializedName("by_moneyness") public ByMoneyness byMoneyness;

        /** Per-expiry OI and volume P/C ratios. */
        public static final class ByExpiryRow {
            @SerializedName("expiry") public String expiry;
            @SerializedName("call_oi") public Long callOi;
            @SerializedName("put_oi") public Long putOi;
            @SerializedName("pc_ratio_oi") public Double pcRatioOi;
            @SerializedName("call_volume") public Long callVolume;
            @SerializedName("put_volume") public Long putVolume;
            @SerializedName("pc_ratio_volume") public Double pcRatioVolume;
        }

        /** OI bucketed by ITM / ATM / OTM for both calls and puts. */
        public static final class ByMoneyness {
            @SerializedName("otm_call_oi") public Long otmCallOi;
            @SerializedName("atm_call_oi") public Long atmCallOi;
            @SerializedName("itm_call_oi") public Long itmCallOi;
            @SerializedName("otm_put_oi") public Long otmPutOi;
            @SerializedName("atm_put_oi") public Long atmPutOi;
            @SerializedName("itm_put_oi") public Long itmPutOi;
        }
    }

    /** Top-N OI concentration plus the Herfindahl index. */
    public static final class OiConcentration {
        @SerializedName("top_3_pct") public Double top3Pct;
        @SerializedName("top_5_pct") public Double top5Pct;
        @SerializedName("top_10_pct") public Double top10Pct;
        @SerializedName("herfindahl") public Double herfindahl;
    }

    /** Dealer hedging required for a canonical spot move. */
    public static final class HedgingScenario {
        /** Spot move size, signed (e.g. {@code -0.02} = down 2%). */
        @SerializedName("move_pct") public Double movePct;
        /** Shares dealers must trade to stay delta-neutral. */
        @SerializedName("dealer_shares") public Long dealerShares;
        /** {@code "buy"} | {@code "sell"}. */
        @SerializedName("direction") public String direction;
        @SerializedName("notional_usd") public Double notionalUsd;
    }

    /** Quote liquidity (spreads / contract counts) at ATM and in the wings. */
    public static final class Liquidity {
        @SerializedName("atm_avg_spread_pct") public Double atmAvgSpreadPct;
        @SerializedName("wing_avg_spread_pct") public Double wingAvgSpreadPct;
        @SerializedName("atm_contracts") public Integer atmContracts;
        @SerializedName("wing_contracts") public Integer wingContracts;
    }
}
