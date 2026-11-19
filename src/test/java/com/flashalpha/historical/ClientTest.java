package com.flashalpha.historical;

import com.google.gson.JsonObject;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/** Unit tests — fully mocked via OkHttp MockWebServer. */
public class ClientTest {

    private MockWebServer server;
    private FlashAlphaHistoricalClient client;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new FlashAlphaHistoricalClient("KEY", server.url("/").toString());
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private void enqueue(int code, String body) {
        server.enqueue(new MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body));
    }

    @Test
    public void formatAt_LocalDateTime_ReturnsIsoMinute() {
        assertEquals("2026-03-05T15:30:00",
                FlashAlphaHistoricalClient.formatAt(LocalDateTime.of(2026, 3, 5, 15, 30)));
    }

    @Test
    public void formatAt_LocalDate_ReturnsIsoDate() {
        assertEquals("2026-03-05",
                FlashAlphaHistoricalClient.formatAt(LocalDate.of(2026, 3, 5)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_RejectsEmptyApiKey() {
        new FlashAlphaHistoricalClient("");
    }

    @Test
    public void exposureSummary_ForwardsAtAsQueryString() throws Exception {
        enqueue(200, "{\"regime\":\"positive_gamma\"}");
        JsonObject resp = client.exposureSummary("SPY", "2026-03-05T15:30:00");
        assertEquals("positive_gamma", resp.get("regime").getAsString());

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(req);
        assertEquals("KEY", req.getHeader("X-Api-Key"));
        String path = req.getPath();
        assertTrue("path should include endpoint: " + path,
                path.startsWith("/v1/exposure/summary/SPY"));
        assertTrue("at must be encoded: " + path,
                path.contains("at=2026-03-05T15%3A30%3A00"));
    }

    @Test(expected = InvalidAtException.class)
    public void invalidAt_400_MapsToTypedException() {
        enqueue(400, "{\"error\":\"invalid_at\",\"message\":\"bad\"}");
        client.exposureSummary("SPY", "garbage");
    }

    @Test(expected = NoCoverageException.class)
    public void noCoverage_404_MapsToTypedException() {
        enqueue(404, "{\"error\":\"no_coverage\"}");
        client.tickers("ZZZZZ");
    }

    @Test(expected = NoDataException.class)
    public void noData_404_MapsToTypedException() {
        enqueue(404, "{\"error\":\"no_data\"}");
        client.exposureSummary("SPY", "2017-01-01");
    }

    @Test(expected = SymbolNotFoundException.class)
    public void symbolNotFound_404_MapsToTypedException() {
        enqueue(404, "{\"error\":\"symbol_not_found\"}");
        client.stockQuote("XYZ", "2024-01-02");
    }

    @Test(expected = InsufficientDataException.class)
    public void insufficientData_404_MapsToTypedException() {
        enqueue(404, "{\"error\":\"insufficient_data\"}");
        client.surface("SPY", "2018-04-16");
    }

    @Test
    public void tierRestricted_403_PopulatesPlanFields() {
        enqueue(403, "{\"error\":\"tier_restricted\",\"current_plan\":\"Growth\"," +
                "\"required_plan\":\"Alpha\",\"message\":\"needs Alpha\"}");
        try {
            client.exposureSummary("SPY", "2026-03-05");
            fail("expected TierRestrictedException");
        } catch (TierRestrictedException ex) {
            assertEquals("Growth", ex.getCurrentPlan());
            assertEquals("Alpha", ex.getRequiredPlan());
        }
    }

    @Test(expected = AuthenticationException.class)
    public void authentication_401_MapsToTypedException() {
        enqueue(401, "");
        client.tickers();
    }

    @Test
    public void optionQuote_PassesAllFilters() throws Exception {
        enqueue(200, "{\"strike\":680,\"type\":\"C\"}");
        client.optionQuote("SPY", "2026-03-05T15:30:00", "2026-03-06", 680.0, "C");
        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(req);
        String path = req.getPath();
        assertTrue(path.contains("strike=680"));
        assertTrue(path.contains("type=C"));
        assertTrue(path.contains("expiry=2026-03-06"));
    }

    @Test
    public void at_LocalDateTimeOverload_FormatsCorrectly() throws Exception {
        enqueue(200, "{\"symbol\":\"SPY\"}");
        client.vrp("SPY", LocalDateTime.of(2025, 6, 18, 12, 0, 0));
        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(req);
        assertTrue(req.getPath().contains("at=2025-06-18T12%3A00%3A00"));
    }

    // ── sell-window ────────────────────────────────────────────────────

    @Test
    public void sellWindowTyped_SendsAtAndBindsEveryField() throws Exception {
        enqueue(200, "{\"symbol\":\"SPY\",\"session\":\"2026-10-16\",\"spot\":772.255,\"quoted_at\":\"2026-10-16T13:35:00Z\",\"legs_indicative\":false,\"windows\":[{\"expiry\":\"2026-10-16\",\"days_to_expiry\":0,\"action\":\"sell\",\"candidates\":[{\"structure\":\"short_put\",\"exit\":\"expiry\",\"legs\":[{\"side\":\"sell\",\"quantity\":1,\"type\":\"put\",\"strike\":769,\"delta\":-0.19,\"bid\":0.39,\"ask\":0.4}],\"mid_credit\":0.395}]},{\"expiry\":\"2026-10-23\",\"days_to_expiry\":7,\"action\":\"skip\"},{\"expiry\":\"2026-10-30\",\"days_to_expiry\":14,\"action\":\"skip\"},{\"action\":\"unavailable\"}],\"endpoint_version\":\"2026.09.30\",\"data_as_of\":{\"node\":\"f3\"},\"archive_as_of\":{\"node\":\"f3\"}}");
        SellWindowResponse r = client.sellWindowTyped("SPY", "2026-10-16T09:35:00");
        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(req);
        assertTrue(req.getPath().startsWith("/v1/strategies/sell-window/SPY"));
        assertTrue(req.getPath().contains("at=2026-10-16T09%3A35%3A00"));
        assertEquals(4, r.windows.size());
        SellWindowResponse.Candidate trade = r.windows.get(0).candidates.get(0);
        assertEquals(0.395, trade.midCredit, 0.0001);
        assertEquals(769.0, trade.legs.get(0).strike, 0.0001);
        assertNull(r.windows.get(3).expiry);
        assertEquals("f3", r.archiveAsOf.node);
    }

    @Test
    public void sellWindow_LocalDateTimeOverload_FormatsAt() throws Exception {
        enqueue(200, "{\"symbol\":\"SPY\"}");
        client.sellWindow("SPY", LocalDateTime.of(2026, 10, 16, 9, 35, 0));
        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(req);
        assertTrue(req.getPath().contains("at=2026-10-16T09%3A35%3A00"));
    }

    // ── expiry impact + strike dependency ──────────────────────────────

    private static final String AT = "2026-10-09T15:55:00";
    private static final String AT_QS = "at=2026-10-09T15%3A55%3A00";

    private static String fixture(String name) throws Exception {
        try (InputStream in = ClientTest.class.getResourceAsStream("/fixtures/" + name)) {
            assertNotNull("missing fixture " + name, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void expiryImpactTyped_SendsOnlyAtAndBindsEveryLevel() throws Exception {
        enqueue(200, fixture("expiry_impact_historical.json"));
        ExpiryImpactResponse r = client.expiryImpactTyped("SPY", AT);
        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(req);
        assertEquals("/v1/exposure/expiry-impact/SPY?" + AT_QS, req.getPath());

        // shared metadata + envelope
        assertEquals("SPY", r.symbol);
        assertEquals(778.405, r.underlyingPrice, 1e-9);
        assertEquals("historical_quote_repriced_dated_inputs", r.pricingBasis);
        assertEquals("partial", r.dataStatus);
        assertEquals("historical_model_reconstruction", r.reasons.get(0).code);
        assertNull(r.reasons.get(0).contractCount);
        assertEquals(Integer.valueOf(302), r.reasons.get(1).contractCount);
        assertEquals(Integer.valueOf(12078), r.eligibleContractCount);
        assertEquals(1480.0, r.observedMaxStrike, 0.0);
        assertNull(r.oiAsOf);
        assertEquals("2026-10-09T19:55:00Z", r.quoteAt);
        assertEquals("2026.09.30", r.endpointVersion);
        assertEquals("f3", r.dataAsOf.node);
        assertNull(r.dataAsOf.equityFeed);
        assertEquals("f3", r.archiveAsOf.node);
        assertEquals("2026-10-09T19:55:00Z", r.archiveAsOf.equityOptionsFeed);
        assertEquals("2026-10-08T20:00:00Z", r.archiveAsOf.oiFeed);

        // selection + totals
        assertEquals("2026-10-09", r.selectedExpiry);
        assertEquals("nearest_active", r.selectionSource);
        assertEquals(3, r.availableExpiries.size());
        assertEquals(58250427831.002655, r.current.grossGexUsd, 1e-3);
        assertEquals(Integer.valueOf(100), r.excludedExpiry.contractCount);
        assertNull(r.excludedExpiry.status);
        assertEquals("available", r.excludingExpiry.status);

        // impact and its strike rows
        assertEquals(27.355976302706107, r.impact.removedGrossSharePct, 1e-12);
        assertEquals(72.64402369729393, r.impact.retainedGrossSharePct, 1e-12);
        assertEquals("positive", r.impact.netSignAfter);
        assertEquals(Boolean.FALSE, r.impact.netSignChanged);
        GammaStrikeImpact top = r.impact.largestGrossReductions.get(0);
        assertEquals(778.0, top.strike, 0.0);
        assertEquals(-0.05202947051984319, top.distanceFromSpotPct, 1e-15);
        assertEquals(87.54989911773554, top.excludedShareAtStrikePct, 1e-12);
        assertEquals(-186739.42129722596, r.impact.strongestRemainingConcentrations.get(1).excludedNetGexUsd, 1e-9);

        // integer JSON values bind as doubles
        assertEquals(0.0, r.strikes.get(0).excludedNetGexUsd, 0.0);
        assertEquals(55.0, r.strikes.get(1).strike, 0.0);

        // facts: free-form supporting values
        assertEquals("fixed_input_comparison", r.facts.get(1).code);
        assertEquals(778.405, r.facts.get(1).supportingValues.get("underlying_price").getAsDouble(), 1e-9);
        assertEquals("2026-10-09", r.facts.get(0).supportingValues.get("expiry").getAsString());
    }

    @Test
    public void expiryImpact_SendsExpiryAndLimitAfterAt() throws Exception {
        enqueue(200, fixture("expiry_impact_historical.json"));
        client.expiryImpact("SPY", AT, "2026-10-12", 3);
        assertEquals("/v1/exposure/expiry-impact/SPY?" + AT_QS + "&expiry=2026-10-12&limit=3",
                server.takeRequest(2, TimeUnit.SECONDS).getPath());

        enqueue(200, fixture("expiry_impact_historical.json"));
        client.expiryImpactTyped("SPY", LocalDateTime.of(2026, 10, 9, 15, 55, 0));
        assertEquals("/v1/exposure/expiry-impact/SPY?" + AT_QS, server.takeRequest(2, TimeUnit.SECONDS).getPath());
    }

    @Test
    public void strikeDependencyTyped_SendsAtAndStrikeAndBindsEveryLevel() throws Exception {
        enqueue(200, fixture("strike_dependency_historical.json"));
        StrikeDependencyResponse r = client.strikeDependencyTyped("SPY", AT, 670);
        assertEquals("/v1/exposure/strike-dependency/SPY?" + AT_QS + "&strike=670",
                server.takeRequest(2, TimeUnit.SECONDS).getPath());

        assertEquals(670.0, r.strike, 0.0);
        assertEquals(-13.92655494247853, r.distanceFromSpotPct, 1e-12);
        assertEquals(-95819046.48603098, r.netGexUsd, 1e-6);
        assertEquals(Integer.valueOf(60), r.contractCount);
        assertEquals("2026-12-18", r.dominantExpiry);
        assertEquals(29.844720445178503, r.dominantExpirySharePct, 1e-12);
        assertEquals("strike_attribution", r.analysisType);
        assertEquals("f3", r.archiveAsOf.node);

        GammaExpiryRow row = r.byExpiry.get(0);
        assertEquals("2026-10-13", row.expiry);
        assertEquals("2026-10-13T20:00:00Z", row.valuationCutoffAt);
        assertNull(row.settlementAt);
        assertEquals(Integer.valueOf(4), row.calendarDaysToExpiry);
        assertEquals(0.0026251306222739787, row.shareOfStrikeGrossPct, 1e-18);
        assertEquals(3035.560157574413, row.grossGexUsd, 1e-9);
        assertEquals(0.0, row.callGexUsd, 0.0);
        assertEquals(Integer.valueOf(2), row.contractCount);
    }

    @Test
    public void strikeDependency_SendsFractionalStrikeExactlyInAnyLocale() throws Exception {
        Locale saved = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY); // comma decimal separator
        try {
            enqueue(200, "{\"symbol\":\"SPY\"}");
            client.strikeDependency("SPY", LocalDateTime.of(2026, 10, 9, 15, 55, 0), 672.5);
            assertEquals("/v1/exposure/strike-dependency/SPY?" + AT_QS + "&strike=672.5",
                    server.takeRequest(2, TimeUnit.SECONDS).getPath());
        } finally {
            Locale.setDefault(saved);
        }

        enqueue(200, "{\"symbol\":\"SPY\"}");
        client.strikeDependency("SPY", AT, 12345678.5); // Double.toString gives 1.23456785E7
        assertTrue(server.takeRequest(2, TimeUnit.SECONDS).getPath().endsWith("&strike=12345678.5"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void strikeDependency_RejectsNonFiniteStrike() {
        client.strikeDependency("SPY", AT, Double.POSITIVE_INFINITY);
    }

    @Test
    public void expiryImpact_403RaisesTierRestricted() {
        enqueue(403, "{\"error\":\"tier_restricted\",\"message\":\"Requires Alpha plan or higher.\",\"current_plan\":\"Growth\",\"required_plan\":\"Alpha\"}");
        try {
            client.expiryImpactTyped("SPY", AT);
            fail("expected TierRestrictedException");
        } catch (TierRestrictedException e) {
            assertEquals(403, e.getStatusCode());
            assertEquals("Alpha", e.getRequiredPlan());
        }
    }

    @Test
    public void strikeDependency_422SurfacesUnsupportedSymbol() throws Exception {
        enqueue(422, fixture("error_unsupported_symbol.json"));
        try {
            client.strikeDependencyTyped("AAPL", AT, 200);
            fail("expected FlashAlphaHistoricalException");
        } catch (FlashAlphaHistoricalException e) {
            assertEquals(422, e.getStatusCode());
            assertEquals("unsupported_symbol", e.getResponse().get("error").getAsString());
            assertEquals("This operation currently supports standard SPY and QQQ options.", e.getMessage());
        }
    }
}
