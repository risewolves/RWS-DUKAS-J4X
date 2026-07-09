If the user answers `y`, they can choose:
- `f` — Foreground mode (logs appear in the terminal).
- `s` — Screen mode (runs in background, survives SSH disconnect, recommended for VPS).
- This eliminates the extra step of manually starting the download after configuration.

- **Enhanced `reset_project.sh` — Clean Backup Files and Logs**
- The script now automatically removes all `.bak_*` configuration backup files created by `configure.sh` (e.g., `JForex4Downloader.java.bak_20260709_120000`).
- The script now **cleans the `logs/` folder** by removing all old runtime log files (keeping only the folder structure).
- This ensures a truly clean slate after a reset, leaving no temporary files behind.

- **Complete, Fully Compilable `JForex4Downloader.java`**
- The previous incomplete skeleton version (with placeholders) has been replaced with the full, working implementation.
- All methods are now complete: retry logic, rate limiting, batch archiving, weekly fallback, and dynamic filename handling.
- The file now compiles successfully without errors.

- **`JForex4ArchiveAuditor.java` Updated for Dynamic Filenames**
- All file operations (`checkArchiveFile()`, `moveSingleFileToArchive()`, `processMonthWithRetry()`) now use `INSTRUMENT.name()` for filename construction.
- The auditor correctly scans, verifies, and backfills files for the currently configured instrument.

### Changed

- **README.md Updated to Version 3.1.0**
- Added explanation of dynamic filename generation and why it matters.
- Updated the "What You Get" section to show dynamic filenames.
- Enhanced the "How to Configure" section with details about instrument validation, auto-correction, and auto-start download.
- Updated the "How to Clean Everything and Start Fresh" section to reflect the enhanced `reset_project.sh` capabilities (cleaning `.bak_*` files and logs).
- Updated the Project Structure diagram to include the `logs/` folder.
- Added a "What's New in v3.1.0" subsection in the configuration guide.
- Updated all command references and examples to be consistent with the new features.

- **CHANGELOG.md Updated**
- Added the new `[3.1.0]` entry at the top with comprehensive details.
- Preserved all previous version history below.

- **`reset_project.sh` Now Cleans Temporary Files**
- Step count increased from 7 to 8 steps.
- Step 4: Removes `.bak_*` backup files.
- Step 5: Cleans the `logs/` folder.
- The script now leaves the project in a truly pristine state, comparable to a fresh git clone.

- **`configure.sh` Now More Resilient**
- The script no longer fails when `CURRENT_INSTRUMENT` is invalid.
- It now detects invalid current values and prompts the user for a valid instrument.

### Fixed

- **Hardcoded Filename Bug in CSV Generation**
- Previously, CSV files were always named with `EURUSD_` regardless of the configured instrument (or `BTCUSD_` in the auditor).
- This caused data corruption when switching instruments (e.g., downloading GBPUSD but writing to BTCUSD files).
- Now fixed: all filenames use `INSTRUMENT.name()` dynamically.

- **Compilation Errors in `JForex4Downloader.java`**
- The previous version contained placeholders (`/* ... same as before */`) and missing method implementations.
- These placeholders caused compilation errors (missing methods, missing imports, constructor mismatches).
- Now fixed: all methods are fully implemented and the file compiles successfully.

- **`configure.sh` Infinite Loop on Invalid Instrument**
- Previously, if `CURRENT_INSTRUMENT` was malformed, the script would loop indefinitely when pressing Enter.
- Now fixed: the script detects invalid current values and forces the user to enter a valid instrument.

- **`configure.sh` Not Accepting Short Symbols**
- Previously, users had to type `Instrument.GBPUSD` exactly.
- Now fixed: users can type `GBPUSD` and the script automatically adds the `Instrument.` prefix.

- **`reset_project.sh` Leaving Backup Files and Logs Behind**
- Previously, `reset_project.sh` only deleted data, cache, and progress files.
- Backup files (`.bak_*`) and old logs were left behind, cluttering the project.
- Now fixed: both are automatically removed during reset.

### Removed

- **Hardcoded "EURUSD" and "BTCUSD" Strings in File Operations**
- All occurrences of `"EURUSD_"` and `"BTCUSD_"` in filename construction have been replaced with `INSTRUMENT.name() + "_"`.

### Known Limitations (as of 3.1.0)

- **Datafeed endpoint** — The tool uses `datafeed.66proxymity88.net`, which is the **only** source for Dukascopy historical data. There is no alternative.
- **Network timeouts** — If your VPS has poor routing to Dukascopy's servers, some months may still fail. The tool's retry logic and weekly fallback mitigate this, but extreme cases may require a VPN or moving your VPS to Europe.
- **Demo accounts expire** — The credentials are not permanent. You may need to renew them on the Dukascopy website every few months. Use `./configure.sh` to update them easily.
- **`AV_SPREAD`** — Calculated from the ask and bid **close** prices of the minute bar, not from every individual tick. For most purposes, this is sufficient.
- **File size limit** — Terabox has a 2 GB per file limit. Do not zip the entire `archive/` folder into one file. Keep individual CSV files (< 20 MB each).
- **Instrument availability** — Not all instruments are available on demo accounts. Stick to major forex pairs (EURUSD, GBPUSD, USDJPY) for best results.

