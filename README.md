# flashalpha-historical (Java)

Java SDK for the **FlashAlpha Historical API** — point-in-time replay of every
live analytics endpoint. Ask what GEX, gamma flip, VRP, narrative, max pain,
or the full stock summary looked like at any **minute back to 2017-01-03**,
in the same response shape as the live API.

> **Point-in-time replay since 2017.** Backtest dealer positioning (GEX, VRP,
> vanna/charm, max pain) at any minute since 2017-01-03, then trade the same
> endpoints live. No look-ahead, no training-serving skew. The Historical API
> is an **Alpha tier** capability.

```xml
<dependency>
    <groupId>com.flashalpha</groupId>
    <artifactId>flashalpha-historical</artifactId>
    <version>0.4.0-RC14</version>
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

## sell-window replay: what did it say on any session since 2018?

[sell-window](https://flashalpha.com/docs/lab-api-strategies-sell-window?utm_source=github&utm_medium=readme&utm_campaign=repo-flashalpha-historical-java&utm_content=sell-window) answers "should you sell options today?" every session for SPY, QQQ and IWM. Option sellers get wiped out on a few bad days. sell-window tells you which days to stay out, and on the days it says sell, it hands you the trades: which expiry, which strikes and how to exit. Every session, for SPY, QQQ and IWM. More than 5 million backtests led to this one endpoint.

This package replays the answer for any past session, strikes included, so you can check the backtest yourself or test your own rules on the same calls.

```java
FlashAlphaHistoricalClient hx = new FlashAlphaHistoricalClient(System.getenv("FLASHALPHA_API_KEY"));
SellWindowResponse answer = hx.sellWindowTyped("SPY", "2026-10-16T09:35:00");

for (SellWindowResponse.Window window : answer.windows) {
    System.out.println(window.expiry + " " + window.action); // sell, skip or unavailable
    if (window.candidates == null) continue;
    for (SellWindowResponse.Candidate trade : window.candidates) {
        System.out.println("  " + trade.structure + " " + trade.midCredit + " " + trade.exit);
    }
}
```

The backtests entered at 09:35 ET, so `at=<session>T09:35:00` returns the backtest's entry strikes. The session switches at the close, so an `at` after the close, including a date-only `at` (read as 16:00 ET), returns the answer for the next session. An `at` before the archive starts returns every window as `unavailable`. A malformed or future `at` returns `400 invalid_at`.

From the live launch on, the action and trades match what the live endpoint answered at that time. Before it they are rebuilt from the archive with the same rules.

| Backtest, 2018 to 2026 | Following sell-window | Selling the same trades every day |
|---|---|---|
| Max drawdown at the same trade size | $255k | $959k |
| Worst month at the same trade size | -$171k | -$307k |
| Sharpe ratio | 0.76 | 0.61 |
| Total P&L at the same max drawdown | $3.55M | $2.32M |
| Worst day at the same max drawdown | -$258k | -$385k |

Cut the risk or earn more. Keep your trade size and the deepest drawdown falls 73%. Or size up about 3.8 times, until the deepest drawdown matches, and you earn 53% more. These are two separate choices, not one result.

**Plans:** Live answers need Basic or higher through November 2026, and Growth or higher from December 1, 2026. Backtesting any past session needs Alpha or higher. For live answers, use [flashalpha-java](https://github.com/FlashAlpha-lab/flashalpha-java).

**Read more:** [API docs](https://flashalpha.com/docs/historical-sell-window?utm_source=github&utm_medium=readme&utm_campaign=repo-flashalpha-historical-java&utm_content=sell-window) | [trading guide](https://flashalpha.com/articles/complete-guide-trading-sell-window-spy-qqq-iwm?utm_source=github&utm_medium=readme&utm_campaign=repo-flashalpha-historical-java&utm_content=sell-window) | [every backtested trade](https://flashalpha.com/articles/sell-window-backtest-every-trade-2018-2026?utm_source=github&utm_medium=readme&utm_campaign=repo-flashalpha-historical-java&utm_content=sell-window) | [sell-window in depth](https://github.com/FlashAlpha-lab/flashalpha-historical-java/blob/main/docs/sell-window.md) | [plans](https://flashalpha.com/pricing?utm_source=github&utm_medium=readme&utm_campaign=repo-flashalpha-historical-java&utm_content=sell-window) | [live forward test on Discord](https://discord.gg/UtH22J8df2)

*Backtest on SPY, QQQ and IWM, January 2018 to October 2026, at halfway fills and $1 per contract. At one contract set per signal, following it made $945,541 on 20,883 trades; selling every day made $2,321,150 on 56,975. At 3.76 times the contracts, both fell $958,924 at their deepest, and the worst month was -$642,135 against -$307,241. Past results do not guarantee future returns, and short options can lose more than the premium collected. The skip call applies to new trades, so it cannot protect open positions from a sudden shock. This is data, not investment advice.*

## Data provenance: `data_as_of`

Every successful response carries `data_as_of`, reporting when each upstream feed last
delivered to the node that answered, plus `endpoint_version` identifying the deployment
that produced it. Every endpoint on this replay service returns a JSON object, so unlike
the live SDK there is no bare-array case where the envelope is unavailable.

```java
GexResponse gex = client.gexTyped("SPY", "2024-03-15T14:30:00Z");

