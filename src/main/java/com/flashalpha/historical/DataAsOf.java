package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

/**
 * When each upstream feed last delivered to the node that served the response.
 *
 * <p>On this replay service every feed is {@code null}: a replay node reads the archive
 * and consumes no live feed. The object is still returned so the envelope has one shape
 * across the live and historical services, and so a historical response cannot be
 * mistaken for a live one.
 *
 * <p>The vintage that matters here is {@link ArchiveAsOf}, carried alongside it as
 * {@code archive_as_of}.
 */
public class DataAsOf {

    /** Which node answered. */
    @SerializedName("node")
    public String node;

    /** Equity and ETF spot quotes. */
    @SerializedName("equity_feed")
    public String equityFeed;

    /** Equity and ETF option quotes. */
    @SerializedName("equity_options_feed")
    public String equityOptionsFeed;

    /** Index spot - SPX, NDX, RUT, VIX. */
    @SerializedName("index_feed")
    public String indexFeed;

    /** Index option quotes. */
    @SerializedName("index_options_feed")
    public String indexOptionsFeed;

    /** Futures prices. */
    @SerializedName("futures_feed")
    public String futuresFeed;

    /** Futures option quotes. */
    @SerializedName("futures_options_feed")
    public String futuresOptionsFeed;

    /** Classified options and stock trade tape. */
    @SerializedName("flow_feed")
    public String flowFeed;

    /** Settled open interest. */
    @SerializedName("oi_feed")
    public String oiFeed;

    /** VIX, VVIX, SKEW, MOVE, SPX and Fear &amp; Greed. */
    @SerializedName("macro_feed")
    public String macroFeed;
}
