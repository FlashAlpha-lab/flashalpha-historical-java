package com.flashalpha.historical;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Integration tests — hit the live https://historical.flashalpha.com.
 * Skipped unless FLASHALPHA_API_KEY is set.
 *
 * <pre>{@code
 * FLASHALPHA_API_KEY=fa_... mvn test -Dtest=IntegrationTest
 * }</pre>
 */
public class IntegrationTest {

    private static final String SPY_AT = "2024-08-05T15:30:00";
    private static final String SPY_DATE = "2024-08-05";
    private static final double EXPECTED_SPOT = 516.435;
    private static final double SPOT_TOL = 1.0;

    private static final Set<String> REGIMES = new HashSet<>();
    static {
        REGIMES.add("positive_gamma");
        REGIMES.add("negative_gamma");
        REGIMES.add("unknown");
    }

    private static String API_KEY;
    private FlashAlphaHistoricalClient hx;

    @BeforeClass
    public static void resolveKey() {
        API_KEY = System.getenv("FLASHALPHA_API_KEY");
    }

    @Before
    public void setUp() {
        Assume.assumeNotNull("FLASHALPHA_API_KEY not set", API_KEY);
        Assume.assumeFalse("FLASHALPHA_API_KEY blank", API_KEY.isEmpty());
        hx = new FlashAlphaHistoricalClient(API_KEY);
    }

    // ── Coverage ──────────────────────────────────────────────────────────

    @Test
    public void tickers_ListsSpy() {
        JsonObject out = hx.tickers();
        assertTrue(out.get("count").getAsInt() >= 1);
        boolean foundSpy = false;
        for (JsonElement e : out.getAsJsonArray("tickers")) {
            if ("SPY".equals(e.getAsJsonObject().get("symbol").getAsString())) {
                foundSpy = true;
                break;
            }
        }
        assertTrue("SPY should be listed", foundSpy);
    }

    @Test
    public void tickers_FilterBySpy_ReturnsObject() {
        JsonObject out = hx.tickers("SPY");
        assertEquals("SPY", out.get("symbol").getAsString());
        JsonObject cov = out.getAsJsonObject("coverage");
        assertTrue(cov.get("first").getAsString().compareTo("2024-08-05") <= 0);
        assertTrue(cov.get("last").getAsString().compareTo("2024-08-05") >= 0);
        assertTrue(cov.get("healthy_days").getAsInt() > 0);
    }

    @Test(expected = NoCoverageException.class)
    public void tickers_UnknownSymbol_ThrowsNoCoverage() {
        hx.tickers("ZZZZZ");
    }

    // ── Market data ───────────────────────────────────────────────────────

    @Test
    public void stockQuote_AtMinuteResolution() {
        JsonObject q = hx.stockQuote("SPY", SPY_AT);
        assertEquals("SPY", q.get("ticker").getAsString());
        double bid = q.get("bid").getAsDouble();
        double mid = q.get("mid").getAsDouble();
        double ask = q.get("ask").getAsDouble();
        assertTrue(bid <= mid);
        assertTrue(mid <= ask);
        assertTrue(Math.abs(mid - EXPECTED_SPOT) < SPOT_TOL);
        assertEquals(SPY_AT, q.get("lastUpdate").getAsString());
    }

    @Test
    public void stockQuote_DateOnly_DefaultsToSessionClose() {
        JsonObject q = hx.stockQuote("SPY", SPY_DATE);
        assertTrue(q.get("lastUpdate").getAsString().endsWith("T16:00:00"));
    }

    @Test
    public void stockQuote_LocalDateTimeOverload_Works() {
        JsonObject q = hx.stockQuote("SPY", LocalDateTime.of(2024, 8, 5, 15, 30, 0));
        assertTrue(Math.abs(q.get("mid").getAsDouble() - EXPECTED_SPOT) < SPOT_TOL);
    }

    @Test
    public void optionQuote_AllFilters_ReturnsSingleObjectWithGreeks() {
        JsonObject q = hx.optionQuote("SPY", SPY_AT, "2024-08-09", 520.0, "C");
        assertEquals(520, q.get("strike").getAsInt());
        assertEquals("C", q.get("type").getAsString());
        for (String g : new String[] {"delta", "gamma", "theta", "vega", "rho", "vanna", "charm"}) {
            assertTrue(q.get(g).getAsJsonPrimitive().isNumber());
        }
        // Documented historical-mode gaps
        assertEquals(0, q.get("bidSize").getAsInt());
        assertEquals(0, q.get("askSize").getAsInt());
        assertEquals(0, q.get("volume").getAsInt());
        assertTrue(q.get("svi_vol").isJsonNull());
        assertEquals("backtest_mode", q.get("svi_vol_gated").getAsString());
        assertTrue(q.get("open_interest").getAsInt() >= 0);
    }

    // ── Exposure ──────────────────────────────────────────────────────────

    @Test
    public void exposureSummary_EveryFieldDeclaredInPocoMustBeReferenced() {
        JsonObject s = hx.exposureSummary("SPY", SPY_AT);
        // ── top-level scalars ──
        assertEquals("SPY", s.get("symbol").getAsString());
        assertTrue(s.get("underlying_price").getAsJsonPrimitive().isNumber());
        assertTrue(Math.abs(s.get("underlying_price").getAsDouble() - EXPECTED_SPOT) < SPOT_TOL);
        assertTrue(s.get("as_of").getAsJsonPrimitive().isString());
        assertFalse(s.get("as_of").getAsString().isEmpty());
        assertEquals(SPY_AT, s.get("as_of").getAsString()); // historical snaps to requested minute
        assertTrue(REGIMES.contains(s.get("regime").getAsString()));
        assertTrue(s.get("gamma_flip").getAsJsonPrimitive().isNumber());
        // ── exposures block (4 fields) ──
        JsonObject e = s.getAsJsonObject("exposures");
        for (String k : new String[] {"net_gex", "net_dex", "net_vex", "net_chex"}) {
            assertTrue("exposures." + k, e.get(k).getAsJsonPrimitive().isNumber());
        }
        // ── interpretation block (3 fields) ──
        JsonObject interp = s.getAsJsonObject("interpretation");
        for (String k : new String[] {"gamma", "vanna", "charm"}) {
            assertTrue("interpretation." + k + " is string",
                    interp.get(k).getAsJsonPrimitive().isString());
            assertFalse("interpretation." + k + " not empty",
                    interp.get(k).getAsString().isEmpty());
        }
        // ── hedging_estimate (every leaf on both sides) ──
        JsonObject h = s.getAsJsonObject("hedging_estimate");
        for (String sideKey : new String[] {"spot_up_1pct", "spot_down_1pct"}) {
            JsonObject side = h.getAsJsonObject(sideKey);
            String dir = side.get("direction").getAsString();
            assertTrue(sideKey + ".direction=" + dir, "buy".equals(dir) || "sell".equals(dir));
            assertTrue(sideKey + ".dealer_shares_to_trade",
                    side.get("dealer_shares_to_trade").getAsJsonPrimitive().isNumber());
            assertTrue(sideKey + ".notional_usd",
                    side.get("notional_usd").getAsJsonPrimitive().isNumber());
            assertNotEquals(0L, side.get("notional_usd").getAsLong());
        }
        long up = h.getAsJsonObject("spot_up_1pct").get("dealer_shares_to_trade").getAsLong();
        long dn = h.getAsJsonObject("spot_down_1pct").get("dealer_shares_to_trade").getAsLong();
        assertEquals(up, -dn);
        // ── zero_dte block (3 fields) ──
        JsonObject z = s.getAsJsonObject("zero_dte");
        assertNotNull("zero_dte block", z);
        assertTrue("zero_dte.net_gex key present", z.has("net_gex"));
        assertTrue("zero_dte.net_gex null or number",
                z.get("net_gex").isJsonNull() || z.get("net_gex").getAsJsonPrimitive().isNumber());
        assertTrue("zero_dte.pct_of_total_gex key present", z.has("pct_of_total_gex"));
        assertTrue("zero_dte.pct_of_total_gex null or number",
                z.get("pct_of_total_gex").isJsonNull()
                        || z.get("pct_of_total_gex").getAsJsonPrimitive().isNumber());
        assertTrue("zero_dte.expiration key present", z.has("expiration"));
        assertTrue("zero_dte.expiration null or string",
                z.get("expiration").isJsonNull()
                        || z.get("expiration").getAsJsonPrimitive().isString());
    }

