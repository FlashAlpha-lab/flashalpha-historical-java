package com.flashalpha.historical;

import com.google.gson.JsonObject;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
}
