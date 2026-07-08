# RWS-DUKAS-J4X — Tick-to-1-Minute OHLCV Exporter (v2.0.0)

**Version 2.0.0** — Production‑ready historical downloader with automatic backfill, rate limiting, and batch archiving.

This Java project connects to the Dukascopy JForex platform (using the **JForex 4 API** – `IClient`) and downloads raw tick data for **EUR/USD** from **2005‑01‑01 to 2026‑07‑03**. It converts ticks into clean **1‑minute OHLCV bars** (Open, High, Low, Close, Volume) along with the **average spread**, and saves the result as **monthly CSV files**.

Unlike previous versions, this tool is designed for **reliable, unattended operation** on VPS environments. It includes:

- **Automatic batch archiving** – keeps only 3 years of data in the working directory to manage storage.
- **Self‑healing backfill** – audits archives for missing or corrupt files and re‑downloads them automatically.
- **Rate limiting & timeout handling** – respects API limits (pause after 3500 requests) and recovers from read timeouts with a 120‑second cool‑down.
- **Master progress file** – never loses track of which months have been successfully downloaded, even across restarts.

---

## What It Does

1. Connects to Dukascopy using `IClient` (the same mechanism as the official JForex 4 GUI – proven stable).
2. For each month in the target range, it calls `history.getTicks()` to fetch **raw ticks** (bid, ask, volume).
3. Aggregates ticks into 1‑minute buckets:
   - `OPEN` – first bid price
   - `HIGH` – highest bid price
   - `LOW` – lowest bid price
   - `CLOSE` – last bid price
   - `AV_SPREAD` – average spread (ask – bid) over the minute
   - `VOLUME` – total bid volume in units
4. Writes the result to a CSV file named `EURUSD_YYYY-MM_1min_OHLCV.csv`.
5. When 36 months (3 years) have been collected, the tool **archives** all CSV files into a folder like `archive/2005-2007/` and **clears the working directory** to free space.
6. If a file is later found to be missing or corrupt, the **Archive Auditor** can automatically backfill it.

---

## Components

### 1. `JForex4Downloader.java` — Main Downloader

- Downloads the entire historical range (2005–2026) month by month.
- Implements retry logic (3 attempts with exponential backoff) and weekly chunk fallback for stubborn months.
- Respects rate limits (pause after 3500 API calls) and timeout recovery (120s cool‑down on `SocketTimeoutException`).
- Uses a master progress file (`.master_download_progress.txt`) to track success/failure so you can resume after interruption.

### 2. `JForex4ArchiveAuditor.java` — Archive Integrity Checker

- Scans all CSV files in the `archive/` folder.
- Verifies each file: existence, size (>5KB), valid header, and at least one data row.
- If a file is missing or corrupt, it **automatically backfills** the month by re‑downloading it and replacing the defective file.
- Updates the master progress file accordingly.
- Designed to be run **before** you migrate the archive to your local machine.

---

## Output Format

Every CSV has exactly 7 columns:

| Column      | Description                             |
|-------------|-----------------------------------------|
| `DATETIME`  | UTC timestamp of the bar start          |
| `OPEN`      | First bid price                         |
| `HIGH`      | Highest bid price                       |
| `LOW`       | Lowest bid price                        |
| `CLOSED`    | Last bid price                          |
| `AV_SPREAD` | Average spread (ask minus bid)          |
| `VOLUME`    | Total volume in units                   |

Example row:
```csv
DATETIME,OPEN,HIGH,LOW,CLOSED,AV_SPREAD,VOLUME
2026-07-08 07:30:00,1.14150,1.14165,1.14140,1.14155,0.00012,1500000.0
```

---

## Prerequisites

