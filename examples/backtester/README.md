# FlashAlpha options backtester (Java)

A complete, runnable backtest template. It replays the FlashAlpha Historical API day by day, decides entries and
exits from the same endpoint outputs you would read live, fills trades on real historical bid/ask quotes, and writes
`trades.csv` and `equity.csv`. It's a line-for-line port of the Python version and produces the same files.

Guide: [How to Backtest Options Strategies in Python](https://flashalpha.com/articles/how-to-backtest-options-strategies-python-flashalpha-historical-api).
The reference version is the [Python backtester](https://github.com/FlashAlpha-lab/flashalpha-historical-python/tree/main/examples/backtester).

## Requirements

- Java 17 or later
- Maven 3.6.3 or later (Gson and the test libraries are downloaded on the first build)
- A FlashAlpha Alpha plan or higher (Historical API)

## Run it

Set your API key, then run from this folder.

macOS / Linux:

```bash
export FLASHALPHA_API_KEY=your_key
mvn -q compile exec:java -Dexec.args="--symbol SPY --start 2024-01-02 --end 2024-06-28"
```

Windows PowerShell:

```powershell
$env:FLASHALPHA_API_KEY = "your_key"
mvn -q compile exec:java "-Dexec.args=--symbol SPY --start 2024-01-02 --end 2024-06-28"
```

The run prints a summary as JSON and writes `results/trades.csv` and `results/equity.csv`:

```json
{
  "sessions": 124,
  "trades": 2,
  "win_rate": 1.0,
  "total_pnl": 58.3,
  "avg_win": 29.15,
  "avg_loss": null,
  "max_drawdown": 35.5,
  "sharpe": 1.52,
  "exit_reasons": {
    "profit_target": 2
  },
  "open_at_end": 0
}
```

Every API response is cached in `.fa_cache/`. A rerun with changed rules costs no API calls for days already fetched.
A half-year of SPY takes about 450 calls the first time.

## Flags

Every setting in `Config` is a flag. Pass `--help` to list them with their defaults.

| Flag | Default | Meaning |
|---|---|---|
| `--symbol` | SPY | Underlying |
| `--start`, `--end` | 2024-01-02, 2024-06-28 | Date range, inclusive |
| `--decision-time` | 15:45 | ET minute used for every decision and fill |
| `--target-dte`, `--min-dte`, `--max-dte` | 35, 25, 50 | Expiry closest to the target inside the window |
| `--short-delta` | 0.20 | Sell the put closest to this absolute delta |
| `--width` | 5.0 | Buy the put this many points lower |
| `--contracts` | 1 | Spreads per trade |
| `--max-positions` | 1 | Open positions allowed at once |
| `--profit-target` | 0.50 | Close when 50% of the entry credit is captured |
| `--stop-loss` | 2.00 | Close when the loss reaches 2x the entry credit |
| `--exit-dte` | 21 | Close with this many calendar days left |
| `--slippage` | 0.5 | 0 = fill at mid, 1 = fill at the bid or ask |
| `--commission` | 0.65 | Per contract per leg, each side |
| `--out` | results | Folder for trades.csv and equity.csv |
| `--cache` | .fa_cache | Folder for cached API responses |

For example, to allow two positions at once in the second half of 2024:

```bash
mvn -q compile exec:java -Dexec.args="--start 2024-07-01 --end 2024-12-31 --max-positions 2 --out results_h2"
```

## Change the strategy

All the code is in `src/main/java/FlashAlphaBacktest.java`. To change the strategy, edit the three methods in the
section marked `STRATEGY: edit these three methods`:

| Method | Decides | Reads |
|---|---|---|
| `entryRules(snap, history, cfg)` | When to open a position | Today's `Snapshot` and earlier sessions' snapshots |
| `selectLegs(chain, snap, cfg)` | Which contracts to trade | The option chain for the chosen expiry |
| `exitRules(pos, snap, history, cfg)` | When to close | The position's marks and P&L, plus the snapshots |

The defaults sell a 20-delta put credit spread about 35 days out when VRP is positive and dealers have been long
gamma for two sessions. They close at 50% of the credit, a 2x loss, 21 days left, or two sessions of negative gamma.

To give the rules more data, add a field to `Snapshot` and fill it in `buildSnapshot()` from any other FlashAlpha
endpoint. Snapshot numbers are `Double`, so `null` (not returned) stays distinct from `0`.

## Output files

`trades.csv`: `entry_date, exit_date, expiry, legs, credit, pnl, exit_reason, days_held`. `credit` is the net premium
for the whole position in dollars after commission (negative for a debit).

`equity.csv`: `date, realized, unrealized, equity, open_positions, gamma_regime, vrp_20d`, one row per session.

## Run the tests

The tests use a stub data source, so they need no API key or network:

```bash
mvn -q test
```

They check the fill math, leg selection, a full open-and-close trade with exact accounting (credit 56.70, P&L 32.90),
strike formatting, and holiday skipping. The test code is in `src/test/java/FlashAlphaBacktestTest.java`.
