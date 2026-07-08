# Changelog

All notable changes to this project are documented in this file.

---

## [2.1.0] - 2026-07-08

### Added

- **Platform‑specific data transfer guide** — The `README.md` now includes a dedicated section that shows you exactly how to copy the `archive/` folder from your VPS to your local machine. It covers:
  - `scp` and `rsync` for **Mac and Linux** (with a clear explanation of why `rsync` is better for resuming interrupted transfers).
  - `scp` and **WinSCP** for **Windows** users, plus a quick note on using WSL if you prefer the command line.
  - A practical warning about the Terabox 2 GB file size limit (so you don't accidentally zip everything into one huge file and fail to upload it).

### Changed

- **Documentation clarity** — The main `README.md` has been restructured to make the post‑download workflow more obvious. Instead of just telling you where the files are, it now holds your hand through the process of pulling them down to your actual working machine, whether you are on a Mac, a Windows PC, or a Linux laptop.

---

## [2.0.0] - 2026-07-08

### Added

- **JForex4Downloader** — Complete rewrite of the historical downloader using the **JForex 4 API (`IClient`)** instead of the problematic `ITesterClient`.
  - Uses `history.getTicks()` with the same connection mechanism as the JForex 4 GUI — proven stable and reliable.
  - Downloads the full range from **2005-01-01 to 2026-07-03** month by month.
  - Implements **automatic retry logic**: 3 attempts per month with exponential backoff (30s, 60s, 120s).
  - **Weekly fallback**: if a full month fails after 3 retries, the tool splits the month into 7‑day chunks and downloads each separately, then merges the results.
  - **Rate limiting**: pauses for 90 seconds after every 3500 API requests to avoid hitting Dukascopy's throttle limits.
  - **Timeout recovery**: when a `SocketTimeoutException` occurs, the tool pauses for 120 seconds before retrying.

- **Batch archiving system** (3‑year batches)
  - Automatically moves CSV files from `./ohlcv_output/` to `./archive/YYYY-YYYY/` every 36 months.
  - Clears the working directory after each archive to keep storage usage low.
  - Allows you to run the downloader continuously without manual intervention.

- **JForex4ArchiveAuditor** — Standalone archive integrity checker and backfill tool.
  - Scans every CSV file in the `./archive/` folder recursively.
  - Validates each file: existence, size (≥5KB), valid CSV header, and at least one data row.
  - If a file is missing or corrupt, it automatically **re‑downloads (backfills)** that month and replaces the defective file.
  - Updates the master progress file accordingly.
  - Designed to be run **before** migrating data to a local machine, ensuring 100% data integrity.

- **Master progress file** (`.master_download_progress.txt`)
  - Lives in the project root and **never** gets archived or deleted.
  - Tracks every month with status: `SUCCESS` or `FAILED`.
  - Enables seamless resume after interruption — simply re‑run the downloader and it skips already‑completed months.
  - Allows you to delete the `ohlcv_output/` folder or move it without losing progress.

- **One‑click fresh start script** (`start_fresh_download.sh`)
  - Automates the entire reset process: stops JForex processes, deletes all CSV files, clears cache, empties trash, compiles, and starts the downloader.
  - Useful for testing or starting over from a completely clean slate.

- **Support for `singlejartest/` examples**
  - Official Dukascopy SDK examples (`MA_Play.java`, `Main.java`, `TesterMain.java`) are preserved for future autotrading reference.
  - They do not interfere with the downloader but remain available for learning.

### Changed

- **Switched from `ITesterClient` to `IClient`** — The old historical downloader (`DukascopyHistoricalDownloader.java`) used `ITesterClient` and suffered from hardcoded 45‑second timeouts. The new version uses `IClient`, which is the same client used by the JForex 4 GUI and has proven to work reliably.

- **JNLP endpoint** updated from `demo_3/jforex_3.jnlp` to `demo_4/jforex_4.jnlp` to align with JForex 4 API.

- **Logging** — All log messages are now in **100% English**, with clear timestamps and structured output for easier debugging.

- **Project structure** — Cleaned up obsolete files and folders:
  - Removed `build.log`, old `logs/` folder, and stale progress files.
  - Kept only `src/main/java/com/rws/dukas/` (2 core files) and `src/main/java/singlejartest/` (examples).

- **Default range** remains **2005-01-01 to 2026-07-03** but is now fully configurable via constants at the top of `JForex4Downloader.java`.

- **CSV format** — Now includes `AV_SPREAD` calculated from the `askClose - bidClose` of each minute bar, sourced directly from `ITick` data (accurate).

### Fixed

- **Hardcoded timeout in `ITesterClient`** — The old approach using `getBars()` had a 45‑second timeout hardcoded inside `CurvesJsonProtocolHandler` that could not be overridden. The new `IClient`‑based approach avoids that class entirely.

- **Intermittent connection failures** — The new retry logic (with exponential backoff and weekly fallback) makes the downloader resilient to transient network issues.

- **Rate limit throttling** — Previously, hitting Dukascopy's API rate limit would cause unexplained failures. The new rate‑limiting logic (pause after 3500 requests) prevents this.

- **Batch archiving gaps** — Fixed a bug where `batchStartYear` would advance incorrectly, causing the downloader to skip years. The archiving logic now advances by `BATCH_YEARS` instead of relying on the current loop variable.

- **Master progress duplication** — `markMasterProgress()` now rewrites the entire progress file on each update, eliminating duplicate entries.

- **Pre‑archive audit** — The audit no longer re‑downloads months that are already marked `SUCCESS` in the master progress file, even if the CSV file is not present in `ohlcv_output/` (because it may have been archived already). The audit **trusts the master progress file**.

### Removed

- `DukascopyHistoricalDownloader.java` — Obsolete JForex 3 SDK downloader that consistently timed out. Replaced by `JForex4Downloader.java`.

- `LiveTickTest.java` — No longer needed; the main downloader includes connection testing.

- `LiveCaptureStrategy.java` — Merged into the main `JForex4Downloader` logic.

- `build.log` and old `logs/` folder — Cleaned up to reduce clutter.

- `ohlcv_output/.download_progress.txt` — Replaced by `.master_download_progress.txt` in the project root.

### Known Limitations

- **Historical datafeed endpoint** — All tools (including this one) use the same Dukascopy datafeed endpoint (`datafeed.66proxymity88.net`). There is no alternative. If your VPS has poor routing to this endpoint, you may still experience sporadic timeouts. The tool's retry logic mitigates this, but extreme network conditions may cause some months to remain `FAILED`.

- **`AV_SPREAD` calculation** — The spread is calculated from the `askClose` and `bidClose` of each minute bar. This is a close approximation but does not capture every tick‑level spread change.

- **Demo account expiry** — The credentials `DEMO2YciXg` / `YciXg` are not permanent. If they expire, you will need to create a new demo account on the Dukascopy website and update the `USERNAME` and `PASSWORD` constants in both Java files.

- **Storage** — While the tool archives every 3 years, the `archive/` folder will eventually contain all 21 years of data (~3 GB total). This is well within 256 GB of storage, but you may want to migrate older batches to a local machine if storage is a concern.

### Planned

- **Multi‑instrument support** — Currently only EUR/USD is tested. Future versions may allow downloading other instruments (e.g., GBP/USD, USD/JPY) by changing the `INSTRUMENT` constant.

- **GUI progress dashboard** — A simple terminal‑based progress bar showing current month, success/failure rates, and estimated time remaining.

- **Auto‑retry on startup** — If the downloader detects that some months are `FAILED` in the master progress file, it could automatically retry them without waiting for the Phase 2 backfill pass.

- **Compressed archive output** — Option to output CSV files in `.gz` or `.zip` format to save even more storage space.

- **Webhook notifications** — Send a notification (e.g., to Discord or Telegram) when the download completes or when a month fails permanently.

---

## [1.0.0] - 2026-07-08 (Previous Version)

### Added (v1.0.0)

- Live tick capture strategy (`singlejartest.LiveCaptureStrategy`)
- Historical downloader using `ITesterClient` (`DukascopyHistoricalDownloader.java`)
- Quick connection test (`LiveTickTest.java`)
- Official Dukascopy examples (`Main.java`, `MA_Play.java`)
- Maven build with fat JAR support

### Fixed (v1.0.0)

- `Period` class ambiguity (explicit imports)
- `startStrategy` signature mismatches
- `getHistory()` method lookup (now uses `context.getHistory()`)
- Chunked historical download with 3‑minute pauses to mitigate timeouts

### Known Limitations (v1.0.0)

- Historical downloader timeouts on some VPS networks (hardcoded 45s timeout in SDK)
- `AV_SPREAD` was `0.0` in historical mode (no spread data from `IBar`)
- Demo credentials would eventually expire
- No automatic backfill or retry logic

---

**Note:** Version 2.0.0 was a **major rewrite** that replaced the old `ITesterClient`-based downloader. Version 2.1.0 keeps all that solid work intact but adds a much-needed, practical guide to actually getting the data onto your own computer.