- **Java 21** (OpenJDK or Oracle)
- **Maven 3.6+**
- A Dukascopy demo account (the included credentials `DEMO2YciXg` / `YciXg` may expire; renew them on the [Dukascopy website](https://www.dukascopy.com) if needed).

---

## How to Build

```bash
cd ~/RWS-DUKAS-J4X
mvn clean compile
```

---

## How to Run

### Option 1 — Full Historical Download (Main Downloader)

This will download all months from 2005‑01 to 2026‑07, archiving every 3 years. **Run this once and leave it overnight** – it will automatically resume if interrupted.

```bash
cd ~/RWS-DUKAS-J4X
mvn exec:java -Dexec.mainClass="com.rws.dukas.JForex4Downloader"
```

**What happens during execution:**
- The tool reads the master progress file and skips already‑completed months.
- It processes month by month with automatic retries.
- Every 3 years, it archives the completed CSV files and clears the working directory.
- At the end, it performs a global backfill pass for any months that failed earlier.

### Option 2 — Archive Audit & Backfill (Post‑Download Verification)

Run this **before** moving your `archive/` folder to your local Mac. It scans all archived files and replaces any that are missing or corrupt.

```bash
cd ~/RWS-DUKAS-J4X
mvn exec:java -Dexec.mainClass="com.rws.dukas.JForex4ArchiveAuditor"
```

**When to use it:**
- After the main download finishes.
- If you suspect some CSV files may be incomplete.
- Before you copy the archive to another machine.

---

## Configuration

All key parameters are at the top of each class as `static final` constants. You can adjust:

| Parameter | Default | Description |
|-----------|---------|-------------|
| `START_YEAR`, `END_YEAR` | 2005, 2026 | Range to download. |
| `BATCH_YEARS` | 3 | How many years per archive folder. |
| `MAX_RETRIES` | 3 | Number of retry attempts per month. |
| `RATE_LIMIT_THRESHOLD` | 3500 | Pause after this many API calls. |
| `RATE_LIMIT_PAUSE_SECONDS` | 90 | Duration of rate‑limit pause. |
| `TIMEOUT_PAUSE_SECONDS` | 120 | Cool‑down after a socket timeout. |
| `ENABLE_WEEKLY_FALLBACK` | true | Whether to split months into weekly chunks on failure. |

---

## File Structure (After Cleanup)

```
RWS-DUKAS-J4X/
├── src/main/java/com/rws/dukas/
│   ├── JForex4Downloader.java          # Main downloader
│   └── JForex4ArchiveAuditor.java      # Archive integrity checker
├── src/main/java/singlejartest/        # Sample strategies (kept for future autotrading)
├── archive/
│   ├── 2005-2007/
│   │   ├── EURUSD_2005-01_1min_OHLCV.csv
│   │   └── ...
│   ├── 2008-2010/
│   └── ...
├── ohlcv_output/                       # Temporary working directory (usually empty after archiving)
├── .master_download_progress.txt       # Persistent progress tracker
├── pom.xml                             # Maven configuration
├── start_fresh_download.sh             # One‑click reset & start script
└── README.md
```

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| **No ticks received** | Check that your demo account is still active. Renew it on the Dukascopy website and update `USERNAME`/`PASSWORD` in the code. |
| **Connection refused / timeout** | The Dukascopy datafeed may be unreachable from your VPS. Try running with a VPN or move to a VPS in Switzerland/Germany. The tool includes built‑in timeout recovery (120s pause) but cannot fix severe routing issues. |
| **Out of disk space** | The tool archives every 3 years and clears the working folder, so it never holds more than ~3 years of CSV files at once. If you want smaller batches, change `BATCH_YEARS` to 1 or 2. |
| **Master progress file is lost** | If you delete `.master_download_progress.txt`, the downloader will start from scratch. Keep it safe. |

---

## Known Limitations

- The historical data feed (`datafeed.66proxymity88.net`) is the **only** source. There is no alternative endpoint.
- The SDK’s underlying HTTP client has a hardcoded 45‑second read timeout for some operations; the tool overcomes this by retrying and using smaller chunks, but extremely poor network conditions may still cause sporadic failures.
- `AV_SPREAD` is calculated from the ask and bid **close** prices of the minute bar, not from every tick. For most use cases this is sufficient.

---

## License

This project is provided under the MIT License. See the `LICENSE` file for details.

---

## Final Note

**Version 2.0.0** is the culmination of all the improvements requested:
- Reliable `IClient`-based downloading (not the flaky tester client).
- Automatic batch archiving to keep storage usage low.
- A separate audit tool to verify and backfill archives.
- Full English language, clean project structure.

If you encounter any issues, please check the logs printed to the console. The tools are designed to be self‑explanatory and recover from most failures automatically.

--- 