### Planned

- **Multi‑instrument support** — Currently only one instrument can be configured at a time. Future versions may allow downloading multiple instruments in a single run.
- **GUI progress dashboard** — A simple terminal‑based progress bar showing current month, success/failure rates, and estimated time remaining.
- **Auto‑retry on startup** — If the downloader detects that some months are `FAILED` in the master progress file, it could automatically retry them without waiting for the Phase 2 backfill pass.
- **Compressed archive output** — Option to output CSV files in `.gz` or `.zip` format to save even more storage space.
- **Webhook notifications** — Send a notification (e.g., to Discord or Telegram) when the download completes or when a month fails permanently.

---

## [3.0.0] - 2026-07-09

### Added

- **`configure.sh` — Interactive Configuration Tool**
- A user-friendly, interactive Bash script that simplifies changing the instrument symbol, date range, and Dukascopy account credentials.
- **No manual code editing required** — the script handles all file updates automatically.
- Validates all user inputs: months (1-12), days (1-31), and years (4-digit format).
- Displays current settings before prompting for new values.
- Allows users to press `Enter` to keep the current value for any setting.
- Creates timestamped backups of both Java files before applying changes.
- Automatically compiles the project after configuration (`mvn clean compile`).
- Provides clear, color-coded feedback for every step.
- Shows a summary of changes before asking for confirmation.

- **Read-Only System Settings Display**
- The `configure.sh` script now displays the following settings as **read-only information**:
- `JNLP_URL` — Dukascopy connection endpoint (rarely changes)
- `BATCH_YEARS` — Archiving interval (3 years by default)
- `OUTPUT_DIR` — Temporary working directory
- `ARCHIVE_BASE_DIR` — Archive destination folder
- These values are shown for reference but **not modified** by the script.
- Users only need to focus on what they actually care about: symbol, date range, and credentials.

- **User-Configurable Settings (9 Simple Questions)**
- The script now prompts for only **9 essential settings**:
1. Instrument (Symbol) — e.g., `Instrument.GBPUSD`
2. Start Year — e.g., `2010`
3. Start Month — `1-12`
4. Start Day — `1-31`
5. End Year — e.g., `2025`
6. End Month — `1-12`
7. End Day — `1-31`
8. Dukascopy Username
9. Dukascopy Password
- This simplifies the configuration process and reduces user confusion.

- **Input Validation**
- The script now validates all numeric inputs:
- Month must be between 1 and 12.
- Day must be between 1 and 31.
- Year must be exactly 4 digits.
- Invalid inputs trigger an error message and re-prompt for the correct value.
- Prevents compilation errors caused by invalid date values.

- **Automatic Backup System**
- Before applying any changes, the script creates backups of both Java files:
- `JForex4Downloader.java.bak_YYYYMMDD_HHMMSS`
- `JForex4ArchiveAuditor.java.bak_YYYYMMDD_HHMMSS`
- Backups are timestamped, so multiple backups can coexist.
- Users can easily restore previous configurations if needed.

- **Post-Configuration Auto-Compilation**
- After successfully applying new settings, the script automatically runs `mvn clean compile`.
- Ensures that the project is immediately ready to run with the new configuration.
- Eliminates the "I forgot to compile" problem.

### Changed

- **README.md Complete Overhaul for v3.0.0**
- Added a new "How to Configure — NEW!" section with detailed `configure.sh` usage instructions.
- Reorganized the Table of Contents to include the new configuration section.
- Updated the Quick Start guide to include `./configure.sh` as an optional first step.
- Added a "What You DON'T Need to Change" section to clarify which settings are read-only.
- Enhanced the "How to Handle Errors" section with specific guidance for invalid symbol names.
- Updated the "How to Clean Everything and Start Fresh" section to include `configure.sh` as a data-preserving option.
- Added a "Restoring Previous Configuration" subsection with clear restoration steps.
- Updated the final summary to reflect the new workflow: Configure → Build → Run.

- **CHANGELOG.md Updated**
- Added the new `[3.0.0]` entry at the top with comprehensive details.
- Preserved all previous version history below.

- **Project Focus Realignment**
- The project now prioritizes **user experience** and **accessibility** over raw technical complexity.
- Configuration is now a first-class feature, not an afterthought.
- The tool is now approachable for non-developers who just want to download data.

### Fixed

