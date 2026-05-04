package com.flashalpha.historical;

import com.google.gson.JsonObject;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Run a strategy callback against the Historical API across a date range.
 * No fill simulation, no portfolio accounting — that belongs in user code.
 *
 * <pre>{@code
 * FlashAlphaHistoricalClient hx = new FlashAlphaHistoricalClient(apiKey);
 * Backtester bt = new Backtester(hx, Backtester.STOCK_SUMMARY, "SPY");
 * List<Backtester.Step> results = bt.run(
 *     Replay.iterDays(LocalDate.parse("2024-08-05"), LocalDate.parse("2024-08-09")),
 *     (at, snap) -> snap.getAsJsonObject("volatility").get("vrp").getAsDouble());
 * }</pre>
 */
public final class Backtester {

    /** Endpoint helper — full stock summary at session close. */
    public static final Replay.AtEndpoint STOCK_SUMMARY =
            (c, s, a) -> c.stockSummary(s, a);

    /** Endpoint helper — exposure summary. */
    public static final Replay.AtEndpoint EXPOSURE_SUMMARY =
            (c, s, a) -> c.exposureSummary(s, a);

    /** Endpoint helper — VRP dashboard. */
    public static final Replay.AtEndpoint VRP =
            (c, s, a) -> c.vrp(s, a);

    private final FlashAlphaHistoricalClient client;
    private final Replay.AtEndpoint endpoint;
    private final String symbol;
    private final boolean skipMissing;

    public Backtester(FlashAlphaHistoricalClient client, Replay.AtEndpoint endpoint, String symbol) {
        this(client, endpoint, symbol, true);
    }

    public Backtester(FlashAlphaHistoricalClient client, Replay.AtEndpoint endpoint, String symbol, boolean skipMissing) {
        this.client = client;
        this.endpoint = endpoint;
        this.symbol = symbol;
        this.skipMissing = skipMissing;
    }

    /** One step in a backtest run. */
    public static final class Step {
        public final String at;
        public final JsonObject snapshot;
        public final Object output;
        public Step(String at, JsonObject snapshot, Object output) {
            this.at = at;
            this.snapshot = snapshot;
            this.output = output;
        }
    }

    /** Strategy callback — takes (at, snapshot) and returns an opaque output object. */
    @FunctionalInterface
    public interface Strategy<T> extends BiFunction<String, JsonObject, T> {}

    public <T> List<Step> run(List<LocalDateTime> timestamps, Strategy<T> strategy) {
        List<Replay.Step> rawSteps = Replay.run(client, endpoint, symbol, timestamps, skipMissing, null);
        List<Step> results = new ArrayList<>(rawSteps.size());
        for (Replay.Step s : rawSteps) {
            results.add(new Step(s.at, s.response, strategy.apply(s.at, s.response)));
        }
        return results;
    }
}
