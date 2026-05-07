package com.flashalpha.historical;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Java client for the FlashAlpha Historical API — point-in-time replay of
 * every live FlashAlpha analytics endpoint.
 *
 * <pre>{@code
 * FlashAlphaHistoricalClient hx = new FlashAlphaHistoricalClient(System.getenv("FLASHALPHA_API_KEY"));
 * JsonObject snap = hx.exposureSummary("SPY", "2020-03-16T15:30:00");
 * System.out.println(snap.get("regime").getAsString()); // "negative_gamma"
 * }</pre>
 *
 * <p>Every analytics method takes a required {@code at} parameter — string,
 * {@link LocalDateTime}, or {@link LocalDate}. ET wall-clock convention; do
 * not shift by UTC offset.
 */
public class FlashAlphaHistoricalClient {

    public static final String DEFAULT_BASE_URL = "https://historical.flashalpha.com";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final DateTimeFormatter MINUTE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient http;
    private final Duration timeout;
    private final Gson gson = new Gson();

    public FlashAlphaHistoricalClient(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL);
    }

    public FlashAlphaHistoricalClient(String apiKey, String baseUrl) {
        this(apiKey, baseUrl, DEFAULT_TIMEOUT);
    }

    public FlashAlphaHistoricalClient(String apiKey, String baseUrl, Duration timeout) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("apiKey must not be null or empty");
        }
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    /** Format a {@link LocalDateTime} as the ET wall-clock minute string. */
    public static String formatAt(LocalDateTime at) { return at.format(MINUTE_FMT); }

    /** Format a {@link LocalDate} as a date string (the API defaults to 16:00 ET). */
    public static String formatAt(LocalDate at) { return at.format(DATE_FMT); }

    // ── Internal HTTP helpers ─────────────────────────────────────────

    private static String seg(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private JsonObject get(String path) { return get(path, null); }

    private JsonObject get(String path, Map<String, String> params) {
        String url = baseUrl + path;
        if (params != null && !params.isEmpty()) url = url + "?" + buildQuery(params);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Api-Key", apiKey)
                .header("Accept", "application/json")
                .GET()
                .timeout(timeout)
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FlashAlphaHistoricalException("HTTP request failed: " + e.getMessage(), 0, null);
        }
        return handleResponse(response);
    }

    private JsonObject handleResponse(HttpResponse<String> response) {
        int status = response.statusCode();
        String body = response.body();

        if (status == 200) {
            return JsonParser.parseString(body).getAsJsonObject();
        }

        JsonObject jsonBody = null;
        try { jsonBody = JsonParser.parseString(body).getAsJsonObject(); } catch (Exception ignored) {}

        String code = jsonBody != null && jsonBody.has("error") && !jsonBody.get("error").isJsonNull()
                ? jsonBody.get("error").getAsString() : null;
        String message = extractMessage(jsonBody, body);

        switch (status) {
            case 400:
                if ("invalid_at".equals(code)) throw new InvalidAtException(message, jsonBody);
                throw new FlashAlphaHistoricalException(message, 400, jsonBody);
            case 401:
                throw new AuthenticationException(message, jsonBody);
            case 403: {
                String currentPlan = stringField(jsonBody, "current_plan");
                String requiredPlan = stringField(jsonBody, "required_plan");
                throw new TierRestrictedException(message, jsonBody, currentPlan, requiredPlan);
            }
            case 404:
                if ("no_coverage".equals(code)) throw new NoCoverageException(message, jsonBody);
                if ("symbol_not_found".equals(code)) throw new SymbolNotFoundException(message, jsonBody);
                if ("insufficient_data".equals(code)) throw new InsufficientDataException(message, jsonBody);
                throw new NoDataException(message, jsonBody);
            case 429: {
                Integer retryAfter = null;
                String h = response.headers().firstValue("Retry-After").orElse(null);
                if (h != null) try { retryAfter = Integer.parseInt(h); } catch (NumberFormatException ignored) {}
                throw new RateLimitException(message, jsonBody, retryAfter);
            }
            default:
                if (status >= 500) throw new ServerException(message, status, jsonBody);
                throw new FlashAlphaHistoricalException(message, status, jsonBody);
        }
    }

    private static String stringField(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return null;
        JsonElement v = obj.get(key);
        return v.isJsonNull() ? null : v.getAsString();
    }

    private static String extractMessage(JsonObject body, String rawText) {
        if (body != null) {
            if (body.has("message") && !body.get("message").isJsonNull()) return body.get("message").getAsString();
            if (body.has("detail")  && !body.get("detail").isJsonNull())  return body.get("detail").getAsString();
        }
        return rawText != null ? rawText : "Unknown error";
    }

    private static String buildQuery(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)).append('=');
            sb.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static Map<String, String> atMap(String at) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("at", at);
        return p;
    }

    // ── Coverage ──────────────────────────────────────────────────────

    /** Lists every symbol with historical coverage. Pass {@code null} for the full table. */
    public JsonObject tickers(String symbol) {
        Map<String, String> p = new LinkedHashMap<>();
        if (symbol != null) p.put("symbol", symbol);
        return get("/v1/tickers", p.isEmpty() ? null : p);
    }

    public JsonObject tickers() { return tickers(null); }

    // ── Market Data ───────────────────────────────────────────────────

    /** Stock bid/ask/mid/last at the requested minute. */
    public JsonObject stockQuote(String ticker, String at) {
        return get("/v1/stockquote/" + seg(ticker), atMap(at));
    }

    public JsonObject stockQuote(String ticker, LocalDateTime at) { return stockQuote(ticker, formatAt(at)); }
    public JsonObject stockQuote(String ticker, LocalDate at)     { return stockQuote(ticker, formatAt(at)); }

    /**
     * Option quote(s) + greeks + OI at the requested minute. Pass any of
     * {@code expiry}/{@code strike}/{@code type} as {@code null} to omit.
     * With all three filters, the response is a single object instead of an array.
     */
    public JsonObject optionQuote(String ticker, String at, String expiry, Double strike, String type) {
        Map<String, String> p = atMap(at);
        if (expiry != null) p.put("expiry", expiry);
        if (strike != null) p.put("strike", String.valueOf(strike));
        if (type   != null) p.put("type", type);
        return get("/v1/optionquote/" + seg(ticker), p);
    }

    public JsonObject optionQuote(String ticker, String at) { return optionQuote(ticker, at, null, null, null); }

    /** 50×50 IV surface grid. Throws {@link InsufficientDataException} on sparse historical days. */
    public JsonObject surface(String symbol, String at) {
        return get("/v1/surface/" + seg(symbol), atMap(at));
    }

    public JsonObject surface(String symbol, LocalDateTime at) { return surface(symbol, formatAt(at)); }

    // ── Exposure ──────────────────────────────────────────────────────

    /** Gamma exposure by strike. */
    public JsonObject gex(String symbol, String at, String expiration, Integer minOi) {
        Map<String, String> p = atMap(at);
        if (expiration != null) p.put("expiration", expiration);
        if (minOi      != null) p.put("min_oi", minOi.toString());
        return get("/v1/exposure/gex/" + seg(symbol), p);
    }

    public JsonObject gex(String symbol, String at) { return gex(symbol, at, null, null); }

    /** Delta exposure by strike. */
    public JsonObject dex(String symbol, String at, String expiration) {
        Map<String, String> p = atMap(at);
        if (expiration != null) p.put("expiration", expiration);
        return get("/v1/exposure/dex/" + seg(symbol), p);
    }

    public JsonObject dex(String symbol, String at) { return dex(symbol, at, null); }

    /** Vanna exposure by strike. */
    public JsonObject vex(String symbol, String at, String expiration) {
        Map<String, String> p = atMap(at);
        if (expiration != null) p.put("expiration", expiration);
        return get("/v1/exposure/vex/" + seg(symbol), p);
    }

    public JsonObject vex(String symbol, String at) { return vex(symbol, at, null); }

    /** Charm exposure by strike. */
    public JsonObject chex(String symbol, String at, String expiration) {
        Map<String, String> p = atMap(at);
        if (expiration != null) p.put("expiration", expiration);
        return get("/v1/exposure/chex/" + seg(symbol), p);
    }

    public JsonObject chex(String symbol, String at) { return chex(symbol, at, null); }

    /** Full composite exposure dashboard. */
    public JsonObject exposureSummary(String symbol, String at) {
        return get("/v1/exposure/summary/" + seg(symbol), atMap(at));
    }

    public JsonObject exposureSummary(String symbol, LocalDateTime at) { return exposureSummary(symbol, formatAt(at)); }
    public JsonObject exposureSummary(String symbol, LocalDate at)     { return exposureSummary(symbol, formatAt(at)); }

    /**
     * Strongly-typed variant of {@link #exposureSummary(String, String)}. Returns
     * a populated {@link ExposureSummaryResponse} with named fields. The original
     * untyped method is unchanged.
     *
     * @param symbol Underlying symbol.
     * @param at     ET wall-clock minute string, e.g. {@code "2020-03-16T15:30:00"}.
     */
    public ExposureSummaryResponse exposureSummaryTyped(String symbol, String at) {
        JsonObject raw = exposureSummary(symbol, at);
        return gson.fromJson(raw, ExposureSummaryResponse.class);
    }

    /**
     * Strongly-typed variant of {@link #exposureSummary(String, LocalDateTime)}.
     *
     * @param symbol Underlying symbol.
     * @param at     ET wall-clock minute as a {@link LocalDateTime}.
     */
    public ExposureSummaryResponse exposureSummaryTyped(String symbol, LocalDateTime at) {
        return exposureSummaryTyped(symbol, formatAt(at));
    }

    /**
     * Strongly-typed variant of {@link #exposureSummary(String, LocalDate)}.
     *
     * @param symbol Underlying symbol.
     * @param at     ET date (the API defaults to 16:00 ET).
     */
    public ExposureSummaryResponse exposureSummaryTyped(String symbol, LocalDate at) {
        return exposureSummaryTyped(symbol, formatAt(at));
    }

    /** Key technical levels — gamma flip, walls, max +/- gamma, highest-OI, 0DTE magnet. */
    public JsonObject exposureLevels(String symbol, String at) {
        return get("/v1/exposure/levels/" + seg(symbol), atMap(at));
    }

    /**
     * Strongly-typed variant of {@link #exposureLevels(String, String)}. Returns
     * a populated {@link ExposureLevelsResponse}. The original untyped method
     * is unchanged.
     *
     * @param symbol Underlying symbol.
     * @param at     ET wall-clock minute string.
     */
    public ExposureLevelsResponse exposureLevelsTyped(String symbol, String at) {
        JsonObject raw = exposureLevels(symbol, at);
        return gson.fromJson(raw, ExposureLevelsResponse.class);
    }

    /** Verbal narrative analysis + prior-day GEX comparison + VIX context. */
    public JsonObject narrative(String symbol, String at) {
        return get("/v1/exposure/narrative/" + seg(symbol), atMap(at));
    }

    /**
     * Strongly-typed variant of {@link #narrative(String, String)}. Returns a
     * populated {@link NarrativeResponse}. The original untyped method is
     * unchanged.
     *
     * @param symbol Underlying symbol.
     * @param at     ET wall-clock minute string.
     */
    public NarrativeResponse narrativeTyped(String symbol, String at) {
        JsonObject raw = narrative(symbol, at);
        return gson.fromJson(raw, NarrativeResponse.class);
    }

    /** 0DTE-specific analytics — regime, expected move, pin risk, hedging, decay, flow. */
    public JsonObject zeroDte(String symbol, String at, Double strikeRange) {
        Map<String, String> p = atMap(at);
        if (strikeRange != null) p.put("strike_range", String.valueOf(strikeRange));
        return get("/v1/exposure/zero-dte/" + seg(symbol), p);
    }

    public JsonObject zeroDte(String symbol, String at) { return zeroDte(symbol, at, null); }

    // ── Max Pain ──────────────────────────────────────────────────────

    /** Strike-by-strike pain curve, OI breakdown, dealer alignment, pin probability. */
    public JsonObject maxPain(String symbol, String at, String expiration) {
        Map<String, String> p = atMap(at);
        if (expiration != null) p.put("expiration", expiration);
        return get("/v1/maxpain/" + seg(symbol), p);
    }

    public JsonObject maxPain(String symbol, String at) { return maxPain(symbol, at, null); }

    /**
     * Strongly-typed variant of {@link #maxPain(String, String, String)}.
     * Returns a populated {@link MaxPainResponse}. The original untyped method
     * is unchanged.
     *
     * @param symbol     Underlying symbol.
     * @param at         ET wall-clock minute string.
     * @param expiration Expiration date filter (nullable, format {@code yyyy-MM-dd}).
     */
    public MaxPainResponse maxPainTyped(String symbol, String at, String expiration) {
        JsonObject raw = maxPain(symbol, at, expiration);
        return gson.fromJson(raw, MaxPainResponse.class);
    }

    /**
     * Strongly-typed variant of {@link #maxPain(String, String)}.
     *
     * @param symbol Underlying symbol.
     * @param at     ET wall-clock minute string.
     */
    public MaxPainResponse maxPainTyped(String symbol, String at) {
        return maxPainTyped(symbol, at, null);
    }

    // ── Composite ─────────────────────────────────────────────────────

    /** Full composite snapshot — price, vol, options flow, exposure, macro. */
    public JsonObject stockSummary(String symbol, String at) {
        return get("/v1/stock/" + seg(symbol) + "/summary", atMap(at));
    }

    public JsonObject stockSummary(String symbol, LocalDateTime at) { return stockSummary(symbol, formatAt(at)); }

    /**
     * Strongly-typed variant of {@link #stockSummary(String, String)}. Returns
     * a populated {@link StockSummaryResponse} with named fields. The original
     * untyped method is unchanged.
     *
     * @param symbol Underlying symbol.
     * @param at     ET wall-clock minute string.
     */
    public StockSummaryResponse stockSummaryTyped(String symbol, String at) {
        JsonObject raw = stockSummary(symbol, at);
        return gson.fromJson(raw, StockSummaryResponse.class);
    }

    /**
     * Strongly-typed variant of {@link #stockSummary(String, LocalDateTime)}.
     *
     * @param symbol Underlying symbol.
     * @param at     ET wall-clock minute as a {@link LocalDateTime}.
     */
    public StockSummaryResponse stockSummaryTyped(String symbol, LocalDateTime at) {
        return stockSummaryTyped(symbol, formatAt(at));
    }

    // ── Volatility ────────────────────────────────────────────────────

    /** Realized vol ladder, IV-RV spreads, skew profiles, term structure, GEX/theta by DTE. */
    public JsonObject volatility(String symbol, String at) {
        return get("/v1/volatility/" + seg(symbol), atMap(at));
    }

    /** SVI parameters, forward prices, total variance surface, arbitrage flags, variance swap fairs. */
    public JsonObject advVolatility(String symbol, String at) {
        return get("/v1/adv_volatility/" + seg(symbol), atMap(at));
    }

    // ── VRP ───────────────────────────────────────────────────────────

    /** VRP dashboard with date-bounded percentile history (no future leakage). */
    public JsonObject vrp(String symbol, String at) {
        return get("/v1/vrp/" + seg(symbol), atMap(at));
    }

    public JsonObject vrp(String symbol, LocalDateTime at) { return vrp(symbol, formatAt(at)); }

    /**
     * Strongly-typed variant of {@link #vrp(String, String)}. Returns a populated
     * {@link VrpResponse} with named fields for the nested VRP, directional,
     * regime, gex_conditioned, strategy_scores, and term_vrp groups. The
     * original untyped method is unchanged.
     *
     * @param symbol Underlying symbol.
     * @param at     ET wall-clock minute string.
     */
    public VrpResponse vrpTyped(String symbol, String at) {
        JsonObject raw = vrp(symbol, at);
        return gson.fromJson(raw, VrpResponse.class);
    }

    /**
     * Strongly-typed variant of {@link #vrp(String, LocalDateTime)}.
     *
     * @param symbol Underlying symbol.
     * @param at     ET wall-clock minute as a {@link LocalDateTime}.
     */
    public VrpResponse vrpTyped(String symbol, LocalDateTime at) {
        return vrpTyped(symbol, formatAt(at));
    }
}
