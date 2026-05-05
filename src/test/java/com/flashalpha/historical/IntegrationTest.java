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
        REGIMES.add("neutral");
        REGIMES.add("undetermined");
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
}
