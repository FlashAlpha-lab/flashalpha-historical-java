package com.flashalpha.historical;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import java.util.Map;

/** A plain-language statement with its supporting values. */
public final class GammaContextFact {

    /** Machine code, for example {@code expiry_gross_share}. */
    @SerializedName("code")
    public String code;

    @SerializedName("text")
    public String text;

    /**
     * Values behind {@link #text}. Keys vary by {@link #code}, so values are kept as raw
     * {@link JsonElement} (numbers, strings).
     */
    @SerializedName("supporting_values")
    public Map<String, JsonElement> supportingValues;
}
