package com.flashalpha.historical;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Typed response model for {@code POST /v1/screener} on the historical API.
 *
 * <p>The {@link #data} rows are kept as raw {@link JsonObject} entries
 * because their column shape depends on the {@code select} clause of
 * the request (and on tier — Alpha unlocks more columns). Use
 * {@link #meta} for envelope info — total / returned counts, universe
 * size, paging, the caller's tier, and snapshot {@code as_of}.
 */
public final class ScreenerResponse {

    /** Result envelope — counts, paging, tier, and snapshot timestamp. */
    @SerializedName("meta")
    public Meta meta;

    /**
     * Result rows. Column shape depends on the request {@code select} clause
     * and on the caller's tier — kept as raw JSON for that reason.
     */
    @SerializedName("data")
    public List<JsonObject> data;

    /** Result envelope. */
    public static final class Meta {
        /** Rows that match the filter before paging. */
        @SerializedName("total_count") public Integer totalCount;
        /** Rows in {@link ScreenerResponse#data} after paging — {@code data.size()}. */
        @SerializedName("returned_count") public Integer returnedCount;
        /** Total symbols in the screener universe at this tier. */
        @SerializedName("universe_size") public Integer universeSize;
        /** Echo of the request {@code offset} (paging start). */
        @SerializedName("offset") public Integer offset;
        /** Echo of the request {@code limit} (max rows returned). */
        @SerializedName("limit") public Integer limit;
        /** Caller's tier — {@code "growth"} or {@code "alpha"}. */
        @SerializedName("tier") public String tier;
        /** ET wall-clock timestamp of the snapshot used for filtering / sorting. */
        @SerializedName("as_of") public String asOf;
    }
}
