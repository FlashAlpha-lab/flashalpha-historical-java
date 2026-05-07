package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Typed response model for {@code GET /v1/symbols} on the historical API.
 *
 * <p>Symbols available for replay through the historical grid.
 */
public final class SymbolsResponse {

    /** Symbols available for historical replay. */
    @SerializedName("symbols")
    public List<String> symbols;

    /** {@code symbols.size()}. */
    @SerializedName("count")
    public Integer count;

    /** Free-form coverage note (e.g. universe scope or grid resolution). */
    @SerializedName("note")
    public String note;

    /** Timestamp of the latest catalogue refresh (ET wall-clock string). */
    @SerializedName("last_updated")
    public String lastUpdated;
}
