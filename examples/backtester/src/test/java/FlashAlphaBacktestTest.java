import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Offline tests for FlashAlphaBacktest: a stub replaces the API, so no key or network is needed. Run: mvn -q test */
class FlashAlphaBacktestTest {

    /** Three sessions of canned API responses. The put spread opens on day 2 and hits the profit target on day 3. */
    static class StubData implements FlashAlphaBacktest.HistoricalData {
        final Set<String> days = Set.of("2024-03-04", "2024-03-05", "2024-03-06");  // all positive gamma, VRP 2.0
        // Marks per date for the two legs: {bid, ask}
        final Map<String, Map<String, double[]>> marks = Map.of(
                "2024-03-06", Map.of("P490.0", new double[] {0.30, 0.34}, "P485.0", new double[] {0.10, 0.12}));

        @Override
        public JsonObject stockQuote(String symbol, String at) {
            if (!days.contains(at.substring(0, 10))) {
                return json("{\"mid\": 500.0, \"lastUpdate\": \"2024-02-29T16:00:00\"}");  // holiday: an earlier quote
            }
            return json("{\"mid\": 500.0, \"lastUpdate\": \"" + at + "\"}");
        }

        @Override
        public JsonObject exposureSummary(String symbol, String at) {
            return json("{\"regime\": \"positive_gamma\", \"gamma_flip\": 495.0, \"exposures\": {\"net_gex\": 1e9}}");
        }

        @Override
        public JsonObject vrp(String symbol, String at) {
            return json("{\"vrp\": {\"vrp_20d\": 2.0, \"atm_iv\": 14.0, \"rv_20d\": 12.0, \"percentile\": 60,"
                    + " \"z_score\": 0.5}, \"gex_conditioned\": {\"harvest_score\": 70}}");
        }

        @Override
        public List<FlashAlphaBacktest.OptionQuote> chain(String symbol, String at, String expiry) {
            if (!expiry.equals("2024-04-05")) {
                return List.of();
            }
            return List.of(
                    quote("P", 490.0, expiry, 1.00, 1.10, -0.21),
                    quote("P", 485.0, expiry, 0.40, 0.46, -0.12),
                    quote("P", 480.0, expiry, 0.20, 0.24, -0.07),
                    quote("C", 510.0, expiry, 1.00, 1.10, 0.20));
        }

        @Override
        public FlashAlphaBacktest.OptionQuote option(String symbol, String at, String expiry, double strike, String type) {
            double[] bidAsk = marks.getOrDefault(at.substring(0, 10), Map.of()).get(type + strike);
            return bidAsk == null ? null : quote(type, strike, expiry, bidAsk[0], bidAsk[1], null);
        }
    }

    static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    static FlashAlphaBacktest.OptionQuote quote(String type, double strike, String expiry, double bid, double ask,
                                                Double delta) {
        FlashAlphaBacktest.OptionQuote q = new FlashAlphaBacktest.OptionQuote();
        q.type = type;
        q.strike = strike;
        q.expiry = expiry;
        q.bid = bid;
        q.ask = ask;
        q.mid = (bid + ask) / 2;
        q.delta = delta;
        return q;
    }

    /** trades.csv as one map per row (the stub's values hold no commas or quotes). */
    static List<Map<String, String>> readCsv(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        String[] header = lines.get(0).split(",");
        return lines.subList(1, lines.size()).stream().map(line -> {
            String[] cells = line.split(",", -1);
            Map<String, String> row = new HashMap<>();
            for (int i = 0; i < header.length; i++) {
                row.put(header[i], cells[i]);
            }
            return row;
        }).toList();
    }

    @Test
    void fillPriceMovesFromMidTowardTheFarSide() {
        FlashAlphaBacktest.OptionQuote q = quote("P", 500.0, "2024-04-05", 1.00, 1.20, null);
        assertEquals(1.10, FlashAlphaBacktest.fillPrice(q, -1, 0.0), 1e-9);
        assertEquals(1.00, FlashAlphaBacktest.fillPrice(q, -1, 1.0), 1e-9);
        assertEquals(1.15, FlashAlphaBacktest.fillPrice(q, +1, 0.5), 1e-9);
    }

    @Test
    void selectLegsBuildsAPutCreditSpread() {
        FlashAlphaBacktest.Config cfg = new FlashAlphaBacktest.Config();
        List<FlashAlphaBacktest.Leg> legs = FlashAlphaBacktest.selectLegs(
                new StubData().chain("SPY", "2024-03-05T15:45:00", "2024-04-05"), new FlashAlphaBacktest.Snapshot(), cfg);
        assertEquals(2, legs.size());
        assertEquals(490.0, legs.get(0).strike);
        assertEquals(-1, legs.get(0).side);
        assertEquals(485.0, legs.get(1).strike);
        assertEquals(+1, legs.get(1).side);
    }

    @Test
    void runOpensAndClosesOneTradeWithExactAccounting(@TempDir Path tmp) throws Exception {
        FlashAlphaBacktest.Config cfg = new FlashAlphaBacktest.Config();
        cfg.start = "2024-03-04";
        cfg.end = "2024-03-06";
        cfg.minDte = 25;
        cfg.maxDte = 40;
        cfg.targetDte = 31;
        Map<String, Object> summary = FlashAlphaBacktest.run(cfg, new StubData(), tmp);
        List<Map<String, String>> trades = readCsv(tmp.resolve("trades.csv"));
        assertEquals(1, summary.get("trades"));
        assertEquals(1, trades.size());
        Map<String, String> t = trades.get(0);
        // Entry on 03-05 (2 positive-gamma sessions, VRP > 0), halfway fills: sell 490 at 1.025, buy 485 at 0.445.
        // Credit = (1.025 - 0.445) * 100 - 2 legs * 0.65 = 56.70
        assertEquals("2024-03-05", t.get("entry_date"));
        assertEquals(56.70, Double.parseDouble(t.get("credit")), 1e-9);
        // Day 3 mid value of the spread: 0.32 - 0.11 = 0.21 -> open profit 56.70 - 21 = 35.70 >= 50% of credit.
        // Close halfway: buy 490 at 0.33, sell 485 at 0.105 -> -22.50, less 1.30 commission: 56.70 - 22.50 - 1.30 = 32.90
        assertEquals("profit_target", t.get("exit_reason"));
        assertEquals(32.90, Double.parseDouble(t.get("pnl")), 1e-9);
        assertEquals("-P490 +P485", t.get("legs"));
        assertEquals("2024-04-05", t.get("expiry"));
    }

    @Test
    void strikeTextKeepsEveryDigit() {
        assertEquals("512", FlashAlphaBacktest.strikeText(512.0));
        assertEquals("517.5", FlashAlphaBacktest.strikeText(517.5));
        assertEquals("21012.25", FlashAlphaBacktest.strikeText(21012.25));
    }

    @Test
    void holidaysAreSkipped(@TempDir Path tmp) throws Exception {
        FlashAlphaBacktest.Config cfg = new FlashAlphaBacktest.Config();
        cfg.start = "2024-03-01";  // 03-01 returns an earlier quote, 03-02/03 are a weekend
        cfg.end = "2024-03-04";
        Map<String, Object> summary = FlashAlphaBacktest.run(cfg, new StubData(), tmp);
        assertEquals(1, summary.get("sessions"));
    }
}