    @Test
    public void levels_KeysPresent() {
        JsonObject out = hx.exposureLevels("SPY", SPY_AT);
        JsonObject levels = out.getAsJsonObject("levels");
        for (String k : new String[] {
                "gamma_flip", "max_positive_gamma", "max_negative_gamma",
                "call_wall", "put_wall", "highest_oi_strike"}) {
            assertTrue("missing " + k, levels.has(k));
        }
    }

    @Test
    public void gex_StrikesShapeAndDocumentedZeros() {
        JsonObject gex = hx.gex("SPY", SPY_AT, null, 100);
        JsonArray strikes = gex.getAsJsonArray("strikes");
        assertTrue(strikes.size() > 5);
        JsonObject sample = strikes.get(0).getAsJsonObject();
        assertEquals(0, sample.get("call_volume").getAsInt());
        assertEquals(0, sample.get("put_volume").getAsInt());
        assertTrue(sample.get("call_oi_change").isJsonNull());
        assertTrue(sample.get("put_oi_change").isJsonNull());
    }

    @Test
    public void dex_PayloadShape() {
        JsonObject out = hx.dex("SPY", SPY_AT);
        assertTrue(out.getAsJsonObject("payload").get("net_dex").getAsJsonPrimitive().isNumber());
    }

    @Test
    public void vex_PayloadAndInterpretation() {
        JsonObject out = hx.vex("SPY", SPY_AT);
        JsonObject p = out.getAsJsonObject("payload");
        assertTrue(p.get("net_vex").getAsJsonPrimitive().isNumber());
        assertTrue(p.get("vex_interpretation").getAsJsonPrimitive().isString());
    }

    @Test
    public void chex_PayloadAndInterpretation() {
        JsonObject out = hx.chex("SPY", SPY_AT);
        JsonObject p = out.getAsJsonObject("payload");
        assertTrue(p.get("net_chex").getAsJsonPrimitive().isNumber());
        assertTrue(p.get("chex_interpretation").getAsJsonPrimitive().isString());
    }

    @Test
    public void narrative_ReturnsBlocks() {
        JsonObject out = hx.narrative("SPY", SPY_AT);
        JsonObject n = out.getAsJsonObject("narrative");
        for (String b : new String[] {"regime", "gex_change", "key_levels", "flow", "vanna", "charm", "zero_dte"}) {
            assertTrue(n.get(b).getAsJsonPrimitive().isString());
        }
        assertEquals(0, n.getAsJsonObject("data").getAsJsonArray("top_oi_changes").size());
    }

    @Test
    public void zeroDte_BasicShape() {
        JsonObject out = hx.zeroDte("SPY", SPY_AT);
        assertTrue(out.has("expiration"));
        assertTrue(out.has("regime"));
        assertTrue(out.has("exposures"));
    }

    // ── Composite & vol ───────────────────────────────────────────────────

    @Test
    public void stockSummary_BlockKeysAndDocumentedGaps() {
        JsonObject s = hx.stockSummary("SPY", SPY_AT);
        for (String k : new String[] {"price", "volatility", "options_flow", "exposure", "macro"}) {
            assertTrue(k, s.has(k));
        }
        JsonObject of = s.getAsJsonObject("options_flow");
        assertEquals(0L, of.get("total_call_volume").getAsLong());
        assertEquals(0L, of.get("total_put_volume").getAsLong());
        assertTrue(of.get("pc_ratio_volume").isJsonNull());
        JsonObject macro = s.getAsJsonObject("macro");
        assertTrue(macro.get("vix_futures").isJsonNull());
        assertTrue(macro.get("fear_and_greed").isJsonNull());
    }

    @Test
    public void volatility_RealizedLadder() {
        JsonObject v = hx.volatility("SPY", SPY_AT);
        JsonObject rv = v.getAsJsonObject("realized_vol");
        for (String w : new String[] {"rv_5d", "rv_10d", "rv_20d", "rv_30d", "rv_60d"}) {
            assertTrue(w, rv.has(w));
        }
    }

    @Test
    public void advVolatility_SviFitsAndVarianceSurface() {
        JsonObject adv = hx.advVolatility("SPY", SPY_AT);
        JsonArray svi = adv.getAsJsonArray("svi_parameters");
        assertTrue(svi.size() > 0);
        JsonObject first = svi.get(0).getAsJsonObject();
        for (String k : new String[] {"expiry", "a", "b", "rho", "m", "sigma", "forward"}) {
            assertTrue(k, first.has(k));
        }
    }

    // ── Surface ───────────────────────────────────────────────────────────

    @Test
    public void surface_50x50Grid() {
        JsonObject out = hx.surface("SPY", SPY_AT);
        assertEquals(50, out.get("grid_size").getAsInt());
        assertEquals(50, out.getAsJsonArray("tenors").size());
        assertEquals(50, out.getAsJsonArray("moneyness").size());
        JsonArray iv = out.getAsJsonArray("iv");
        assertEquals(50, iv.size());
        assertEquals(50, iv.get(0).getAsJsonArray().size());
    }

    // ── VRP ───────────────────────────────────────────────────────────────

