# flashalpha-historical (Java)

Java SDK for the **FlashAlpha Historical API** — point-in-time replay of every
live analytics endpoint. Ask what GEX, gamma flip, VRP, narrative, max pain,
or the full stock summary looked like at any **minute back to 2018-04-16**,
in the same response shape as the live API.

```xml
<dependency>
    <groupId>com.flashalpha</groupId>
    <artifactId>flashalpha-historical</artifactId>
    <version>0.1.0</version>
</dependency>
```

Java 11+. Same `X-Api-Key` you use for `api.flashalpha.com` — Alpha plan or
higher on every endpoint.

## Quickstart

```java
import com.flashalpha.historical.FlashAlphaHistoricalClient;
import com.google.gson.JsonObject;

FlashAlphaHistoricalClient hx = new FlashAlphaHistoricalClient(System.getenv("FLASHALPHA_API_KEY"));

// One snapshot — what dealer positioning looked like during the COVID crash
JsonObject snap = hx.exposureSummary("SPY", "2020-03-16T15:30:00");
System.out.println(snap.get("regime").getAsString());
// → "negative_gamma"
```

`at` accepts strings, `LocalDateTime`, or `LocalDate` (date-only defaults to
16:00 ET on the API side).

## Backtesting

```java
import com.flashalpha.historical.*;
import java.time.LocalDate;
import java.util.List;

FlashAlphaHistoricalClient hx = new FlashAlphaHistoricalClient(apiKey);

Backtester bt = new Backtester(hx, Backtester.STOCK_SUMMARY, "SPY");

List<Backtester.Step> results = bt.run(
    Replay.iterDays(LocalDate.parse("2024-01-02"), LocalDate.parse("2024-03-29")),
    (at, snap) -> {
        double vrp = snap.getAsJsonObject("volatility").get("vrp").getAsDouble();
        String regime = snap.getAsJsonObject("exposure").get("regime").getAsString();
        return Map.of("fire", vrp > 5 && regime.equals("positive_gamma"));
    });
```

### Minute-level

```java
List<Replay.Step> steps = Replay.run(
    hx, Backtester.EXPOSURE_SUMMARY, "SPY",
    Replay.iterMinutes(LocalDate.parse("2025-01-15"), LocalDate.parse("2025-01-15"), 15));

for (Replay.Step s : steps) {
    System.out.println(s.at + "  " + s.response.get("regime").getAsString());
}
```

## API surface

| Method | Endpoint |
|---|---|
| `tickers([symbol])` | `/v1/tickers` |
| `stockQuote(t, at)` | `/v1/stockquote/{t}` |
| `optionQuote(t, at, expiry?, strike?, type?)` | `/v1/optionquote/{t}` |
| `surface(s, at)` | `/v1/surface/{s}` |
| `gex(s, at, expiration?, minOi?)` | `/v1/exposure/gex/{s}` |
| `dex(s, at, expiration?)` | `/v1/exposure/dex/{s}` |
| `vex(s, at, expiration?)` | `/v1/exposure/vex/{s}` |
| `chex(s, at, expiration?)` | `/v1/exposure/chex/{s}` |
| `exposureSummary(s, at)` | `/v1/exposure/summary/{s}` |
| `exposureLevels(s, at)` | `/v1/exposure/levels/{s}` |
| `narrative(s, at)` | `/v1/exposure/narrative/{s}` |
| `zeroDte(s, at, strikeRange?)` | `/v1/exposure/zero-dte/{s}` |
| `maxPain(s, at, expiration?)` | `/v1/maxpain/{s}` |
| `stockSummary(s, at)` | `/v1/stock/{s}/summary` |
| `volatility(s, at)` | `/v1/volatility/{s}` |
| `advVolatility(s, at)` | `/v1/adv_volatility/{s}` |
| `vrp(s, at)` | `/v1/vrp/{s}` |

## Exceptions

| Type | Status |
|---|---|
| `FlashAlphaHistoricalException` | base |
| `AuthenticationException` | 401 |
| `TierRestrictedException` | 403 — needs Alpha plan |
| `InvalidAtException` | 400 — bad `at` format |
| `NoDataException` | 404 — outside coverage / inside gap |
| `SymbolNotFoundException` | 404 — symbol not at this `at` |
| `NoCoverageException` | 404 — symbol not in historical dataset |
| `InsufficientDataException` | 404 — surface grid too sparse |
| `RateLimitException` | 429 |
| `ServerException` | 5xx |

## License

MIT
