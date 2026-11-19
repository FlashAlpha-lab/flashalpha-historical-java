package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

/** One entry of {@link GammaContextMetadata#reasons}: a machine code with its explanation. */
public final class GammaContextReason {

    /** Machine code, for example {@code oi_vintage_unknown} or {@code gamma_unavailable}. */
    @SerializedName("code")
    public String code;

    @SerializedName("message")
    public String message;

    /** Contracts excluded for this reason. Null when the reason does not exclude contracts. */
    @SerializedName("contract_count")
    public Integer contractCount;
}
