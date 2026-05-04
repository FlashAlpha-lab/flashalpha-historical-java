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
        REGIMES.add("transition");
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
    public void exposureSummary_ShapeAndInvariants() {
        JsonObject s = hx.exposureSummary("SPY", SPY_AT);
        assertEquals("SPY", s.get("symbol").getAsString());
        assertTrue(Math.abs(s.get("underlying_price").getAsDouble() - EXPECTED_SPOT) < SPOT_TOL);
        assertTrue(REGIMES.contains(s.get("regime").getAsString()));
        assertTrue(s.get("gamma_flip").getAsJsonPrimitive().isNumber());
        JsonObject e = s.getAsJsonObject("exposures");
        for (String k : new String[] {"net_gex", "net_dex", "net_vex", "net_chex"}) {
            assertTrue(e.get(k).getAsJsonPrimitive().isNumber());
        }
        JsonObject h = s.getAsJsonObject("hedging_estimate");
        long up = h.getAsJsonObject("spot_up_1pct").get("dealer_shares_to_trade").getAsLong();
        long dn = h.getAsJsonObject("spot_down_1pct").get("dealer_shares_to_trade").getAsLong();
        assertEquals(up, -dn);
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
    public void vrp_DashboardKeysAndHySpreadHardcode() {
        JsonObject v = hx.vrp("SPY", SPY_AT);
        JsonObject core = v.getAsJsonObject("vrp");
        for (String k : new String[] {
                "atm_iv", "rv_5d", "rv_10d", "rv_20d", "rv_30d",
                "vrp_5d", "vrp_10d", "vrp_20d", "vrp_30d"}) {
            assertTrue(k, core.has(k));
        }
        assertEquals(3.5, v.getAsJsonObject("macro").get("hy_spread").getAsDouble(), 1e-9);
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
