import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * FlashAlpha options backtester: a complete, runnable template.
 *
 * <p>Replays the FlashAlpha Historical API day by day, decides entries and exits from the same endpoint outputs you
 * would read live, fills trades on real historical bid/ask quotes, and writes trades.csv and equity.csv.
 *
 * <p>Edit the three marked methods to change the strategy:
 * <pre>
 *     entryRules()  - when to open a position (reads FlashAlpha signals)
 *     selectLegs()  - which option contracts to trade (reads the option chain)
 *     exitRules()   - when to close (reads the position's marks and FlashAlpha signals)
 * </pre>
 *
 * <p>Requirements: Java 17+, Maven 3.6.3+, Gson (pulled in by pom.xml). An Alpha plan or higher (Historical API).
 * <pre>
 *     export FLASHALPHA_API_KEY=your_key
 *     mvn -q compile exec:java -Dexec.args="--symbol SPY --start 2024-01-02 --end 2024-06-28"
 * </pre>
 *
 * <p>Every API response is cached in .fa_cache/, so a rerun with changed rules costs no API calls for days already
 * fetched.
 */
public class FlashAlphaBacktest {

    static final String BASE_URL = "https://historical.flashalpha.com";

    // ============================================================================================================
    // Settings
    // ============================================================================================================
    /** Every public field is also a command-line flag: shortDelta is --short-delta, maxPositions is --max-positions. */
    public static class Config {
        public String symbol = "SPY";
        public String start = "2024-01-02";
        public String end = "2024-06-28";
        public String decisionTime = "15:45";  // ET; every decision and fill uses quotes at this minute
        // Trade selection (a put credit spread by default)
        public int targetDte = 35;             // pick the expiry closest to this many calendar days out...
        public int minDte = 25;                // ...within this window
        public int maxDte = 50;
        public double shortDelta = 0.20;       // sell the put closest to this absolute delta
        public double width = 5.0;             // buy the put this many points lower
        public int contracts = 1;
        public int maxPositions = 1;           // open positions allowed at once
        // Exits
        public double profitTarget = 0.50;     // close when 50% of the entry credit is captured
        public double stopLoss = 2.00;         // close when the loss reaches 2x the entry credit
        public int exitDte = 21;               // close with this many calendar days left
        // Costs
        public double slippage = 0.5;          // 0 = fill at mid, 1 = fill at the bid or ask, 0.5 = halfway
        public double commission = 0.65;       // per contract per leg, each side
    }

    // ============================================================================================================
    // FlashAlpha data: a small cached client for the Historical API
    // ============================================================================================================
    /** The endpoints the backtester reads. FlashAlphaData calls the API; tests plug in canned data. */
    public interface HistoricalData {
        JsonObject stockQuote(String symbol, String at) throws IOException, InterruptedException;

        JsonObject exposureSummary(String symbol, String at) throws IOException, InterruptedException;

        JsonObject vrp(String symbol, String at) throws IOException, InterruptedException;

        List<OptionQuote> chain(String symbol, String at, String expiry) throws IOException, InterruptedException;

        OptionQuote option(String symbol, String at, String expiry, double strike, String type)
                throws IOException, InterruptedException;
    }

    public static class FlashAlphaData implements HistoricalData {
        private static final Type CHAIN = new TypeToken<List<OptionQuote>>() { }.getType();

        private final String baseUrl;
        private final String apiKey;
        private final Path cache;
        private final HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(60))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        private final Gson gson = new Gson();
        public int calls = 0;

        public FlashAlphaData(String apiKey, String cacheDir, String baseUrl) throws IOException {
            if (apiKey == null || apiKey.isEmpty()) {
                throw new FatalError("Set FLASHALPHA_API_KEY (Alpha plan or higher).");
            }
            this.baseUrl = baseUrl.replaceAll("/+$", "");
            this.apiKey = apiKey;
            this.cache = Path.of(cacheDir);
            Files.createDirectories(cache);
        }

        /**
         * GET with a disk cache. Returns parsed JSON, or null when the API has no data (404).
         * Parameters are name/value pairs; a null value leaves the parameter out.
         */
        public JsonElement get(String path, String... nameValuePairs) throws IOException, InterruptedException {
            Map<String, String> params = new LinkedHashMap<>();
            for (int i = 0; i < nameValuePairs.length; i += 2) {
                if (nameValuePairs[i + 1] != null) {
                    params.put(nameValuePairs[i], nameValuePairs[i + 1]);
                }
            }
            Path hit = cache.resolve(cacheKey(path, params) + ".json");
            if (Files.exists(hit)) {
                return parse(Files.readString(hit));
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path + queryString(params)))
                    .header("X-Api-Key", apiKey)
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();
            String body = null;
            for (int attempt = 0; attempt < 5 && body == null; attempt++) {
                HttpResponse<String> r;
                try {
                    r = http.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (IOException e) {  // connection error or timeout: wait and retry (not counted as a call)
                    Thread.sleep((1L << attempt) * 1000);
                    continue;
                }
                calls++;
                int status = r.statusCode();
                if (status == 200) {
                    body = r.body();
                } else if (status == 404) {
                    body = "null";
                } else if (status == 429 || status == 500 || status == 502 || status == 503 || status == 504) {
                    Thread.sleep(retryAfterSeconds(r, attempt) * 1000L);
                } else {
                    String text = r.body();
                    throw new FatalError("FlashAlpha API error " + status + " on " + path + ": "
                            + text.substring(0, Math.min(200, text.length())));
                }
            }
            if (body == null) {
                throw new FatalError("FlashAlpha API kept failing on " + path);
            }
            JsonElement data = parse(body);  // parse first, so a bad response is never cached
            Files.writeString(hit, body, StandardCharsets.UTF_8);
            return data;
        }

        @Override
        public JsonObject stockQuote(String symbol, String at) throws IOException, InterruptedException {
            return asObject(get("/v1/stockquote/" + symbol, "at", at));
        }

        @Override
        public JsonObject exposureSummary(String symbol, String at) throws IOException, InterruptedException {
            return asObject(get("/v1/exposure/summary/" + symbol, "at", at));
        }

        @Override
        public JsonObject vrp(String symbol, String at) throws IOException, InterruptedException {
            return asObject(get("/v1/vrp/" + symbol, "at", at));
        }

        @Override
        public List<OptionQuote> chain(String symbol, String at, String expiry) throws IOException, InterruptedException {
            JsonElement data = get("/v1/optionquote/" + symbol, "at", at, "expiry", expiry);
            List<OptionQuote> chain = data == null ? null : gson.fromJson(data, CHAIN);
            return chain == null ? List.of() : chain;
        }

        @Override
        public OptionQuote option(String symbol, String at, String expiry, double strike, String type)
                throws IOException, InterruptedException {
            JsonElement data = get("/v1/optionquote/" + symbol,
                    "at", at, "expiry", expiry, "strike", strikeText(strike), "type", type);
            return data == null ? null : gson.fromJson(data, OptionQuote.class);
        }

        /** SHA-1 of the path and the sorted parameters (the same key the Python version uses). */
        private static String cacheKey(String path, Map<String, String> params) {
            StringJoiner pairs = new StringJoiner(", ", "[", "]");
            new TreeMap<>(params).forEach((k, v) -> pairs.add("[" + jsonString(k) + ", " + jsonString(v) + "]"));
            String key = "[" + jsonString(path) + ", " + pairs + "]";
            try {
                byte[] digest = MessageDigest.getInstance("SHA-1").digest(key.getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(digest);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        private static String queryString(Map<String, String> params) {
            StringJoiner query = new StringJoiner("&", "?", "");
            params.forEach((k, v) -> query.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "="
                    + URLEncoder.encode(v, StandardCharsets.UTF_8)));
            return params.isEmpty() ? "" : query.toString();
        }

        /** Seconds to wait before a retry: Retry-After when it is a whole number of seconds, else 1, 2, 4, 8, 16. */
        private static long retryAfterSeconds(HttpResponse<?> r, int attempt) {
            String wait = r.headers().firstValue("Retry-After").orElse("");
            return wait.matches("[0-9]+") ? Long.parseLong(wait) : 1L << attempt;
        }

        private static JsonElement parse(String text) {
            JsonElement data = JsonParser.parseString(text);
            return data.isJsonNull() ? null : data;
        }

        private static JsonObject asObject(JsonElement data) {
            return data == null ? null : data.getAsJsonObject();
        }
    }

    /** Strike as the API and the trade log write it: 512 -> "512", 517.5 -> "517.5", 21012.25 -> "21012.25". */
    static String strikeText(double strike) {
        return formatG(strike, 10);
    }

    /** One option quote from /v1/optionquote (a chain row, or the single contract asked for). Null = not sent. */
    public static class OptionQuote {
        public String type;            // "C" or "P"
        public String expiry;
        public Double strike;
        public Double bid;
        public Double ask;
        public Double mid;
        public Double bidSize;
        public Double askSize;
        public String lastUpdate;
        @SerializedName("implied_vol") public Double impliedVol;
        public Double delta;
        public Double gamma;
        public Double theta;
        public Double vega;
        public Double rho;
        public Double vanna;
        public Double charm;
        @SerializedName("open_interest") public Double openInterest;
        public Double volume;
    }

    /** Everything the rules can read for one decision time. A number is null when the API did not return it. */
    public static class Snapshot {
        public String date;
        public String at;
        public Double spot;
        // /v1/exposure/summary
        public String gammaRegime;     // "positive_gamma" or "negative_gamma"
        public Double netGex;
        public Double gammaFlip;
        // /v1/vrp
        public Double atmIv;           // vol points
        public Double rv20d;
        public Double vrp20d;          // implied minus 20-day realized, vol points
        public Double vrpPercentile;
        public Double vrpZ;
        public Double harvestScore;
    }

    /**
     * Everything the rules can read for one decision time, as one Snapshot. Returns null on a non-trading day.
     *
     * <p>Add fields from any other FlashAlpha endpoint here (exposure levels, narrative, max pain, 0DTE, surface...).
     */
    static Snapshot buildSnapshot(HistoricalData fa, String symbol, String at) throws IOException, InterruptedException {
        JsonObject quote = fa.stockQuote(symbol, at);
        String lastUpdate = text(quote, "lastUpdate");
        if (quote == null || quote.size() == 0 || lastUpdate == null || !lastUpdate.startsWith(at.substring(0, 10))) {
            return null;  // weekend or market holiday: the archive returned an earlier session's quote
        }
        JsonObject ex = fa.exposureSummary(symbol, at);
        JsonObject v = fa.vrp(symbol, at);
        JsonObject core = child(v, "vrp");
        Snapshot s = new Snapshot();
        s.date = at.substring(0, 10);
        s.at = at;
        s.spot = number(quote, "mid");
        // /v1/exposure/summary
        s.gammaRegime = text(ex, "regime");
        s.netGex = number(child(ex, "exposures"), "net_gex");
        s.gammaFlip = number(ex, "gamma_flip");
        // /v1/vrp
        s.atmIv = number(core, "atm_iv");
        s.rv20d = number(core, "rv_20d");
        s.vrp20d = number(core, "vrp_20d");
        s.vrpPercentile = number(core, "percentile");
        s.vrpZ = number(core, "z_score");
        s.harvestScore = number(child(v, "gex_conditioned"), "harvest_score");
        return s;
    }

    // ============================================================================================================
    // STRATEGY: edit these three methods
    // ============================================================================================================

    /**
     * ENTRY RULES. Return true to open a position at this decision time.
     *
     * <p>snap is today's snapshot (see buildSnapshot); history holds the earlier sessions' snapshots, oldest first.
     * Default: implied vol above 20-day realized (VRP &gt; 0) and dealers long gamma for 2 sessions running.
     */
    static boolean entryRules(Snapshot snap, List<Snapshot> history, Config cfg) {
        Snapshot previous = history.isEmpty() ? null : history.get(history.size() - 1);
        return snap.vrp20d != null && snap.vrp20d > 0
                && previous != null
                && "positive_gamma".equals(previous.gammaRegime)
                && "positive_gamma".equals(snap.gammaRegime);
    }

    /**
     * WHICH CONTRACTS. Return the legs to trade, each with side -1 = sell, +1 = buy; null to skip the trade.
     *
     * <p>Default: put credit spread - sell the put closest to cfg.shortDelta, buy the put cfg.width points lower.
     */
    static List<Leg> selectLegs(List<OptionQuote> chain, Snapshot snap, Config cfg) {
        List<OptionQuote> puts = new ArrayList<>();
        for (OptionQuote o : chain) {
            if ("P".equals(o.type) && o.delta != null && o.bid != null && o.bid > 0) {  // a null bid counts as 0
                puts.add(o);
            }
        }
        if (puts.isEmpty()) {
            return null;
        }
        OptionQuote shortPut = puts.get(0);  // absolute delta closest to cfg.shortDelta; the first one wins a tie
        for (OptionQuote o : puts) {
            if (Math.abs(Math.abs(o.delta) - cfg.shortDelta) < Math.abs(Math.abs(shortPut.delta) - cfg.shortDelta)) {
                shortPut = o;
            }
        }
        OptionQuote longPut = null;  // the highest strike at least cfg.width below the short strike
        for (OptionQuote o : puts) {
            if (o.strike <= shortPut.strike - cfg.width && (longPut == null || o.strike > longPut.strike)) {
                longPut = o;
            }
        }
        if (longPut == null) {
            return null;
        }
        return List.of(
                new Leg("P", shortPut.strike, shortPut.expiry, -1),
                new Leg("P", longPut.strike, longPut.expiry, +1));
    }

    /**
     * EXIT RULES. Return a reason string to close the position at this decision time, or null to hold.
     *
     * <p>pos.pnlMid is the open profit of the whole position in dollars at mid. pos.credit is the net premium taken
     * in, after commission: positive for a credit, negative for a debit, so the targets below use its size.
     * Default: take 50% of the credit, stop at 2x the credit, close at 21 days left, or close after 2 sessions
     * of negative dealer gamma.
     */
    static String exitRules(Position pos, Snapshot snap, List<Snapshot> history, Config cfg) {
        if (pos.pnlMid >= cfg.profitTarget * Math.abs(pos.credit)) {
            return "profit_target";
        }
        if (pos.pnlMid <= -cfg.stopLoss * Math.abs(pos.credit)) {
            return "stop_loss";
        }
        if (pos.dte(snap.date) <= cfg.exitDte) {
            return "time_exit";
        }
        Snapshot previous = history.isEmpty() ? null : history.get(history.size() - 1);
        if (previous != null && "negative_gamma".equals(previous.gammaRegime)
                && "negative_gamma".equals(snap.gammaRegime)) {
            return "negative_gamma_2_sessions";
        }
        return null;
    }

    // ============================================================================================================
    // Engine (no changes needed below to run a new strategy)
    // ============================================================================================================

    /** One option leg: type "C" or "P", side -1 = sell, +1 = buy. */
    public static class Leg {
        public final String type;
        public final double strike;
        public final String expiry;
        public final int side;

        public Leg(String type, double strike, String expiry, int side) {
            this.type = type;
            this.strike = strike;
            this.expiry = expiry;
            this.side = side;
        }

        @Override
        public String toString() {
            return "{type=" + type + ", strike=" + strikeText(strike) + ", expiry=" + expiry + ", side=" + side + "}";
        }
    }

    public static class Position {
        public final List<Leg> legs;
        public final String entryDate;
        public final double credit;  // net premium for the whole position in dollars after commission (negative = debit)
        public final Map<Integer, OptionQuote> marks; // last quote per leg
        public double pnlMid = 0.0;

        Position(List<Leg> legs, String entryDate, double credit, Map<Integer, OptionQuote> marks) {
            this.legs = legs;
            this.entryDate = entryDate;
            this.credit = credit;
            this.marks = marks;
        }

        public String expiry() {
            return legs.stream().map(leg -> leg.expiry).min(Comparator.naturalOrder()).orElseThrow();
        }

        public long dte(String date) {
            return ChronoUnit.DAYS.between(LocalDate.parse(date), LocalDate.parse(expiry()));
        }
    }

    /** Price for buying (side +1) or selling (side -1) one contract: mid moved toward the ask or bid. */
    static double fillPrice(OptionQuote quote, int side, double slippage) {
        double mid = (quote.bid + quote.ask) / 2;
        return side > 0 ? mid + slippage * (quote.ask - mid) : mid - slippage * (mid - quote.bid);
    }

    record ExpiryChain(String expiry, List<OptionQuote> chain) { }

    record ContractKey(String type, Double strike, String expiry) { }

    /** Expiry closest to cfg.targetDte inside [minDte, maxDte] that has a chain (Friday, else Thursday). */
    static ExpiryChain findExpiry(HistoricalData fa, String symbol, String at, Config cfg)
            throws IOException, InterruptedException {
        LocalDate today = LocalDate.parse(at.substring(0, 10));
        List<LocalDate> fridays = new ArrayList<>();
        for (int d = cfg.minDte; d <= cfg.maxDte; d++) {
            if (today.plusDays(d).getDayOfWeek() == DayOfWeek.FRIDAY) {
                fridays.add(today.plusDays(d));
            }
        }
        fridays.sort(Comparator.comparingLong(f -> Math.abs(ChronoUnit.DAYS.between(today, f) - cfg.targetDte)));
        for (LocalDate friday : fridays) {
            for (LocalDate day : List.of(friday, friday.minusDays(1))) {  // Thursday when the Friday is a holiday
                List<OptionQuote> chain = fa.chain(symbol, at, day.toString());
                if (!chain.isEmpty()) {
                    return new ExpiryChain(day.toString(), chain);
                }
            }
        }
        return null;
    }

    static List<String> tradingDays(String start, String end) {
        List<String> days = new ArrayList<>();
        for (LocalDate day = LocalDate.parse(start); !day.isAfter(LocalDate.parse(end)); day = day.plusDays(1)) {
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                days.add(day.toString());
            }
        }
        return days;
    }

    static Map<String, Object> run(Config cfg, HistoricalData fa, Path outDir) throws IOException, InterruptedException {
        Files.createDirectories(outDir);
        List<Position> openPositions = new ArrayList<>();
        List<Map<String, Object>> trades = new ArrayList<>();
        List<Map<String, Object>> equity = new ArrayList<>();
        List<Snapshot> history = new ArrayList<>();
        double realized = 0.0;
        for (String date : tradingDays(cfg.start, cfg.end)) {
            String at = date + "T" + cfg.decisionTime + ":00";
            Snapshot snap = buildSnapshot(fa, cfg.symbol, at);
            if (snap == null) {
                continue;
            }

            // 1. Mark and exit open positions
            for (Position pos : new ArrayList<>(openPositions)) {
                double valueMid = 0.0;
                boolean onExpiry = pos.dte(date) <= 0;  // expiry day, or the first session after it when that day had no data
                for (int i = 0; i < pos.legs.size(); i++) {
                    Leg leg = pos.legs.get(i);
                    OptionQuote q = onExpiry ? null : fa.option(cfg.symbol, at, leg.expiry, leg.strike, leg.type);
                    if (q != null && q.bid != null && q.ask != null && q.ask > 0) {
                        pos.marks.put(i, q);
                    }
                    double mid = (pos.marks.get(i).bid + pos.marks.get(i).ask) / 2;
                    valueMid += leg.side * mid * 100 * cfg.contracts;
                }
                pos.pnlMid = pos.credit + valueMid;  // credit received plus the (negative) value of what we hold
                String reason = onExpiry ? "expiry" : exitRules(pos, snap, history, cfg);
                if (reason != null) {
                    Sum closeValue = new Sum();
                    for (int i = 0; i < pos.legs.size(); i++) {
                        Leg leg = pos.legs.get(i);
                        if (onExpiry) {  // settle at intrinsic value against the spot at the decision time
                            double intrinsic = leg.type.equals("P") ? leg.strike - snap.spot : snap.spot - leg.strike;
                            closeValue.add(leg.side * Math.max(0.0, intrinsic));
                        } else {
                            closeValue.add(leg.side * fillPrice(pos.marks.get(i), -leg.side, cfg.slippage));
                        }
                    }
                    double costs = onExpiry ? 0.0 : cfg.commission * pos.legs.size() * cfg.contracts;
                    double pnl = pos.credit + closeValue.value() * 100 * cfg.contracts - costs;
                    realized += pnl;
                    openPositions.remove(pos);
                    StringJoiner legsText = new StringJoiner(" ");
                    for (Leg leg : pos.legs) {
                        legsText.add((leg.side > 0 ? "+" : "-") + leg.type + strikeText(leg.strike));
                    }
                    Map<String, Object> trade = new LinkedHashMap<>();
                    trade.put("entry_date", pos.entryDate);
                    trade.put("exit_date", date);
                    trade.put("expiry", pos.expiry());
                    trade.put("legs", legsText.toString());
                    trade.put("credit", round(pos.credit, 2));
                    trade.put("pnl", round(pnl, 2));
                    trade.put("exit_reason", reason);
                    trade.put("days_held", ChronoUnit.DAYS.between(LocalDate.parse(pos.entryDate), LocalDate.parse(date)));
                    trades.add(trade);
                }
            }

            // 2. Entries
            if (openPositions.size() < cfg.maxPositions && entryRules(snap, history, cfg)) {
                ExpiryChain found = findExpiry(fa, cfg.symbol, at, cfg);
                List<Leg> legs = found != null ? selectLegs(found.chain(), snap, cfg) : null;
                if (legs != null && !legs.isEmpty()) {
                    Map<ContractKey, OptionQuote> byKey = new HashMap<>();
                    for (OptionQuote o : found.chain()) {
                        byKey.put(new ContractKey(o.type, o.strike, o.expiry), o);
                    }
                    List<Leg> missing = new ArrayList<>();
                    for (Leg leg : legs) {
                        if (!byKey.containsKey(new ContractKey(leg.type, leg.strike, leg.expiry))) {
                            missing.add(leg);
                        }
                    }
                    if (!missing.isEmpty()) {
                        throw new IllegalStateException("selectLegs must pick contracts from the chain it receives: " + missing);
                    }
                    Map<Integer, OptionQuote> quotes = new HashMap<>();
                    Sum openValue = new Sum();
                    Sum valueMid = new Sum();
                    for (int i = 0; i < legs.size(); i++) {
                        Leg leg = legs.get(i);
                        OptionQuote q = byKey.get(new ContractKey(leg.type, leg.strike, leg.expiry));
                        quotes.put(i, q);
                        openValue.add(leg.side * fillPrice(q, leg.side, cfg.slippage));
                        valueMid.add(leg.side * (q.bid + q.ask) / 2);
                    }
                    double credit = -openValue.value() * 100 * cfg.contracts - cfg.commission * legs.size() * cfg.contracts;
                    Position pos = new Position(legs, date, credit, quotes);
                    pos.pnlMid = credit + valueMid.value() * 100 * cfg.contracts;
                    openPositions.add(pos);
                }
            }

            Sum unrealized = new Sum();
            for (Position p : openPositions) {
                unrealized.add(p.pnlMid);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", date);
            row.put("realized", round(realized, 2));
            row.put("unrealized", round(unrealized.value(), 2));
            row.put("equity", round(realized + unrealized.value(), 2));
            row.put("open_positions", openPositions.size());
            row.put("gamma_regime", snap.gammaRegime);
            row.put("vrp_20d", asSent(snap.vrp20d));
            equity.add(row);
            history.add(snap);
        }

        writeCsv(outDir.resolve("trades.csv"), trades);
        writeCsv(outDir.resolve("equity.csv"), equity);
        return summarize(trades, equity);
    }

    static void writeCsv(Path path, List<Map<String, Object>> rows) throws IOException {
        if (rows.isEmpty()) {
            Files.writeString(path, "");
            return;
        }
        List<String> columns = new ArrayList<>(rows.get(0).keySet());
        StringBuilder csv = new StringBuilder(String.join(",", columns)).append("\r\n");
        for (Map<String, Object> row : rows) {
            StringJoiner line = new StringJoiner(",");
            for (String column : columns) {
                line.add(csvField(row.get(column)));
            }
            csv.append(line).append("\r\n");
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }

    static Map<String, Object> summarize(List<Map<String, Object>> trades, List<Map<String, Object>> equity) {
        List<Double> pnls = new ArrayList<>();
        for (Map<String, Object> t : trades) {
            pnls.add((Double) t.get("pnl"));
        }
        List<Double> daily = new ArrayList<>();
        double previous = 0.0;
        double peak = 0.0;
        double maxDrawdown = 0.0;
        for (Map<String, Object> e : equity) {
            double value = (Double) e.get("equity");
            daily.add(value - previous);
            previous = value;
            peak = Math.max(peak, value);
            maxDrawdown = Math.max(maxDrawdown, peak - value);
        }
        double sd = 0;
        double meanDaily = daily.isEmpty() ? 0 : sum(daily) / daily.size();
        if (daily.size() > 1) {
            Sum squares = new Sum();
            for (double x : daily) {
                squares.add((x - meanDaily) * (x - meanDaily));
            }
            sd = Math.sqrt(squares.value() / (daily.size() - 1));
        }
        List<Double> wins = new ArrayList<>();
        List<Double> losses = new ArrayList<>();
        for (double p : pnls) {
            (p > 0 ? wins : losses).add(p);  // break-even trades count as losses
        }
        Map<String, Integer> reasons = new LinkedHashMap<>();
        for (Map<String, Object> t : trades) {
            reasons.merge((String) t.get("exit_reason"), 1, Integer::sum);
        }
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("sessions", equity.size());
        s.put("trades", trades.size());
        s.put("win_rate", pnls.isEmpty() ? null : round((double) wins.size() / pnls.size(), 3));
        s.put("total_pnl", round(sum(pnls), 2));  // 0.0 when there are no trades
        s.put("avg_win", wins.isEmpty() ? null : round(sum(wins) / wins.size(), 2));
        s.put("avg_loss", losses.isEmpty() ? null : round(sum(losses) / losses.size(), 2));
        s.put("max_drawdown", round(maxDrawdown, 2));
        s.put("sharpe", sd > 0 ? round(meanDaily / sd * Math.sqrt(252), 2) : null);
        s.put("exit_reasons", reasons);
        s.put("open_at_end", equity.isEmpty() ? 0 : equity.get(equity.size() - 1).get("open_positions"));
        return s;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        try {
            Config cfg = new Config();
            Map<String, String> paths = new LinkedHashMap<>();
            paths.put("--out", "results");
            paths.put("--cache", ".fa_cache");
            parseArgs(args, cfg, paths);
            Path out = Path.of(paths.get("--out"));
            FlashAlphaData fa = new FlashAlphaData(System.getenv("FLASHALPHA_API_KEY"), paths.get("--cache"), BASE_URL);
            Map<String, Object> summary = run(cfg, fa, out);
            System.out.println(toJson(summary, ""));
            System.out.println("API calls this run: " + fa.calls + ". Trades: " + out.resolve("trades.csv")
                    + ". Equity: " + out.resolve("equity.csv"));
        } catch (FatalError e) {
            System.err.println(e.getMessage());
            System.exit(e.exitCode);
        }
    }

    // ============================================================================================================
    // Plumbing: command line, JSON field access, and number formatting that matches the Python version
    // ============================================================================================================

    /** A problem that ends the run with a message and no stack trace. */
    static class FatalError extends RuntimeException {
        final int exitCode;

        FatalError(String message) {
            this(message, 1);
        }

        FatalError(String message, int exitCode) {
            super(message);
            this.exitCode = exitCode;
        }
    }

    /** Reads --flag value (or --flag=value) into the Config fields and the --out/--cache paths. */
    static void parseArgs(String[] args, Config cfg, Map<String, String> paths) {
        Map<String, Field> flags = new LinkedHashMap<>();
        for (Field f : Config.class.getFields()) {
            flags.put("--" + f.getName().replaceAll("([A-Z])", "-$1").toLowerCase(Locale.ROOT), f);
        }
        for (int i = 0; i < args.length; i++) {
            String name = args[i];
            String value = null;
            if (name.equals("-h") || name.equals("--help")) {
                StringBuilder usage = new StringBuilder("FlashAlpha options backtester. Flags (default):\n");
                try {
                    for (Map.Entry<String, Field> flag : flags.entrySet()) {
                        usage.append("  ").append(flag.getKey()).append(" (").append(flag.getValue().get(cfg)).append(")\n");
                    }
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
                paths.forEach((flag, path) -> usage.append("  ").append(flag).append(" (").append(path).append(")\n"));
                System.out.print(usage);
                System.exit(0);
            }
            if (name.contains("=")) {
                value = name.substring(name.indexOf('=') + 1);
                name = name.substring(0, name.indexOf('='));
            } else if (i + 1 < args.length) {
                value = args[++i];
            }
            if (!flags.containsKey(name) && !paths.containsKey(name)) {
                throw new FatalError("unrecognized argument: " + name + " (see --help)", 2);
            }
            if (value == null) {
                throw new FatalError("argument " + name + ": expected one argument", 2);
            }
            if (paths.containsKey(name)) {
                paths.put(name, value);
                continue;
            }
            Field field = flags.get(name);
            try {
                if (field.getType() == int.class) {
                    field.setInt(cfg, Integer.parseInt(value.trim()));
                } else if (field.getType() == double.class) {
                    field.setDouble(cfg, Double.parseDouble(value.trim()));
                } else {
                    field.set(cfg, value);
                }
            } catch (NumberFormatException e) {
                throw new FatalError("argument " + name + ": invalid " + field.getType() + " value: '" + value + "'", 2);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** The JSON object under key, or null when it is missing, null or not an object. */
    static JsonObject child(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    /** The number under key, or null when it is missing or null. */
    static Double number(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : null;
    }

    /** The string under key, or null when it is missing or null. */
    static String text(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    /** Python's round(x, places): round half to even on the exact binary value, keeping the sign of zero. */
    static double round(double x, int places) {
        if (!Double.isFinite(x)) {
            return x;
        }
        double r = new BigDecimal(x).setScale(places, RoundingMode.HALF_EVEN).doubleValue();
        return r == 0 ? Math.copySign(0.0, x) : r;
    }

    static double sum(List<Double> values) {
        Sum s = new Sum();
        for (double v : values) {
            s.add(v);
        }
        return s.value();
    }

    /** Adds doubles the way Python 3.12+ sum() does (Neumaier compensated summation), so totals match bit for bit. */
    static final class Sum {
        private double total = 0.0;
        private double compensation = 0.0;

        void add(double x) {
            double t = total + x;
            compensation += Math.abs(total) >= Math.abs(x) ? (total - t) + x : (x - t) + total;
            total = t;
        }

        double value() {
            return compensation != 0 && Double.isFinite(compensation) ? total + compensation : total;
        }
    }

    /** Whole numbers arrive from the API without a decimal point (2, not 2.0); keep them that way in the CSV. */
    static Object asSent(Double x) {
        return x != null && x == Math.rint(x) && Math.abs(x) < 1e15 ? (Object) x.longValue() : x;
    }

    /** Python's str(float), e.g. 50.7, 0.0, 1e-05: the shortest digits that read back as the same double. */
    static String repr(double x) {
        if (Double.isNaN(x)) {
            return "nan";
        }
        if (Double.isInfinite(x)) {
            return x > 0 ? "inf" : "-inf";
        }
        if (x == 0) {
            return 1 / x < 0 ? "-0.0" : "0.0";
        }
        BigDecimal d = new BigDecimal(Double.toString(x)).stripTrailingZeros();
        int exponent = d.precision() - d.scale() - 1;
        if (exponent < -4 || exponent >= 16) {
            return scientific(d, exponent);
        }
        String plain = d.toPlainString();
        return plain.contains(".") ? plain : plain + ".0";
    }

    /** Python's format(x, ".<precision>g"): that many significant digits, no trailing zeros (512.0 -> "512"). */
    static String formatG(double x, int precision) {
        if (x == 0 || !Double.isFinite(x)) {
            return x == 0 ? (1 / x < 0 ? "-0" : "0") : repr(x);
        }
        BigDecimal d = new BigDecimal(x).round(new MathContext(precision, RoundingMode.HALF_EVEN)).stripTrailingZeros();
        int exponent = d.precision() - d.scale() - 1;
        return exponent < -4 || exponent >= precision ? scientific(d, exponent) : d.toPlainString();
    }

    private static String scientific(BigDecimal d, int exponent) {
        String digits = d.unscaledValue().abs().toString();
        String mantissa = digits.length() == 1 ? digits : digits.charAt(0) + "." + digits.substring(1);
        return (d.signum() < 0 ? "-" : "") + mantissa + "e" + (exponent < 0 ? "-" : "+")
                + (Math.abs(exponent) < 10 ? "0" : "") + Math.abs(exponent);
    }

    /** One CSV field as Python's csv module writes it (None is empty, quotes only when needed). */
    static String csvField(Object value) {
        String s = value == null ? "" : value instanceof Double ? repr((Double) value) : value.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\r") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /** JSON text with 2-space indents, the same layout as Python's json.dumps(value, indent=2). */
    static String toJson(Object value, String indent) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Double) {
            return repr((Double) value);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            if (map.isEmpty()) {
                return "{}";
            }
            String inner = indent + "  ";
            StringJoiner entries = new StringJoiner(",\n", "{\n", "\n" + indent + "}");
            map.forEach((k, v) -> entries.add(inner + jsonString(k.toString()) + ": " + toJson(v, inner)));
            return entries.toString();
        }
        return jsonString(value.toString());
    }

    /** A JSON string literal, escaped like Python's json.dumps (non-ASCII as backslash-u escapes). */
    static String jsonString(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                default:
                    if (c < 0x20 || c > 0x7e) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.append('"').toString();
    }
}
