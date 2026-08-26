package com.flashalpha.historical;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * The envelope lives on {@link FlashAlphaResponse} rather than being repeated on each
 * response model. That is only sound if the base actually binds through Gson, so these
 * tests exercise the binding rather than the declaration - a model that quietly stopped
 * extending the base would still compile.
 */
public class ResponseEnvelopeTest {

    private static final String BODY = "{"
            + "\"symbol\":\"SPY\","
            + "\"net_gex\":1234.5,"
            + "\"endpoint_version\":\"2026.08.25\","
            + "\"data_as_of\":{"
            + "\"node\":\"f3\",\"equity_feed\":null,\"equity_options_feed\":null,"
            + "\"index_feed\":null,\"index_options_feed\":null,\"futures_feed\":null,"
            + "\"futures_options_feed\":null,\"flow_feed\":null,\"oi_feed\":null,"
            + "\"macro_feed\":null},"
            + "\"archive_as_of\":{"
            + "\"node\":\"f3\","
            + "\"equity_feed\":\"2024-03-15T14:29:59.500Z\","
            + "\"equity_options_feed\":\"2024-03-15T14:29:58.100Z\","
            + "\"index_feed\":null,\"index_options_feed\":null,\"futures_feed\":null,"
            + "\"futures_options_feed\":null,\"flow_feed\":null,"
            + "\"oi_feed\":\"2024-03-14T20:00:00.000Z\","
            + "\"macro_feed\":null}"
            + "}";

    private final Gson gson = new Gson();

    @Test
    public void envelopeBindsThroughTheBaseClass() {
        GexResponse gex = gson.fromJson(BODY, GexResponse.class);

        assertEquals("2026.08.25", gex.endpointVersion);
        assertNotNull("archive_as_of did not bind through the base class", gex.archiveAsOf);
        assertEquals("f3", gex.archiveAsOf.node);
        assertEquals("2024-03-15T14:29:58.100Z", gex.archiveAsOf.equityOptionsFeed);
    }

    /**
     * A replay node reads the archive and consumes no live feed, so every live feed is
     * null. The object is still returned, and that all-null shape is what stops a
     * historical response being mistaken for a live one - so it must survive as an
     * object with null members, not collapse to a null reference.
     */
    @Test
    public void liveFeedsAreAllNullButTheObjectSurvives() {
        GexResponse gex = gson.fromJson(BODY, GexResponse.class);

        assertNotNull("data_as_of collapsed to null; the all-null object is what marks a replayed response",
                gex.dataAsOf);
        assertNull(gex.dataAsOf.equityFeed);
        assertNull(gex.dataAsOf.equityOptionsFeed);
        assertNull(gex.dataAsOf.oiFeed);
        assertNull(gex.dataAsOf.macroFeed);
    }

    /**
     * The archive vintage is what makes a gap detectable, so it must pass through
     * untouched rather than being normalised toward the requested instant. Normalising
     * it would erase exactly the signal a point-in-time study reads.
     */
    @Test
    public void archiveVintagePassesThroughUnmodified() {
        GexResponse gex = gson.fromJson(BODY, GexResponse.class);

        assertEquals("2024-03-15T14:29:59.500Z", gex.archiveAsOf.equityFeed);
        assertEquals("2024-03-14T20:00:00.000Z", gex.archiveAsOf.oiFeed);
    }

    /** A response that did not read a class of data reports null for it. */
    @Test
    public void unreadDataClassesStayNull() {
        GexResponse gex = gson.fromJson(BODY, GexResponse.class);

        assertNull(gex.archiveAsOf.futuresFeed);
        assertNull(gex.archiveAsOf.flowFeed);
    }

    /** Responses predating the envelope must still parse; all members stay null. */
    @Test
    public void preEnvelopeResponsesStillParse() {
        GexResponse gex = gson.fromJson("{\"symbol\":\"SPY\",\"net_gex\":1.0}", GexResponse.class);

        assertNull(gex.endpointVersion);
        assertNull(gex.dataAsOf);
        assertNull(gex.archiveAsOf);
    }

    /**
     * The envelope must serialize back flat, under the wire names, rather than nesting
     * or renaming - round-tripping is how callers persist a response alongside its
     * provenance.
     */
    @Test
    public void envelopeRoundTripsUnderWireNames() {
        GexResponse gex = gson.fromJson(BODY, GexResponse.class);

        String out = gson.toJson(gex);

        assertTrue("archive_as_of missing: " + out, out.contains("\"archive_as_of\""));
        assertTrue("endpoint_version missing: " + out, out.contains("\"endpoint_version\""));
        assertFalse("camelCase leaked to the wire: " + out, out.contains("\"archiveAsOf\""));
    }

    /**
     * Guard the sweep itself: every response model must reach the base. Trusting that one
     * regex touched every file is exactly the assumption worth testing, and a model added
     * later would otherwise slip through silently.
     *
     * <p>The class list is read from the source tree rather than hardcoded, so the guard
     * cannot drift out of step with it.
     */
    @Test
    public void everyResponseModelCarriesTheEnvelope() throws Exception {
        List<String> missing = new ArrayList<>();
        int checked = 0;

        for (String name : responseClassNames()) {
            Class<?> type = Class.forName("com.flashalpha.historical." + name);
            if (Modifier.isAbstract(type.getModifiers())) {
                continue;
            }
            checked++;
            if (!FlashAlphaResponse.class.isAssignableFrom(type)) {
                missing.add(name);
            }
        }

        assertTrue("found no response models; the guard is not actually checking anything", checked > 0);
        assertTrue("response models not extending FlashAlphaResponse: " + missing, missing.isEmpty());
    }

    /** The fixture must cover every declared feed, so it cannot fall behind the model. */
    @Test
    public void fixtureCoversEveryDeclaredFeed() {
        for (Field f : DataAsOf.class.getDeclaredFields()) {
            String wire = f.getAnnotation(SerializedName.class).value();
            assertTrue("fixture omits " + wire, BODY.contains("\"" + wire + "\""));
        }
    }

    private static List<String> responseClassNames() {
        File dir = new File("src/main/java/com/flashalpha/historical");
        assertTrue("source directory not found: " + dir.getAbsolutePath(), dir.isDirectory());

        List<String> names = new ArrayList<>();
        File[] files = dir.listFiles();
        assertNotNull(files);
        for (File f : files) {
            String n = f.getName();
            if (n.endsWith("Response.java")) {
                names.add(n.substring(0, n.length() - ".java".length()));
            }
        }
        return names;
    }
}
