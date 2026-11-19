package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

/**
 * Gross, net, call and put GEX (USD per 1% move) and the contract count for a set of
 * contracts. Used by {@link ExpiryImpactResponse} and, through {@link GammaExpiryRow},
 * by {@link StrikeDependencyResponse}.
 */
public class GammaTotals {

    @SerializedName("gross_gex_usd")
    public Double grossGexUsd;

    @SerializedName("net_gex_usd")
    public Double netGexUsd;

    @SerializedName("call_gex_usd")
    public Double callGexUsd;

    @SerializedName("put_gex_usd")
    public Double putGexUsd;

    @SerializedName("contract_count")
    public Integer contractCount;

    /**
     * Set on {@link ExpiryImpactResponse#excludingExpiry}: {@code available},
     * {@code zero_gross_exposure} or {@code no_remaining_contracts}. Null elsewhere.
     */
    @SerializedName("status")
    public String status;
}
