package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

/**
 * Base for every typed response model. Carries the envelope the API returns on all
 * successful responses.
 *
 * <p>Gson reflects over the full class hierarchy, so inherited fields bind exactly as
 * declared ones do and the wire shape is unchanged. All members are objects rather than
 * primitives, so a response predating the envelope leaves them null instead of failing
 * to parse.
 */
public abstract class FlashAlphaResponse {

    /** Identifies the deployment that produced this response. */
    @SerializedName("endpoint_version")
    public String endpointVersion;

    /** Live-feed freshness. All null on this replay service. See {@link DataAsOf}. */
    @SerializedName("data_as_of")
    public DataAsOf dataAsOf;

    /** Vintage of the archive rows actually replayed. See {@link ArchiveAsOf}. */
    @SerializedName("archive_as_of")
    public ArchiveAsOf archiveAsOf;
}