- **No manual code editing required**
- Previously, users had to manually edit Java files to change symbols, dates, or credentials.
- This was error-prone and intimidating for non-developers.
- The new `configure.sh` script eliminates this entirely.

- **Reduced user error**
- Previously, users could enter invalid dates (e.g., month 13) and cause compilation failures.
- The script now validates all inputs and prevents invalid values from being saved.

- **No more forgotten compilation steps**
- Previously, users would change Java files and forget to run `mvn clean compile`.
- The script now automatically compiles the project after every configuration change.

### Removed

- **Manual "How to Update Credentials" guidance**
- The README previously had a section on manually editing Java files for credentials.
- This has been replaced with the `./configure.sh` approach, which is simpler and safer.

### Known Limitations (as of 3.0.0)

- **Datafeed endpoint** — The tool uses `datafeed.66proxymity88.net`, which is the **only** source for Dukascopy historical data. There is no alternative.
- **Network timeouts** — If your VPS has poor routing to Dukascopy's servers, some months may still fail. The tool's retry logic and weekly fallback mitigate this, but extreme cases may require a VPN or moving your VPS to Europe.
- **Demo accounts expire** — The credentials are not permanent. You may need to renew them on the Dukascopy website every few months. Use `./configure.sh` to update them easily.
- **`AV_SPREAD`** — Calculated from the ask and bid **close** prices of the minute bar, not from every individual tick. For most purposes, this is sufficient.
- **File size limit** — Terabox has a 2 GB per file limit. Do not zip the entire `archive/` folder into one file. Keep individual CSV files (< 20 MB each).
- **Instrument availability** — Not all instruments are available on demo accounts. Stick to major forex pairs (EURUSD, GBPUSD, USDJPY) for best results.

### Planned

- **Multi‑instrument support** — Currently only one instrument can be configured at a time. Future versions may allow downloading multiple instruments in a single run.
- **GUI progress dashboard** — A simple terminal‑based progress bar showing current month, success/failure rates, and estimated time remaining.
- **Auto‑retry on startup** — If the downloader detects that some months are `FAILED` in the master progress file, it could automatically retry them without waiting for the Phase 2 backfill pass.
- **Compressed archive output** — Option to output CSV files in `.gz` or `.zip` format to save even more storage space.
- **Webhook notifications** — Send a notification (e.g., to Discord or Telegram) when the download completes or when a month fails permanently.

---

## [2.2.0] - 2026-07-09

### Added

- **`reset_project.sh` — New clean-slate reset script**
- A dedicated script that performs a complete project reset **without** automatically starting the downloader.
- Stops all running JForex processes.
- Deletes all CSV files from `ohlcv_output/` and `archive/`.
- Deletes the master progress file (`.master_download_progress.txt`).
- Empties the system Trash/Recycle Bin.
- Clears JForex cache (`/root/JForex/cache`, `/root/JForex/data`, `/root/JForex/tmp`).
- Recompiles the project (`mvn clean compile`).
- Does **not** start the downloader automatically, giving you full control to run it manually (e.g., inside `screen` or `tmux`).
- Includes safety checks: verifies the project directory exists and Maven is installed before proceeding.
- Provides clear, color-coded feedback for each step.

- **Comprehensive Table of Contents in README**
- The `README.md` now includes a full table of contents with 20 sections.
- Allows users to quickly jump to any topic without scrolling through the entire document.
- Improves navigation and discoverability.

- **"Quick Start" section in README**
- A concise, 5‑step guide for impatient users who just want to get the tool running immediately.
- Covers compilation, running in `screen`, detaching, and re-attaching.
- Reduces friction for first-time users.

- **Detailed explanation of `BATCH_YEARS` logic**
- The README now includes a full explanation of how the 3‑year archiving system works.
- Describes why 3 years was chosen (450 MB per batch) and how to change it.
- Helps users understand and customize the archiving behavior.

- **Detailed explanation of weekly fallback mechanism**
- The README now explains how the tool splits a failed month into 7‑day chunks.
- Describes why this works (smaller payloads = faster transfers = fewer timeouts).
- Gives users confidence in the tool's resilience.

- **"How to Update Credentials" section in README**
- Step‑by‑step guide for updating the `USERNAME` and `PASSWORD` constants in both Java files.
- Includes exact file names and line locations.
- Saves users from hunting through code to find where credentials are stored.

### Changed

- **README restructuring**
- The main documentation has been completely reorganized for better flow and readability.
- Added a clear distinction between `start_fresh_download.sh` and `reset_project.sh` — two scripts that serve different purposes.
- Expanded all sections with more detailed examples and explanations.
- Added more troubleshooting entries (e.g., "Maven not found", "Java version mismatch", "Permission denied").
- Updated all command references to be consistent with the new project structure.

