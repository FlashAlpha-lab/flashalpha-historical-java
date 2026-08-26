package com.flashalpha.historical;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Typed response model for {@code GET /v1/surface/{symbol}?at=...}.
 *
 * <p>Implied-volatility surface as a regular grid: tenor (rows) ×
 * moneyness (columns). The {@link #iv} matrix is the IV (annualised %)
 * at each {@code (tenor, moneyness)} pair.
 *
 * <p>{@link #slicesUsed} reports the per-expiry slices that fed the fit
 * — useful for sanity-checking sparsity warnings. Its element shape is
 * preserved as raw JSON ({@link JsonElement}) so callers can adapt as
 * the API evolves.
 */
public final class SurfaceResponse extends FlashAlphaResponse {

    @SerializedName("symbol")
    public String symbol;

    @SerializedName("spot")
    public Double spot;

    @SerializedName("as_of")
    public String asOf;

    /** Grid resolution (e.g. {@code 50} for a 50×50 surface). */
    @SerializedName("grid_size")
    public Integer gridSize;

    /** Tenor axis — years to expiry. Length = {@link #gridSize}. */
    @SerializedName("tenors")
    public List<Double> tenors;

    /** Moneyness axis — {@code K / F} or log-moneyness. Length = {@link #gridSize}. */
    @SerializedName("moneyness")
    public List<Double> moneyness;

    /** IV grid (annualised %). Indexed {@code iv[tenorIdx][moneynessIdx]}. */
    @SerializedName("iv")
    public double[][] iv;

    /**
     * Per-expiry slice metadata used to fit the surface. Kept as raw
     * JSON so consumers tolerate forward-compatible shape changes.
     */
    @SerializedName("slices_used")
    public List<JsonElement> slicesUsed;
}