    @Test
    public void vrp_EveryFieldDeclaredInPocoMustBeReferenced() {
        JsonObject v = hx.vrp("SPY", SPY_AT);

        // ── top-level scalars ──
        assertEquals("SPY", v.get("symbol").getAsString());
        assertTrue(v.get("underlying_price").getAsJsonPrimitive().isNumber());
        assertTrue(v.get("as_of").getAsJsonPrimitive().isString());
        assertTrue(v.get("market_open").getAsJsonPrimitive().isBoolean());
        for (String k : new String[] {"variance_risk_premium", "convexity_premium", "fair_vol"}) {
            assertTrue(k, v.get(k).getAsJsonPrimitive().isNumber());
        }
        assertTrue(v.get("warnings").isJsonArray());
        assertTrue("dealer_flow_risk key", v.has("dealer_flow_risk"));
        // strategy_scores / net_harvest_score nullable on historical
        assertTrue("strategy_scores key", v.has("strategy_scores"));
        assertTrue("net_harvest_score key", v.has("net_harvest_score"));
        // Customer trap: net_gex must NOT be top-level
        assertFalse("net_gex must NOT be top-level", v.has("net_gex"));

        // ── vrp.* core block ──
        JsonObject core = v.getAsJsonObject("vrp");
        for (String k : new String[] {"atm_iv", "rv_5d", "rv_10d", "rv_20d", "rv_30d",
                                       "vrp_5d", "vrp_10d", "vrp_20d", "vrp_30d"}) {
            assertTrue("vrp." + k, core.get(k).getAsJsonPrimitive().isNumber());
        }
        assertTrue("vrp.z_score key", core.has("z_score"));
        assertTrue("vrp.percentile key", core.has("percentile"));
        assertTrue("vrp.history_days", core.get("history_days").getAsJsonPrimitive().isNumber());

        // ── directional ──
        JsonObject dir = v.getAsJsonObject("directional");
        for (String k : new String[] {"put_wing_iv_25d", "call_wing_iv_25d",
                                       "downside_rv_20d", "upside_rv_20d",
                                       "downside_vrp", "upside_vrp"}) {
            assertTrue("directional." + k, dir.get(k).getAsJsonPrimitive().isNumber());
        }
        assertFalse("put_vrp must NOT exist", dir.has("put_vrp"));
        assertFalse("call_vrp must NOT exist", dir.has("call_vrp"));

        // ── term_vrp[] ──
        JsonArray term = v.getAsJsonArray("term_vrp");
        assertTrue("term_vrp non-empty", term.size() > 0);
        JsonObject first = term.get(0).getAsJsonObject();
        for (String k : new String[] {"dte", "iv", "rv", "vrp"}) {
            assertTrue("term_vrp[0]." + k, first.has(k));
        }

        // ── gex_conditioned + vanna_conditioned ──
        JsonObject gc = v.getAsJsonObject("gex_conditioned");
        assertTrue(gc.get("regime").getAsJsonPrimitive().isString());
        assertTrue(gc.get("harvest_score").getAsJsonPrimitive().isNumber());
        assertTrue(gc.get("interpretation").getAsJsonPrimitive().isString());
        JsonObject vc = v.getAsJsonObject("vanna_conditioned");
        assertTrue(vc.get("outlook").getAsJsonPrimitive().isString());
        assertTrue(vc.get("interpretation").getAsJsonPrimitive().isString());

        // ── regime — net_gex lives HERE ──
        JsonObject reg = v.getAsJsonObject("regime");
        assertTrue(reg.get("gamma").getAsJsonPrimitive().isString());
        assertTrue("regime.vrp_regime key", reg.has("vrp_regime"));
        assertTrue(reg.get("net_gex").getAsJsonPrimitive().isNumber());
        assertTrue(reg.get("gamma_flip").getAsJsonPrimitive().isNumber());

        // ── macro (historical-specific) ──
        JsonObject macro = v.getAsJsonObject("macro");
        for (String k : new String[] {"vix", "vix_3m", "vix_term_slope", "dgs10", "hy_spread"}) {
            assertTrue("macro." + k, macro.get(k).getAsJsonPrimitive().isNumber());
        }
        assertFalse("macro.fed_funds must NOT exist on historical", macro.has("fed_funds"));
    }

    // ── Max Pain ──────────────────────────────────────────────────────────

    @Test
    public void maxPain_PainCurveMinimumIsAtMaxPainStrike() {
        JsonObject mp = hx.maxPain("SPY", SPY_AT, "2024-08-09");
        assertEquals("2024-08-09", mp.get("expiration").getAsString());
        double maxPainStrike = mp.get("max_pain_strike").getAsDouble();
        JsonArray curve = mp.getAsJsonArray("pain_curve");
        assertTrue(curve.size() > 0);
        double bestStrike = Double.NaN;
        double bestPain = Double.MAX_VALUE;
        for (JsonElement r : curve) {
            JsonObject row = r.getAsJsonObject();
            double total = row.get("total_pain").getAsDouble();
            if (total < bestPain) {
                bestPain = total;
                bestStrike = row.get("strike").getAsDouble();
            }
        }
        assertTrue("min total_pain at " + bestStrike + " but max_pain_strike=" + maxPainStrike,
                Math.abs(bestStrike - maxPainStrike) <= 5);
    }

    // ── rc.4 typed POCO field-walk tests ──────────────────────────────────
    //
    // Mirror of the *EveryFieldDeclaredInPocoMustBeReferenced pattern but
    // walked through the typed POCOs. A renamed wire field surfaces as a
    // null assertion failure. Historical-mode documented gaps (zero
    // volumes, null pc_ratio_volume, null vix_futures, null fear_and_greed)
    // are explicitly accommodated.

    @Test
    public void testStockSummary_EveryFieldDeclaredInPocoMustBeReferenced() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        JsonObject json = hx.stockSummary("SPY", SPY_AT);
        StockSummaryResponse r = gson.fromJson(json, StockSummaryResponse.class);

        // ── top-level ──
        assertEquals("SPY", r.symbol);
        assertEquals(SPY_AT, r.asOf);
        assertNotNull("market_open", r.marketOpen);

        // ── price ──
        assertNotNull("price", r.price);
        assertNotNull("price.bid", r.price.bid);
        assertNotNull("price.ask", r.price.ask);
        assertNotNull("price.mid", r.price.mid);
        assertNotNull("price.last", r.price.last);
        assertNotNull("price.last_update", r.price.lastUpdate);

        // ── volatility ──
        assertNotNull("volatility", r.volatility);
        assertNotNull("volatility.atm_iv", r.volatility.atmIv);
        assertNotNull("volatility.hv_20", r.volatility.hv20);
        assertNotNull("volatility.hv_60", r.volatility.hv60);
        assertNotNull("volatility.vrp", r.volatility.vrp);

        // skew_25d — full 7-field block (rc.4)
        assertNotNull("volatility.skew_25d", r.volatility.skew25d);
        assertNotNull("skew_25d.expiry", r.volatility.skew25d.expiry);
        assertNotNull("skew_25d.days_to_expiry", r.volatility.skew25d.daysToExpiry);
        assertNotNull("skew_25d.put_25d_iv", r.volatility.skew25d.put25dIv);
        assertNotNull("skew_25d.atm_iv", r.volatility.skew25d.atmIv);
        assertNotNull("skew_25d.call_25d_iv", r.volatility.skew25d.call25dIv);
        assertNotNull("skew_25d.skew_25d", r.volatility.skew25d.skew25d);
        assertNotNull("skew_25d.smile_ratio", r.volatility.skew25d.smileRatio);

        // iv_term_structure
        assertNotNull("iv_term_structure", r.volatility.ivTermStructure);
        assertFalse("iv_term_structure non-empty", r.volatility.ivTermStructure.isEmpty());
        StockSummaryResponse.TermStructureRow tsr = r.volatility.ivTermStructure.get(0);
        assertNotNull("term_structure[0].expiry", tsr.expiry);
        assertNotNull("term_structure[0].days_to_expiry", tsr.daysToExpiry);
        assertNotNull("term_structure[0].iv", tsr.iv);

        // ── options_flow (rc.4 wire uses total_* prefix; historical-mode
        // has zero volumes and null pc_ratio_volume — documented gap) ──
        assertNotNull("options_flow", r.optionsFlow);
        assertNotNull("options_flow.total_call_oi", r.optionsFlow.callOi);
        assertNotNull("options_flow.total_put_oi", r.optionsFlow.putOi);
        // call/put_volume are present but 0 in historical mode
        assertNotNull("options_flow.total_call_volume", r.optionsFlow.callVolume);
        assertNotNull("options_flow.total_put_volume", r.optionsFlow.putVolume);
        assertEquals(0L, (long) r.optionsFlow.callVolume);
        assertEquals(0L, (long) r.optionsFlow.putVolume);
        assertNotNull("options_flow.pc_ratio_oi", r.optionsFlow.pcRatioOi);
        // pc_ratio_volume is null in historical mode (zero volumes → div-by-0)
        assertNotNull("options_flow.active_expirations", r.optionsFlow.activeExpirations);

