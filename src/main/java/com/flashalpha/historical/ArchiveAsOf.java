package com.flashalpha.historical;

/**
 * The vintage of the archive rows actually replayed for the timestamp you requested.
 *
 * <p>Same shape as {@link DataAsOf} - the key order is a contract shared with the live
 * service - but the values describe stored rows rather than live feeds. A field is
 * {@code null} when the response did not read that class of data.
 *
 * <p>This is what makes an archive gap detectable. Request a moment with no row and the
 * query returns the most recent earlier row; nothing else in the response distinguishes
 * the two. Point-in-time work should read this and drop or flag observations whose
 * inputs precede the requested instant by more than the study tolerates.
 *
 * <p>{@link DataAsOf#oiFeed} trailing by a session is correct rather than a gap: settled
 * open interest is published once per session, so the newest figure that existed at any
 * intraday moment is the prior close.
 */
public final class ArchiveAsOf extends DataAsOf {
}