- **CHANGELOG restructuring**
- The `CHANGELOG.md` now follows the [Keep a Changelog](https://keepachangelog.com/) format more strictly.
- Added clearer section headers (`### Added`, `### Changed`, `### Fixed`, `### Removed`) for every version.
- Improved the readability and consistency of all entries.

- **`start_fresh_download.sh` improvements**
- The existing script has been refined with additional safety checks.
- It now checks that the project directory exists and that Maven is installed.
- Improved error messages and color-coded output.
- The script still performs the full reset and automatically starts the downloader, but now handles edge cases more gracefully.

### Fixed

- **Missing error handling in scripts**
- Both `reset_project.sh` and `start_fresh_download.sh` now check if the project directory exists before attempting to `cd` into it.
- Both scripts now check if Maven is installed before running `mvn clean compile`.
- Prevents cryptic error messages and provides clear guidance when dependencies are missing.

- **README inconsistencies**
- Fixed outdated references to `start_fresh_download.sh` where `reset_project.sh` should have been mentioned.
- Corrected command examples that were not fully accurate.
- Updated the file structure diagram to reflect the addition of `reset_project.sh`.

### Removed

- **Redundant instructions in README**
- Streamlined sections that were previously duplicated across the document.
- Consolidated overlapping explanations (e.g., "How to Run" and "How to Run in Background" are now clearly separate but cross‑referenced).

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

### Known Limitations (as of 2.0.0)

- **Historical datafeed endpoint** — All tools (including this one) use the same Dukascopy datafeed endpoint (`datafeed.66proxymity88.net`). There is no alternative. If your VPS has poor routing to this endpoint, you may still experience sporadic timeouts. The tool's retry logic mitigates this, but extreme network conditions may cause some months to remain `FAILED`.

- **`AV_SPREAD` calculation** — The spread is calculated from the `askClose` and `bidClose` of each minute bar. This is a close approximation but does not capture every tick‑level spread change.

- **Demo account expiry** — The credentials `DEMO2YciXg` / `YciXg` are not permanent. If they expire, you will need to create a new demo account on the Dukascopy website and update the `USERNAME` and `PASSWORD` constants in both Java files.

- **Storage** — While the tool archives every 3 years, the `archive/` folder will eventually contain all 21 years of data (~3 GB total). This is well within 256 GB of storage, but you may want to migrate older batches to a local machine if storage is a concern.

### Planned (as of 2.0.0)

- **Multi‑instrument support** — Currently only EUR/USD is tested. Future versions may allow downloading other instruments (e.g., GBP/USD, USD/JPY) by changing the `INSTRUMENT` constant.

- **GUI progress dashboard** — A simple terminal‑based progress bar showing current month, success/failure rates, and estimated time remaining.

- **Auto‑retry on startup** — If the downloader detects that some months are `FAILED` in the master progress file, it could automatically retry them without waiting for the Phase 2 backfill pass.

- **Compressed archive output** — Option to output CSV files in `.gz` or `.zip` format to save even more storage space.

- **Webhook notifications** — Send a notification (e.g., to Discord or Telegram) when the download completes or when a month fails permanently.

---

## [1.0.0] - 2026-07-08 (Previous Version)

### Added

- Live tick capture strategy (`singlejartest.LiveCaptureStrategy`)
- Historical downloader using `ITesterClient` (`DukascopyHistoricalDownloader.java`)
- Quick connection test (`LiveTickTest.java`)
- Official Dukascopy examples (`Main.java`, `MA_Play.java`)
- Maven build with fat JAR support

### Fixed

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

## Summary of Major Releases

| Version | Date | Key Focus |
|---------|------|-----------|
| **3.1.0** | 2026-07-09 | **Critical fixes**: Dynamic filenames (no more hardcoded EURUSD/BTCUSD), instrument validation in `configure.sh`, auto-start download, enhanced `reset_project.sh` (cleans backups and logs), full compilable code. |
| **3.0.0** | 2026-07-09 | Added `configure.sh` — interactive configuration tool. Simplified user experience with only 9 essential questions. Read-only display of system settings. Input validation. Auto-backup. Auto-compilation. |
| **2.2.0** | 2026-07-09 | Added `reset_project.sh`, comprehensive README overhaul, detailed explanations of archiving and fallback logic |
| **2.1.0** | 2026-07-08 | Platform-specific data transfer guide, improved documentation clarity |
| **2.0.0** | 2026-07-08 | Complete rewrite: JForex 4 API, batch archiving, Archive Auditor, master progress, retry logic, weekly fallback |
| **1.0.0** | 2026-07-08 | Initial release with live capture and basic historical downloader (ITesterClient) |

---

**Note:** Version 3.1.0 is a **critical bugfix release** that addresses the hardcoded filename issue that caused data corruption when switching instruments. All users who download multiple instruments are strongly encouraged to upgrade to version 3.1.0 or later. The dynamic filename feature ensures that each instrument's data is stored in its own correctly named CSV files.