        // ── exposure ──
        assertNotNull("exposure", r.exposure);
        assertNotNull("exposure.net_gex", r.exposure.netGex);
        assertNotNull("exposure.net_dex", r.exposure.netDex);
        assertNotNull("exposure.net_vex", r.exposure.netVex);
        assertNotNull("exposure.net_chex", r.exposure.netChex);
        assertNotNull("exposure.gamma_flip", r.exposure.gammaFlip);
        assertNotNull("exposure.call_wall", r.exposure.callWall);
        assertNotNull("exposure.put_wall", r.exposure.putWall);
        assertNotNull("exposure.max_pain", r.exposure.maxPain);
        assertNotNull("exposure.highest_oi_strike", r.exposure.highestOiStrike);
        assertNotNull("exposure.regime", r.exposure.regime);
        assertTrue("exposure.regime=" + r.exposure.regime, REGIMES.contains(r.exposure.regime));
        assertNotNull("exposure.oi_weighted_dte", r.exposure.oiWeightedDte);

        assertNotNull("interpretation", r.exposure.interpretation);
        assertNotNull("interpretation.gamma", r.exposure.interpretation.gamma);
        assertNotNull("interpretation.vanna", r.exposure.interpretation.vanna);
        assertNotNull("interpretation.charm", r.exposure.interpretation.charm);

        assertNotNull("hedging_estimate", r.exposure.hedgingEstimate);
        StockSummaryResponse.HedgingMove[] hMoves = {
                r.exposure.hedgingEstimate.spotUp1Pct,
                r.exposure.hedgingEstimate.spotDown1Pct,
        };
        String[] hNames = {"spot_up_1pct", "spot_down_1pct"};
        for (int i = 0; i < hMoves.length; i++) {
            assertNotNull("hedging_estimate." + hNames[i], hMoves[i]);
            assertNotNull(hNames[i] + ".dealer_shares", hMoves[i].dealerShares);
            assertNotNull(hNames[i] + ".direction", hMoves[i].direction);
            assertTrue(hNames[i] + ".direction=" + hMoves[i].direction,
                    "buy".equals(hMoves[i].direction) || "sell".equals(hMoves[i].direction));
            assertNotNull(hNames[i] + ".notional_usd", hMoves[i].notionalUsd);
        }

        assertNotNull("zero_dte", r.exposure.zeroDte);
        assertNotNull("zero_dte.net_gex", r.exposure.zeroDte.netGex);
        assertNotNull("zero_dte.pct_of_total", r.exposure.zeroDte.pctOfTotal);
        // zero_dte.expiration may be null on no-0DTE days

        // top_strikes (rc.4 adds total_oi)
        assertNotNull("top_strikes", r.exposure.topStrikes);
        assertFalse("top_strikes non-empty", r.exposure.topStrikes.isEmpty());
        StockSummaryResponse.TopStrikeRow ts = r.exposure.topStrikes.get(0);
        assertNotNull("top_strikes[0].strike", ts.strike);
        assertNotNull("top_strikes[0].net_gex", ts.netGex);
        assertNotNull("top_strikes[0].call_oi", ts.callOi);
        assertNotNull("top_strikes[0].put_oi", ts.putOi);
        assertNotNull("top_strikes[0].total_oi", ts.totalOi);

