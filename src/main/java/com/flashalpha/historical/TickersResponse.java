package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Typed response model for {@code GET /v1/tickers} on the historical API.
 *
 * <p>The historical {@code /v1/tickers} endpoint has two response shapes:
 *
 * <ul>
 *   <li>Unfiltered (no {@code symbol} query parameter): {@link #tickers}
 *       holds one {@link TickerCoverageRow} per supported symbol and
 *       {@link #count} mirrors {@code tickers.size()}.</li>
 *   <li>Filtered to a specific {@code symbol}: the response collapses to
 *       a single coverage row with {@link #symbol} populated and
 *       {@link #coverage} describing first / last available dates and
 *       healthy-day count. {@link #tickers} / {@link #count} are
 *       {@code null} on this filtered shape.</li>
 * </ul>
 */
public final class TickersResponse {

    /** All supported symbols and their coverage windows. {@code null} on the filtered single-symbol shape. */
    @SerializedName("tickers")
    public List<TickerCoverageRow> tickers;

    /** {@code tickers.size()} — present on the unfiltered list shape only. */
    @SerializedName("count")
    public Integer count;

    /** Echo of the requested symbol on the filtered shape; {@code null} on the unfiltered list shape. */
    @SerializedName("symbol")
    public String symbol;

    /** Coverage block on the filtered single-symbol shape. */
    @SerializedName("coverage")
    public Coverage coverage;

    /** One row of the unfiltered list — symbol + nested coverage block. */
    public static final class TickerCoverageRow {
        /** Symbol (e.g. {@code "SPY"}). */
        @SerializedName("symbol") public String symbol;
        /** Coverage window for this symbol. */
        @SerializedName("coverage") public Coverage coverage;
    }

    /** Historical coverage window. */
    public static final class Coverage {
        /** First available date (ISO {@code yyyy-MM-dd}). */
        @SerializedName("first") public String first;
        /** Last available date (ISO {@code yyyy-MM-dd}). */
        @SerializedName("last") public String last;
        /** Number of trading days with healthy historical coverage. */
        @SerializedName("healthy_days") public Integer healthyDays;
    }
}
