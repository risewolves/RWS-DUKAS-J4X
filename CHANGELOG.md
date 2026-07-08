# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-07-08

### Added

- **Live tick capture strategy** (`singlejartest.LiveCaptureStrategy`)
  - Connects via `IClient` (which we confirmed works reliably).
  - Receives real‑time ticks for EUR/USD.
  - Aggregates them into 1‑minute OHLCV bars on the fly.
  - Automatically saves monthly CSV files when the month changes or when the user stops the program.
  - Includes accurate `AV_SPREAD` calculation from bid/ask spreads.

- **Historical downloader** (`com.rws.dukas.DukascopyHistoricalDownloader`)
  - Uses `ITesterClient` to attempt bulk downloads from 2005‑01‑01 to 2026‑07‑03.
  - Splits the workload into daily chunks to reduce the load on the connection.
  - Includes a configurable 3‑minute wait between chunks (`WAIT_MS`).
  - Skips months that already have a CSV file.

- **Quick connection test** (`com.rws.dukas.LiveTickTest`)
  - A lightweight test that simply connects and prints a few ticks.
  - Useful for checking whether your credentials and network are working.

- **Official Dukascopy examples** (`singlejartest.Main` and `singlejartest.MA_Play`)
  - These are the original SDK examples, kept as a reference for how to use `IClient` and `IStrategy`.

- **Maven build setup**
  - Configured to produce a fat JAR (`dukascopy-downloader-1.0.0.jar`) that includes all dependencies.
  - Uses the official Dukascopy public repository for the JForex SDK.

### Fixed

- **Period class ambiguity**
  - Earlier versions had compilation errors because `java.time.Period` and `com.dukascopy.api.Period` conflicted.
  - We replaced wildcard `import java.time.*` with explicit imports to keep only the SDK’s `Period`.

- **`startStrategy` signature mismatches**
  - We tried to use `startStrategy(IStrategy)` with both `IClient` and `ITesterClient`.
  - The working solution uses `IClient.startStrategy(IStrategy)` for live data, which compiles and runs correctly.

- **`getHistory()` method not found**
  - In earlier attempts, we called `client.getHistory()` directly.
  - The correct approach for live strategies is to get `IHistory` via `context.getHistory()` inside the strategy, which we now do.

- **`Instrument not opened` and `Read timed out`**
  - These errors appeared repeatedly when using `ITesterClient` for large historical ranges.
  - We introduced daily chunking and pauses to mitigate the issue, though it still depends on the network setup.

### Known Limitations (for this release)

- The historical downloader may still time out on some VPS networks (especially those outside Europe). This is because the SDK’s internal HTTP client has a hard‑coded timeout that we cannot override.
- The `AV_SPREAD` column is set to `0.0` when you use the historical downloader with pre‑aggregated bars. Only the live capture strategy provides actual spread values.
- The demo credentials (`DEMO2YciXg` / `YciXg`) will eventually expire. You will need to renew them manually.

### What We Learned

Building this taught us a few things about the JForex SDK:

- `IClient` is great for live data and is much more stable than `ITesterClient`.
- `ITesterClient` is fragile when fetching large amounts of historical data over certain network paths.
- The official documentation covers the API methods well, but it does not warn you about the internal timeouts or the differences in behaviour between the two client types.

---

## [Unreleased]

### Planned

- Add support for other instruments (currently only tested on EUR/USD).
- Add a retry mechanism for failed historical chunks.
- Explore using an external HTTP client or proxy to avoid the SDK’s internal timeout.
- Improve error logging to make debugging easier.

---