        // ── macro (vix_futures + fear_and_greed are null in historical) ──
        assertNotNull("macro", r.macro);
        StockSummaryResponse.Quote[] quotes = {
                r.macro.vix, r.macro.vvix, r.macro.skew, r.macro.spx, r.macro.move
        };
        String[] quoteNames = {"vix", "vvix", "skew", "spx", "move"};
        for (int i = 0; i < quotes.length; i++) {
            if (quotes[i] != null) {
                assertNotNull(quoteNames[i] + ".value", quotes[i].value);
            }
        }
        if (r.macro.vixTermStructure != null) {
            assertNotNull("vix_term_structure.structure", r.macro.vixTermStructure.structure);
            assertNotNull("vix_term_structure.near_slope_pct", r.macro.vixTermStructure.nearSlopePct);
            if (r.macro.vixTermStructure.levels != null) {
                StockSummaryResponse.VixTermLevels lv = r.macro.vixTermStructure.levels;
                assertNotNull("vix_term_structure.levels.vix", lv.vix);
                java.util.Objects.requireNonNullElse(lv.vix9d, 0.0);
                java.util.Objects.requireNonNullElse(lv.vix3m, 0.0);
                java.util.Objects.requireNonNullElse(lv.vix6m, 0.0);
            }
        }
        // vix_futures null in historical mode (documented gap) — typed leaves
        // exercised via deserialization; if non-null, walk them.
        if (r.macro.vixFutures != null) {
            assertNotNull("vix_futures.front_month", r.macro.vixFutures.frontMonth);
            assertNotNull("vix_futures.spot", r.macro.vixFutures.spot);
            assertNotNull("vix_futures.spread", r.macro.vixFutures.spread);
            assertNotNull("vix_futures.basis_pct", r.macro.vixFutures.basisPct);
            assertNotNull("vix_futures.basis", r.macro.vixFutures.basis);
        }
        // fear_and_greed null in historical mode (documented gap)
        if (r.macro.fearAndGreed != null) {
            assertNotNull("fear_and_greed.score", r.macro.fearAndGreed.score);
            assertNotNull("fear_and_greed.rating", r.macro.fearAndGreed.rating);
        }
    }

    @Test
    public void testNarrative_EveryFieldDeclaredInPocoMustBeReferenced() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        JsonObject json = hx.narrative("SPY", SPY_AT);
        NarrativeResponse r = gson.fromJson(json, NarrativeResponse.class);

        assertEquals("SPY", r.symbol);
        assertNotNull("underlying_price", r.underlyingPrice);
        assertNotNull("as_of", r.asOf);
        assertNotNull("narrative", r.narrative);

        // narrative prose strings — every leaf
        assertNotNull("narrative.regime", r.narrative.regime);
        assertFalse("narrative.regime non-empty", r.narrative.regime.isEmpty());
        assertNotNull("narrative.gex_change", r.narrative.gexChange);
        assertNotNull("narrative.key_levels", r.narrative.keyLevels);
        assertNotNull("narrative.flow", r.narrative.flow);
        assertNotNull("narrative.vanna", r.narrative.vanna);
        assertNotNull("narrative.charm", r.narrative.charm);
        assertNotNull("narrative.zero_dte", r.narrative.zeroDte);
        assertNotNull("narrative.outlook", r.narrative.outlook);

        // narrative.data block
        assertNotNull("narrative.data", r.narrative.data);
        NarrativeResponse.NarrativeData d = r.narrative.data;
        assertNotNull("data.net_gex", d.netGex);
        assertNotNull("data.net_gex_prior", d.netGexPrior);
        assertNotNull("data.net_gex_change_pct", d.netGexChangePct);
        assertNotNull("data.vix", d.vix);
        assertNotNull("data.gamma_flip", d.gammaFlip);
        assertNotNull("data.call_wall", d.callWall);
        assertNotNull("data.put_wall", d.putWall);
        assertNotNull("data.regime", d.regime);
        assertTrue("data.regime=" + d.regime, REGIMES.contains(d.regime));
        assertNotNull("data.zero_dte_pct", d.zeroDtePct);

        // top_oi_changes (empty list documented for historical mode);
        // POCO declares the row shape — exercise its leaves only when a row exists.
        assertNotNull("data.top_oi_changes", d.topOiChanges);
        if (!d.topOiChanges.isEmpty()) {
            NarrativeResponse.OiChangeRow row = d.topOiChanges.get(0);
            assertNotNull("top_oi_changes[0].strike", row.strike);
            assertNotNull("top_oi_changes[0].type", row.type);
            assertTrue("type=" + row.type, "call".equals(row.type) || "put".equals(row.type));
            assertNotNull("top_oi_changes[0].oi_change", row.oiChange);
            assertNotNull("top_oi_changes[0].volume", row.volume);
        }
    }

    @Test
    public void testExposureLevels_EveryFieldDeclaredInPocoMustBeReferenced() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        JsonObject json = hx.exposureLevels("SPY", SPY_AT);
        ExposureLevelsResponse r = gson.fromJson(json, ExposureLevelsResponse.class);

        assertEquals("SPY", r.symbol);
        assertNotNull("underlying_price", r.underlyingPrice);
        assertNotNull("as_of", r.asOf);
        assertNotNull("levels", r.levels);

        // All 7 levels including zero_dte_magnet
        assertNotNull("levels.gamma_flip", r.levels.gammaFlip);
        assertNotNull("levels.max_positive_gamma", r.levels.maxPositiveGamma);
        assertNotNull("levels.max_negative_gamma", r.levels.maxNegativeGamma);
        assertNotNull("levels.call_wall", r.levels.callWall);
        assertNotNull("levels.put_wall", r.levels.putWall);
        assertNotNull("levels.highest_oi_strike", r.levels.highestOiStrike);
        assertNotNull("levels.zero_dte_magnet", r.levels.zeroDteMagnet);
    }

    // ── Errors ────────────────────────────────────────────────────────────

    @Test(expected = InvalidAtException.class)
    public void invalidAt_Throws() { hx.exposureSummary("SPY", "garbage"); }

    @Test(expected = NoDataException.class)
    public void outOfCoverage_Throws() { hx.exposureSummary("SPY", "2017-01-01"); }

    @Test(expected = NoDataException.class)
    public void holiday_Throws() { hx.exposureSummary("SPY", "2024-01-01"); }

    @Test(expected = NoDataException.class)
    public void optionQuote_NonexistentStrike_Throws() {
        hx.optionQuote("SPY", SPY_AT, "2024-08-09", 99999.0, "C");
    }

    // ── Replay & Backtester ───────────────────────────────────────────────

    @Test
    public void replay_OneTradingWeek() {
        List<LocalDateTime> days = Replay.iterDays(
                LocalDate.of(2024, 8, 5), LocalDate.of(2024, 8, 9));
        List<Replay.Step> steps = Replay.run(hx, Backtester.EXPOSURE_SUMMARY, "SPY", days);
        assertEquals(5, steps.size());
        for (Replay.Step s : steps) {
            assertEquals("SPY", s.response.get("symbol").getAsString());
            assertTrue(REGIMES.contains(s.response.get("regime").getAsString()));
        }
    }

    @Test
    public void replay_OneDayAt30MinStep() {
        List<LocalDateTime> minutes = Replay.iterMinutes(
                LocalDate.of(2024, 8, 5), LocalDate.of(2024, 8, 5), 30);
        List<Replay.Step> steps = Replay.run(hx, Backtester.EXPOSURE_SUMMARY, "SPY", minutes);
        assertEquals(14, steps.size());
        Set<Double> spots = new HashSet<>();
        for (Replay.Step s : steps) {
            spots.add(s.response.get("underlying_price").getAsDouble());
        }
        assertTrue("spot constant across day", spots.size() > 1);
    }

    @Test
    public void replay_SkipsHolidaySilently() {
        AtomicInteger errors = new AtomicInteger();
        List<LocalDateTime> ts = List.of(
                LocalDateTime.of(2024, 8, 5, 15, 30, 0),
                LocalDateTime.of(2024, 1, 1, 16, 0, 0));
        List<Replay.Step> steps = Replay.run(hx, Backtester.EXPOSURE_SUMMARY, "SPY", ts,
                true, (at, ex) -> errors.incrementAndGet());
        assertEquals(1, steps.size());
        assertEquals(1, errors.get());
    }

    @Test
    public void backtester_RunsStrategyAndCollectsOutputs() {
        Backtester bt = new Backtester(hx, Backtester.STOCK_SUMMARY, "SPY");
        List<LocalDateTime> days = Replay.iterDays(
                LocalDate.of(2024, 8, 5), LocalDate.of(2024, 8, 9));
        List<Backtester.Step> results = bt.run(days, (at, snap) -> {
            JsonObject vol = snap.getAsJsonObject("volatility");
            JsonObject exp = snap.getAsJsonObject("exposure");
            JsonObject row = new JsonObject();
            row.add("vrp", vol.get("vrp"));
            row.add("regime", exp.get("regime"));
            return row;
        });
        assertEquals(5, results.size());
        for (Backtester.Step r : results) {
            JsonObject out = (JsonObject) r.output;
            assertTrue(REGIMES.contains(out.get("regime").getAsString()));
        }
    }

    // ── rc.9 typed POCO field-walk tests ──────────────────────────────────
    //
    // Same EveryFieldDeclaredInPocoMustBeReferenced discipline as the rc.4
    // tests above, extended to the rc.9 types: Volatility, AdvVolatility,
    // Surface, Gex, Dex, Vex, Chex, and ZeroDte (typed). Historical-mode
    // documented gaps (payload-wrapped Dex/Vex/Chex; intraday-only fields
    // null on the historical grid) are explicitly accommodated.

    @Test
    public void testVolatility_EveryFieldDeclaredInPocoMustBeReferenced() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        JsonObject json = hx.volatility("SPY", SPY_AT);
        VolatilityResponse r = gson.fromJson(json, VolatilityResponse.class);

        assertEquals("SPY", r.symbol);
        assertNotNull("underlying_price", r.underlyingPrice);
        assertNotNull("as_of", r.asOf);
        assertNotNull("market_open", r.marketOpen);
        assertNotNull("atm_iv", r.atmIv);

        // realized_vol
        assertNotNull("realized_vol", r.realizedVol);
        assertNotNull("realized_vol.rv_5d",  r.realizedVol.rv5d);
        assertNotNull("realized_vol.rv_10d", r.realizedVol.rv10d);
        assertNotNull("realized_vol.rv_20d", r.realizedVol.rv20d);
        assertNotNull("realized_vol.rv_30d", r.realizedVol.rv30d);
        assertNotNull("realized_vol.rv_60d", r.realizedVol.rv60d);

        // iv_rv_spreads
        assertNotNull("iv_rv_spreads", r.ivRvSpreads);
        assertNotNull("iv_rv_spreads.vrp_5d",  r.ivRvSpreads.vrp5d);
        assertNotNull("iv_rv_spreads.vrp_10d", r.ivRvSpreads.vrp10d);
        assertNotNull("iv_rv_spreads.vrp_20d", r.ivRvSpreads.vrp20d);
        assertNotNull("iv_rv_spreads.vrp_30d", r.ivRvSpreads.vrp30d);
        assertNotNull("iv_rv_spreads.assessment", r.ivRvSpreads.assessment);

        // skew_profiles[0]
        assertNotNull("skew_profiles", r.skewProfiles);
        assertFalse("skew_profiles non-empty", r.skewProfiles.isEmpty());
        VolatilityResponse.SkewProfile sp = r.skewProfiles.get(0);
        assertNotNull("skew_profiles[0].expiry", sp.expiry);
        assertNotNull("skew_profiles[0].days_to_expiry", sp.daysToExpiry);
        assertNotNull("skew_profiles[0].put_10d_iv", sp.put10dIv);
        assertNotNull("skew_profiles[0].put_25d_iv", sp.put25dIv);
        assertNotNull("skew_profiles[0].atm_iv", sp.atmIv);
        assertNotNull("skew_profiles[0].call_25d_iv", sp.call25dIv);
        assertNotNull("skew_profiles[0].call_10d_iv", sp.call10dIv);
        assertNotNull("skew_profiles[0].skew_25d", sp.skew25d);
        assertNotNull("skew_profiles[0].smile_ratio", sp.smileRatio);
        assertNotNull("skew_profiles[0].tail_convexity", sp.tailConvexity);

        // term_structure
        assertNotNull("term_structure", r.termStructure);
        assertNotNull("term_structure.near_slope_pct", r.termStructure.nearSlopePct);
        assertNotNull("term_structure.far_slope_pct",  r.termStructure.farSlopePct);
        assertNotNull("term_structure.state", r.termStructure.state);

        // iv_dispersion
        assertNotNull("iv_dispersion", r.ivDispersion);
        assertNotNull("iv_dispersion.cross_expiry", r.ivDispersion.crossExpiry);
        assertNotNull("iv_dispersion.cross_strike", r.ivDispersion.crossStrike);

        // gex_by_dte / theta_by_dte
        assertNotNull("gex_by_dte", r.gexByDte);
        assertFalse("gex_by_dte non-empty", r.gexByDte.isEmpty());
        VolatilityResponse.GexByDteRow gex = r.gexByDte.get(0);
        assertNotNull("gex_by_dte[0].bucket", gex.bucket);
        assertNotNull("gex_by_dte[0].net_gex", gex.netGex);
        assertNotNull("gex_by_dte[0].pct_of_total", gex.pctOfTotal);
        assertNotNull("gex_by_dte[0].contract_count", gex.contractCount);

        assertNotNull("theta_by_dte", r.thetaByDte);
        assertFalse("theta_by_dte non-empty", r.thetaByDte.isEmpty());
        VolatilityResponse.ThetaByDteRow th = r.thetaByDte.get(0);
        assertNotNull("theta_by_dte[0].bucket", th.bucket);
        assertNotNull("theta_by_dte[0].net_theta", th.netTheta);
        assertNotNull("theta_by_dte[0].contract_count", th.contractCount);

        // put_call_profile — historical-mode volumes are 0, pc_ratio_volume
        // null (documented gap) but typed leaves still exercised
        assertNotNull("put_call_profile", r.putCallProfile);
        assertNotNull("put_call_profile.by_expiry", r.putCallProfile.byExpiry);
        assertFalse("put_call_profile.by_expiry non-empty", r.putCallProfile.byExpiry.isEmpty());
        VolatilityResponse.PutCallProfile.ByExpiryRow be = r.putCallProfile.byExpiry.get(0);
        assertNotNull("by_expiry[0].expiry", be.expiry);
        assertNotNull("by_expiry[0].call_oi", be.callOi);
        assertNotNull("by_expiry[0].put_oi", be.putOi);
        assertNotNull("by_expiry[0].pc_ratio_oi", be.pcRatioOi);
        assertNotNull("by_expiry[0].call_volume", be.callVolume);
        assertNotNull("by_expiry[0].put_volume", be.putVolume);
        assertNotNull("put_call_profile.by_moneyness", r.putCallProfile.byMoneyness);
        VolatilityResponse.PutCallProfile.ByMoneyness bm = r.putCallProfile.byMoneyness;
        assertNotNull("by_moneyness.otm_call_oi", bm.otmCallOi);
        assertNotNull("by_moneyness.atm_call_oi", bm.atmCallOi);
        assertNotNull("by_moneyness.itm_call_oi", bm.itmCallOi);
        assertNotNull("by_moneyness.otm_put_oi", bm.otmPutOi);
        assertNotNull("by_moneyness.atm_put_oi", bm.atmPutOi);
        assertNotNull("by_moneyness.itm_put_oi", bm.itmPutOi);

        // oi_concentration
        assertNotNull("oi_concentration", r.oiConcentration);
        assertNotNull("oi_concentration.top_3_pct", r.oiConcentration.top3Pct);
        assertNotNull("oi_concentration.top_5_pct", r.oiConcentration.top5Pct);
        assertNotNull("oi_concentration.top_10_pct", r.oiConcentration.top10Pct);
        assertNotNull("oi_concentration.herfindahl", r.oiConcentration.herfindahl);

        // hedging_scenarios
        assertNotNull("hedging_scenarios", r.hedgingScenarios);
        assertFalse("hedging_scenarios non-empty", r.hedgingScenarios.isEmpty());
        VolatilityResponse.HedgingScenario hs = r.hedgingScenarios.get(0);
        assertNotNull("hedging_scenarios[0].move_pct", hs.movePct);
        assertNotNull("hedging_scenarios[0].dealer_shares", hs.dealerShares);
        assertNotNull("hedging_scenarios[0].direction", hs.direction);
        assertNotNull("hedging_scenarios[0].notional_usd", hs.notionalUsd);

        // liquidity
        assertNotNull("liquidity", r.liquidity);
        assertNotNull("liquidity.atm_avg_spread_pct", r.liquidity.atmAvgSpreadPct);
        assertNotNull("liquidity.wing_avg_spread_pct", r.liquidity.wingAvgSpreadPct);
        assertNotNull("liquidity.atm_contracts", r.liquidity.atmContracts);
        assertNotNull("liquidity.wing_contracts", r.liquidity.wingContracts);
    }

    @Test
    public void testAdvVolatility_EveryFieldDeclaredInPocoMustBeReferenced() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        JsonObject json = hx.advVolatility("SPY", SPY_AT);
        AdvVolatilityResponse r = gson.fromJson(json, AdvVolatilityResponse.class);

        assertEquals("SPY", r.symbol);
        assertNotNull("underlying_price", r.underlyingPrice);
        assertNotNull("as_of", r.asOf);
        assertNotNull("market_open", r.marketOpen);

        // svi_parameters
        assertNotNull("svi_parameters", r.sviParameters);
        assertFalse("svi_parameters non-empty", r.sviParameters.isEmpty());
        AdvVolatilityResponse.SviParameters svi = r.sviParameters.get(0);
        assertNotNull("svi_parameters[0].expiry", svi.expiry);
        assertNotNull("svi_parameters[0].days_to_expiry", svi.daysToExpiry);
        assertNotNull("svi_parameters[0].forward", svi.forward);
        assertNotNull("svi_parameters[0].a", svi.a);
        assertNotNull("svi_parameters[0].b", svi.b);
        assertNotNull("svi_parameters[0].rho", svi.rho);
        assertNotNull("svi_parameters[0].m", svi.m);
        assertNotNull("svi_parameters[0].sigma", svi.sigma);
        assertNotNull("svi_parameters[0].atm_total_variance", svi.atmTotalVariance);
        assertNotNull("svi_parameters[0].atm_iv", svi.atmIv);

        // forward_prices
        assertNotNull("forward_prices", r.forwardPrices);
        assertFalse("forward_prices non-empty", r.forwardPrices.isEmpty());
        AdvVolatilityResponse.ForwardPrice fp = r.forwardPrices.get(0);
        assertNotNull("forward_prices[0].expiry", fp.expiry);
        assertNotNull("forward_prices[0].days_to_expiry", fp.daysToExpiry);
        assertNotNull("forward_prices[0].forward", fp.forward);
        assertNotNull("forward_prices[0].spot", fp.spot);
        assertNotNull("forward_prices[0].basis_pct", fp.basisPct);

        // total_variance_surface
        assertNotNull("total_variance_surface", r.totalVarianceSurface);
        assertNotNull("total_variance_surface.moneyness", r.totalVarianceSurface.moneyness);
        assertNotNull("total_variance_surface.expiries", r.totalVarianceSurface.expiries);
        assertNotNull("total_variance_surface.tenors", r.totalVarianceSurface.tenors);
        assertNotNull("total_variance_surface.total_variance", r.totalVarianceSurface.totalVariance);
        assertNotNull("total_variance_surface.implied_vol", r.totalVarianceSurface.impliedVol);
        assertTrue("total_variance non-empty", r.totalVarianceSurface.totalVariance.length > 0);
        assertTrue("implied_vol non-empty", r.totalVarianceSurface.impliedVol.length > 0);

        // arbitrage_flags — typed leaves exercised when at least one row is present
        assertNotNull("arbitrage_flags", r.arbitrageFlags);
        if (!r.arbitrageFlags.isEmpty()) {
            AdvVolatilityResponse.ArbitrageFlag af = r.arbitrageFlags.get(0);
            assertNotNull("arbitrage_flags[0].expiry", af.expiry);
            assertNotNull("arbitrage_flags[0].type", af.type);
            assertNotNull("arbitrage_flags[0].strike_or_k", af.strikeOrK);
            assertNotNull("arbitrage_flags[0].description", af.description);
        }

        // variance_swap_fair_values
        assertNotNull("variance_swap_fair_values", r.varianceSwapFairValues);
        assertFalse("variance_swap_fair_values non-empty", r.varianceSwapFairValues.isEmpty());
        AdvVolatilityResponse.VarianceSwapFairValue vs = r.varianceSwapFairValues.get(0);
        assertNotNull("variance_swap_fair_values[0].expiry", vs.expiry);
        assertNotNull("variance_swap_fair_values[0].days_to_expiry", vs.daysToExpiry);
        assertNotNull("variance_swap_fair_values[0].fair_variance", vs.fairVariance);
        assertNotNull("variance_swap_fair_values[0].fair_vol", vs.fairVol);
        assertNotNull("variance_swap_fair_values[0].atm_iv", vs.atmIv);
        assertNotNull("variance_swap_fair_values[0].convexity_adjustment", vs.convexityAdjustment);

        // greeks_surfaces
        assertNotNull("greeks_surfaces", r.greeksSurfaces);
        AdvVolatilityResponse.GreekSurface[] surfaces = {
                r.greeksSurfaces.vanna, r.greeksSurfaces.charm,
                r.greeksSurfaces.volga, r.greeksSurfaces.speed,
        };
        String[] surfNames = {"vanna", "charm", "volga", "speed"};
        for (int i = 0; i < surfaces.length; i++) {
            assertNotNull("greeks_surfaces." + surfNames[i], surfaces[i]);
            assertNotNull("greeks_surfaces." + surfNames[i] + ".strikes", surfaces[i].strikes);
            assertNotNull("greeks_surfaces." + surfNames[i] + ".expiries", surfaces[i].expiries);
            assertNotNull("greeks_surfaces." + surfNames[i] + ".values", surfaces[i].values);
            assertTrue("greeks_surfaces." + surfNames[i] + ".values non-empty",
                    surfaces[i].values.length > 0);
        }
    }

    @Test
    public void testSurface_EveryFieldDeclaredInPocoMustBeReferenced() {
        SurfaceResponse r = hx.surfaceTyped("SPY", SPY_AT);

        assertEquals("SPY", r.symbol);
        assertNotNull("spot", r.spot);
        assertNotNull("as_of", r.asOf);
        assertNotNull("grid_size", r.gridSize);
        assertNotNull("tenors", r.tenors);
        assertNotNull("moneyness", r.moneyness);
        assertNotNull("iv", r.iv);
        assertEquals("tenors length matches grid_size", (int) r.gridSize, r.tenors.size());
        assertEquals("moneyness length matches grid_size", (int) r.gridSize, r.moneyness.size());
        assertEquals("iv outer length matches grid_size", (int) r.gridSize, r.iv.length);
        assertEquals("iv inner length matches grid_size", (int) r.gridSize, r.iv[0].length);
        assertNotNull("slices_used", r.slicesUsed);
    }

    @Test
    public void testGex_EveryFieldDeclaredInPocoMustBeReferenced() {
        GexResponse r = hx.gexTyped("SPY", SPY_AT);

        assertEquals("SPY", r.symbol);
        assertNotNull("underlying_price", r.underlyingPrice);
        assertNotNull("as_of", r.asOf);
        assertNotNull("gamma_flip", r.gammaFlip);
        assertNotNull("net_gex", r.netGex);
        // net_gex_label is a typed leaf — populated from the wire when the
        // upstream classifier assigned a regime, otherwise null on the grid
        assertNotNull("strikes", r.strikes);
        assertFalse("strikes non-empty", r.strikes.isEmpty());
        GexResponse.GexStrikeRow row = r.strikes.get(0);
        assertNotNull("strikes[0].strike", row.strike);
        assertNotNull("strikes[0].call_gex", row.callGex);
        assertNotNull("strikes[0].put_gex", row.putGex);
        assertNotNull("strikes[0].net_gex", row.netGex);
        assertNotNull("strikes[0].call_oi", row.callOi);
        assertNotNull("strikes[0].put_oi", row.putOi);
        // Historical-mode documented gap: call_volume / put_volume = 0,
        // call_oi_change / put_oi_change null (no prior-day reference)
        assertNotNull("strikes[0].call_volume", row.callVolume);
        assertNotNull("strikes[0].put_volume", row.putVolume);
        Long callChg = row.callOiChange;
        Long putChg = row.putOiChange;
        assertTrue("strikes[0].call_oi_change typed leaf", callChg == null || callChg.longValue() == callChg);
        assertTrue("strikes[0].put_oi_change typed leaf", putChg == null || putChg.longValue() == putChg);
    }

    @Test
    public void testDex_EveryFieldDeclaredInPocoMustBeReferenced() {
        // Historical Dex wire wraps the analytics in {symbol, as_of, payload:
        // {net_dex, ..., strikes: [...]}}. Walk the JSON directly so the
        // payload-wrapped shape is exercised end-to-end.
        JsonObject out = hx.dex("SPY", SPY_AT);
        assertEquals("SPY", out.get("symbol").getAsString());
        assertEquals(SPY_AT, out.get("as_of").getAsString());
        JsonObject payload = out.getAsJsonObject("payload");
        assertNotNull("payload", payload);
        assertTrue("payload.net_dex", payload.get("net_dex").getAsJsonPrimitive().isNumber());
        JsonArray strikes = payload.getAsJsonArray("strikes");
        assertTrue("payload.strikes non-empty", strikes.size() > 0);
        JsonObject row = strikes.get(0).getAsJsonObject();
        for (String k : new String[]{"strike", "call_dex", "put_dex", "net_dex"}) {
            assertTrue("strikes[0]." + k, row.get(k).getAsJsonPrimitive().isNumber());
        }
    }

    @Test
    public void testVex_EveryFieldDeclaredInPocoMustBeReferenced() {
        JsonObject out = hx.vex("SPY", SPY_AT);
        assertEquals("SPY", out.get("symbol").getAsString());
        assertEquals(SPY_AT, out.get("as_of").getAsString());
        JsonObject payload = out.getAsJsonObject("payload");
        assertNotNull("payload", payload);
        assertTrue("payload.net_vex", payload.get("net_vex").getAsJsonPrimitive().isNumber());
        assertTrue("payload.vex_interpretation", payload.get("vex_interpretation").getAsJsonPrimitive().isString());
        JsonArray strikes = payload.getAsJsonArray("strikes");
        assertTrue("payload.strikes non-empty", strikes.size() > 0);
        JsonObject row = strikes.get(0).getAsJsonObject();
        for (String k : new String[]{"strike", "call_vex", "put_vex", "net_vex"}) {
            assertTrue("strikes[0]." + k, row.get(k).getAsJsonPrimitive().isNumber());
        }
    }

    @Test
    public void testChex_EveryFieldDeclaredInPocoMustBeReferenced() {
        JsonObject out = hx.chex("SPY", SPY_AT);
        assertEquals("SPY", out.get("symbol").getAsString());
        assertEquals(SPY_AT, out.get("as_of").getAsString());
        JsonObject payload = out.getAsJsonObject("payload");
        assertNotNull("payload", payload);
        assertTrue("payload.net_chex", payload.get("net_chex").getAsJsonPrimitive().isNumber());
        assertTrue("payload.chex_interpretation", payload.get("chex_interpretation").getAsJsonPrimitive().isString());
        JsonArray strikes = payload.getAsJsonArray("strikes");
        assertTrue("payload.strikes non-empty", strikes.size() > 0);
        JsonObject row = strikes.get(0).getAsJsonObject();
        for (String k : new String[]{"strike", "call_chex", "put_chex", "net_chex"}) {
            assertTrue("strikes[0]." + k, row.get(k).getAsJsonPrimitive().isNumber());
        }
    }

    @Test
    public void testZeroDte_EveryFieldDeclaredInPocoMustBeReferenced() {
        // SPX has daily 0DTE; SPY is the canonical replay symbol. Use SPX
        // with the same SPY_AT minute so the typed walk always finds a 0DTE.
        ZeroDteResponse r = hx.zeroDteTyped("SPX", SPY_AT);
        assertNotNull(r);
        assertEquals("SPX", r.symbol);
        assertNotNull("as_of", r.asOf);

        if (Boolean.TRUE.equals(r.noZeroDte)) {
            // No-0DTE fallback path — only message + nextZeroDteExpiry are
            // populated. Typed leaves still exercised by deserialization.
            assertNotNull(r.nextZeroDteExpiry);
            return;
        }

        assertNotNull("underlying_price", r.underlyingPrice);
        assertNotNull("market_open", r.marketOpen);

        // regime
        assertNotNull("regime", r.regime);
        assertNotNull("regime.label", r.regime.label);
        assertNotNull("regime.gamma_flip", r.regime.gammaFlip);
        assertNotNull("regime.spot_vs_flip", r.regime.spotVsFlip);
        assertNotNull("regime.spot_to_flip_pct", r.regime.spotToFlipPct);
        assertNotNull("regime.distance_to_flip_dollars", r.regime.distanceToFlipDollars);
        assertNotNull("regime.distance_to_flip_sigmas", r.regime.distanceToFlipSigmas);

        // exposures
        assertNotNull("exposures", r.exposures);
        assertNotNull("exposures.net_gex", r.exposures.netGex);
        assertNotNull("exposures.net_dex", r.exposures.netDex);
        assertNotNull("exposures.net_vex", r.exposures.netVex);
        assertNotNull("exposures.net_chex", r.exposures.netChex);

        // expected_move
        assertNotNull("expected_move", r.expectedMove);
        assertNotNull("expected_move.implied_1sd_dollars", r.expectedMove.implied1SdDollars);
        assertNotNull("expected_move.implied_1sd_pct", r.expectedMove.implied1SdPct);
        assertNotNull("expected_move.upper_bound", r.expectedMove.upperBound);
        assertNotNull("expected_move.lower_bound", r.expectedMove.lowerBound);
        assertNotNull("expected_move.straddle_price", r.expectedMove.straddlePrice);
        assertNotNull("expected_move.atm_iv", r.expectedMove.atmIv);

        // pin_risk
        assertNotNull("pin_risk", r.pinRisk);
        assertNotNull("pin_risk.magnet_strike", r.pinRisk.magnetStrike);
        assertNotNull("pin_risk.components", r.pinRisk.components);
        assertNotNull("pin_risk.components.oi_score", r.pinRisk.components.oiScore);
        assertNotNull("pin_risk.components.proximity_score", r.pinRisk.components.proximityScore);
        assertNotNull("pin_risk.components.time_score", r.pinRisk.components.timeScore);
        assertNotNull("pin_risk.components.gamma_score", r.pinRisk.components.gammaScore);

        // hedging — all 8 buckets + convexity
        assertNotNull("hedging", r.hedging);
        ZeroDteResponse.HedgingBucket[] buckets = {
                r.hedging.spotUp10Bp, r.hedging.spotDown10Bp,
                r.hedging.spotUp25Bp, r.hedging.spotDown25Bp,
                r.hedging.spotUpHalfPct, r.hedging.spotDownHalfPct,
                r.hedging.spotUp1Pct, r.hedging.spotDown1Pct,
        };
        String[] bucketNames = {
                "spotUp10Bp", "spotDown10Bp", "spotUp25Bp", "spotDown25Bp",
                "spotUpHalfPct", "spotDownHalfPct", "spotUp1Pct", "spotDown1Pct",
        };
        for (int i = 0; i < buckets.length; i++) {
            assertNotNull("hedging." + bucketNames[i], buckets[i]);
            assertNotNull("hedging." + bucketNames[i] + ".dealer_shares_to_trade",
                    buckets[i].dealerSharesToTrade);
            assertNotNull("hedging." + bucketNames[i] + ".direction", buckets[i].direction);
            assertNotNull("hedging." + bucketNames[i] + ".notional_usd", buckets[i].notionalUsd);
        }

        // decay
        assertNotNull("decay", r.decay);
        assertNotNull("decay.net_theta_dollars", r.decay.netThetaDollars);
        assertNotNull("decay.charm_regime", r.decay.charmRegime);

        // vol_context
        assertNotNull("vol_context", r.volContext);
        assertNotNull("vol_context.zero_dte_atm_iv", r.volContext.zeroDteAtmIv);

        // levels
        assertNotNull("levels", r.levels);
        assertNotNull("levels.call_wall", r.levels.callWall);
        assertNotNull("levels.put_wall", r.levels.putWall);

        // strikes
        assertNotNull("strikes", r.strikes);
        if (!r.strikes.isEmpty()) {
            ZeroDteResponse.Strike s = r.strikes.get(0);
            assertNotNull("strikes[0].strike", s.strike);
            assertNotNull("strikes[0].net_gex", s.netGex);
        }
    }
}
