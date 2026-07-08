# RWS-DUKAS-J4X — Tick-to-1-Minute OHLCV Exporter (v2.1.0)

**Version 2.1.0** — Production‑ready historical downloader with automatic backfill, rate limiting, batch archiving, and **clear instructions to get your CSV files onto your local computer** (Mac, Windows, or Linux).

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

## The "Run Once" Workflow — How It Really Works

**You only need to run the downloader ONCE.** It is designed to handle everything automatically from 2005 to 2026 without any manual intervention.

Here is exactly what happens when you execute:

```bash
mvn exec:java -Dexec.mainClass="com.rws.dukas.JForex4Downloader"
```

1. **The Master Progress File**  
   - The tool checks `.master_download_progress.txt` in the project root.  
   - It skips every month that already has a `SUCCESS` status.  
   - This file is **never archived or deleted**, so even if you stop the program with `Ctrl+C` and restart it, it picks up exactly where it left off. No data is lost, and no month is downloaded twice.

2. **Continuous Month‑by‑Month Processing**  
   - The loop runs from `2005-01` up to `2026-07`.  
   - For each month, it attempts the download with built‑in retries (3 attempts) and weekly chunk fallback if needed.  
   - The status (`SUCCESS` or `FAILED`) is written to the master progress file **immediately** after each month finishes.

3. **Automatic Batch Archiving (Storage Management)**  
   - Every time 36 months (3 years) are processed, the tool automatically:  
     - Moves all CSV files from `./ohlcv_output/` to a new folder like `./archive/2005-2007/`.  
     - Clears the working directory completely.  
     - Continues downloading the next batch seamlessly.  
   - **You never need to manually archive or delete anything.** The tool ensures that only the current batch stays in the working folder, keeping storage usage low.

4. **Auto‑Backfill (Phase 2)**  
   - After the main loop finishes processing all months, the tool enters **Phase 2**.  
   - It looks at the list of months that were marked `FAILED` and **tries to download them again** (one final attempt).  
   - If they succeed, the master progress file is updated to `SUCCESS`.  
   - If they still fail, they are listed in the final report so you know exactly which months need manual attention.

5. **Interruption Recovery**  
   - If your VPS restarts, the SSH session drops, or you press `Ctrl+C` — **just run the exact same command again**.  
   - The tool reads the master progress file, skips all already‑completed months, and continues from the next missing month.  
   - There is **no setup step** and no manual calculation of where you left off.

**In summary:**  
You run **one command**, wait (overnight is recommended), and by the time it finishes, you will have:
- All 258 months of data in the `./archive/` folder.
- A clean final report showing exactly which months (if any) failed.
- Zero storage bloat in the working directory.

You do not need to run the script multiple times, you do not need to manually change date ranges, and you do not need to babysit the download. **Just run it once and let it run.**

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

## Downloading the Archived Data to Your Local Computer

Once the downloader has finished running, all your monthly CSV files are safely stored inside the `archive/` folder on your VPS. Now you need to bring them onto your **local machine** (Mac, Windows, or Linux) so you can actually use the data.

Here are the simplest and most reliable ways to do that.

### For Mac or Linux (Terminal)

Both macOS and Linux come with `scp` and `rsync` built‑in or easily installable.

**1. Using `scp` (simple, no resume)**

```bash
scp -r root@<YOUR_VPS_IP>:/root/RWS-DUKAS-J4X/archive/ /path/to/your/local/folder/
```

- Replace `<YOUR_VPS_IP>` with your VPS’s public IP address.
- The `-r` flag copies the entire folder recursively.
- Example: `scp -r root@123.45.67.89:/root/RWS-DUKAS-J4X/archive/ ~/Documents/ForexData/`

**2. Using `rsync` (recommended – supports resume)**

If the transfer gets interrupted (network drop, laptop sleep), `rsync` can pick up where it left off without re‑copying everything.

```bash
rsync -avz --progress root@<YOUR_VPS_IP>:/root/RWS-DUKAS-J4X/archive/ /path/to/your/local/folder/archive/
```

- `-a` preserves the folder structure.
- `-v` shows what’s being copied.
- `-z` compresses data during transfer (saves bandwidth).
- `--progress` shows a progress bar for each file.
- If it stops, just run the exact same command again – it will only copy the files that haven’t finished yet.

### For Windows (PowerShell or Command Prompt)

**Option A: Use `scp` (if you have OpenSSH installed)**

Windows 10/11 usually includes OpenSSH Client. Open **PowerShell** or **Command Prompt** and run:

```bash
scp -r root@<YOUR_VPS_IP>:/root/RWS-DUKAS-J4X/archive/ C:\Users\YourName\Documents\ForexData\
```

If you get an error about `scp` not found, you can install OpenSSH Client via Settings → Apps → Optional Features.

**Option B: Use WinSCP (graphical)**

1. Download and install [WinSCP](https://winscp.net/).
2. Open WinSCP, choose **SFTP** as the file protocol.
3. Enter your VPS IP, username (`root`), and password.
4. Click **Login**.
5. In the right panel, navigate to `/root/RWS-DUKAS-J4X/archive/`.
6. In the left panel, navigate to where you want to save the data on your Windows machine.
7. Select the `archive/` folder and drag it to the left panel (or click **Download**).

**Option C: Use `rsync` via WSL (Windows Subsystem for Linux)**

If you use WSL (Ubuntu or similar) on Windows, you can run the same `rsync` command as for Linux inside your WSL terminal.

### Important Notes

- The entire `archive/` folder will be **about 3 GB** in size (for 21 years of 1‑minute OHLCV data). This is usually fine even on a slow connection, but it may take a while – be patient.
- **Do not** zip the whole archive into one single file if you plan to upload it to Terabox – Terabox has a **2 GiB file size limit**. Instead, keep the individual CSV files (each is < 20 MB) and copy the folder as‑is.
- After copying, you can verify the data by opening any CSV file with Excel, Google Sheets, or a text editor.

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

**Version 2.1.0** adds clear, step‑by‑step instructions for downloading the archived CSV files to your local machine – whether you use Mac, Windows, or Linux. Everything else remains as reliable as before.

If you encounter any issues, please check the logs printed to the console. The tools are designed to be self‑explanatory and recover from most failures automatically.
---