gex.archiveAsOf.equityOptionsFeed;  // "2024-03-15T14:29:58.100Z"  the rows replayed
gex.archiveAsOf.oiFeed;             // "2024-03-14T20:00:00.000Z"  prior session's close
gex.dataAsOf.equityOptionsFeed;     // null - a replay node consumes no live feed
gex.endpointVersion;                // the deployment that answered
```

Every response model extends `FlashAlphaResponse`, which carries `endpointVersion`,
`dataAsOf` and `archiveAsOf`, so the envelope is a typed member on all of them rather
than a field Gson silently discards.

| Field | Feed | Expected cadence |
|---|---|---|
| `node` | Which node answered | Nodes hydrate independently |
| `equity_feed` | Equity and ETF spot quotes | seconds, during market hours |
| `equity_options_feed` | Equity and ETF option quotes | seconds, during market hours |
| `index_feed` | Index spot (SPX, RUT, VIX and the other index roots) | seconds, during market hours |
| `index_options_feed` | Index option quotes | seconds, during market hours |
| `futures_feed` | Futures prices | seconds, during the futures session |
| `futures_options_feed` | Futures option quotes | seconds, during the futures session |
| `flow_feed` | Classified options and stock trade tape | seconds, during market hours |
| `oi_feed` | Settled open interest | daily, dated to the prior 16:00 ET close |
| `macro_feed` | VIX, VVIX, SKEW, MOVE, SPX, Fear & Greed | minutes; reports its OLDEST component |

Historical responses carry a second object, `archive_as_of`, in the same shape: the
vintage of the archive rows actually replayed for the timestamp you requested. Its
every feed in `data_as_of` is `null`, because a replay node reads the archive and consumes no
live feed.

`archive_as_of` is what makes an archive gap detectable. Request a moment with no row
and the query returns the most recent earlier row; nothing else in the response
distinguishes the two. Point-in-time work should read it and drop or flag observations
whose inputs precede the requested instant by more than the study tolerates.

### How to read it

- **Check the feeds your call depends on.** A GEX call on an equity is answered from
  `equity_feed`, `equity_options_feed` and `oi_feed`. `futures_feed` being `null` in that
  response says nothing about the answer.
- **Compare against the cadence, not the clock.** `oi_feed` at the previous session's
  close is correct: settled open interest is published once per session, so on a Monday
  the newest figure that exists is Friday's. An options feed an hour behind during the
  regular session is not correct.
- **`null` means "not seen on this node", not "broken".** A node that has never been
  asked for a futures symbol has never opened that feed.
- **Spot and options are separate on purpose.** They arrive over different pipes and can
  fail independently.
- **It evidences feed activity, not per-contract freshness.** An illiquid strike may not
  have quoted for hours while its feed is healthy.
- **`data_as_of` is not `as_of`.** `as_of` is response-generation time or the newest
  contract in the payload, depending on the endpoint. `data_as_of` describes the feeds
  behind it.

Full reference: <https://flashalpha.com/docs/lab-api-overview#response-envelope> and the
methodology whitepaper at <https://flashalpha.com/methodology#freshness-reporting>.
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
| `sellWindow(s, at)` / `sellWindowTyped(s, at)` | `/v1/strategies/sell-window/{s}` |

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

## Get access

The Historical API requires the **Alpha tier ($1,499/mo)**: the only public source
of aggregate vanna/charm exposure and point-in-time replay since 2017.

Quant teams, prop desks, and vol funds:
**[flashalpha.com/for-quant-teams](https://flashalpha.com/for-quant-teams?utm_source=github&utm_medium=readme&utm_campaign=repo-flashalpha-historical-java)**
