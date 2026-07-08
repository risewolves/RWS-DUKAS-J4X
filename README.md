# RWS-DUKAS-J4X — Tick-to-1-Minute OHLCV Exporter (v2.1.0)

**Version 2.1.0** — A complete, self-contained tool to download 21 years of EUR/USD tick data from Dukascopy and convert it into monthly 1‑minute OHLCV CSV files.

---

## What This Tool Does

This Java application connects to Dukascopy's JForex platform, pulls raw tick data for EUR/USD from **2005‑01‑01 to 2026‑07‑03**, and converts it into clean 1‑minute OHLCV bars (Open, High, Low, Close, Volume) with average spread. The output is one CSV file per month.

It is designed for **unattended, long‑running operation on a VPS**. You run it **once**, and it handles everything automatically:

- Downloads month by month with retries (3 attempts per month).
- If a month fails, it falls back to weekly chunks.
- Every 3 years (36 months), it archives the CSV files and clears the working directory to keep storage usage low.
- If interrupted, it resumes exactly where it left off.
- After all months are processed, it automatically retries any failed months (backfill).
- You can also run a separate **Archive Auditor** to verify file integrity and backfill missing/corrupt files before moving data to your local machine.

---

## What You Get (Output Format)

Each CSV file has exactly 7 columns:

| Column      | Description                                     |
|-------------|-------------------------------------------------|
| `DATETIME`  | UTC timestamp of the bar start (YYYY-MM-DD HH:MM:SS) |
| `OPEN`      | First bid price in that minute                  |
| `HIGH`      | Highest bid price in that minute                |
| `LOW`       | Lowest bid price in that minute                 |
| `CLOSED`    | Last bid price in that minute                   |
| `AV_SPREAD` | Average spread (ask minus bid) in that minute   |
| `VOLUME`    | Total bid volume in that minute (in units)      |

Example row:
```
DATETIME,OPEN,HIGH,LOW,CLOSED,AV_SPREAD,VOLUME
2005-01-02 22:00:00,1.35480,1.35490,1.35464,1.35480,0.00010,103.3
```

Files are named: `EURUSD_YYYY-MM_1min_OHLCV.csv`

---

## Prerequisites (What You Need)

- **Java 21** (OpenJDK or Oracle) — install with: `sudo apt install openjdk-21-jdk`
- **Maven 3.6+** — install with: `sudo apt install maven`
- A Dukascopy demo account. The code includes demo credentials, but they may expire. If you see connection errors, create a new demo account at [Dukascopy](https://www.dukascopy.com) and update the `USERNAME` and `PASSWORD` constants in the Java files.

---

## How to Build

```bash
cd ~/RWS-DUKAS-J4X
mvn clean compile
```

---

## How to Run

### Option 1 — Run in Foreground (For Testing)

```bash
cd ~/RWS-DUKAS-J4X
mvn exec:java -Dexec.mainClass="com.rws.dukas.JForex4Downloader"
```

You will see logs on the screen. Press `Ctrl+C` to stop. If you stop it, **just run the same command again** — it will resume from where it stopped.

### Option 2 — Run in Background (Recommended for VPS)

Since the download takes several hours (or overnight), you should run it in the background so it continues even if your SSH session disconnects.

**Using `screen` (recommended):**
```bash
# Start a new screen session
screen -S dukas-download

# Inside the screen, run the downloader
cd ~/RWS-DUKAS-J4X
mvn exec:java -Dexec.mainClass="com.rws.dukas.JForex4Downloader"

# Detach from screen: press Ctrl+A, then D
# To re-attach later: screen -r dukas-download
```

**Using `nohup` (simpler, no re-attach):**
```bash
cd ~/RWS-DUKAS-J4X
nohup mvn exec:java -Dexec.mainClass="com.rws.dukas.JForex4Downloader" > download.log 2>&1 &

# View the log: tail -f download.log
```

### Option 3 — Run Archive Auditor (Post-Download Verification)

After the main download finishes, run this to check all CSV files in `archive/` for corruption or missing data. It will automatically backfill (re-download) any problematic months.

```bash
cd ~/RWS-DUKAS-J4X
mvn exec:java -Dexec.mainClass="com.rws.dukas.JForex4ArchiveAuditor"
```

**When to run this:**
- After the main download completes.
- Before you copy the data to your local machine.
- If you suspect some files might be incomplete.

---

## How to Read Logs

### If running in foreground (Option 1)
Logs appear directly on your terminal.

### If running with `screen` (Option 2)
Logs appear inside the screen session. Re-attach with `screen -r dukas-download`.

### If running with `nohup` (Option 2)
Logs are written to `download.log` (or whatever file you specified). View with:
```bash
tail -f download.log
```

### Log Format
Each log line starts with a timestamp:
```
[2026-07-08 12:34:56] FETCH: 2008-02
[2026-07-08 12:34:57]   -> Retrieved 817154 ticks.
[2026-07-08 12:34:57]   -> SUCCESS: 28799 bars.
```

Look for:
- `SUCCESS` — month downloaded successfully.
- `FAILED` — month failed (will be retried in Phase 2).
- `SKIP` — month already downloaded, skipping.
- `RATE LIMIT` — pause triggered (normal, expected).
- `TIMEOUT` — network timeout (tool will pause and retry).

---

## How to Resume After Interruption

**Just run the exact same command again.**

```bash
cd ~/RWS-DUKAS-J4X
mvn exec:java -Dexec.mainClass="com.rws.dukas.JForex4Downloader"
```

The tool reads the `.master_download_progress.txt` file, skips all months already marked `SUCCESS`, and continues from the next missing month.

**No manual calculation needed. No data loss. No duplicate downloads.**

---

## How to Handle Errors

### Error: "Connection refused" or "Read timed out"
- The tool has built-in retry logic (3 attempts) and 120-second timeout recovery.
- This is usually a network issue. If it keeps failing, try running with a VPN or move your VPS to a different region (Switzerland/Germany tend to have better routing to Dukascopy).

### Error: "Rate limit exceeded" or "429"
- The tool automatically pauses for 90 seconds after every 3500 API requests.
- No action needed — just let it run.

### Error: "No ticks received"
- Your demo account may have expired.
- Go to Dukascopy, create a new demo account, and update `USERNAME` and `PASSWORD` in both Java files.

### Error: "Out of disk space"
- The tool archives every 3 years and clears the working directory, so it never holds more than ~3 years of CSV files at once (about 450 MB).
- If you have less than 450 MB free, reduce `BATCH_YEARS` to 1 or 2 in the code.

---

## How to Verify the Results

### 1. Check the final report (printed at the end)

The tool prints a summary like this:
```
============================================================
FINAL REPORT
Total months processed: 258
Successful: 252
Failed: 6
PERMANENTLY FAILED MONTHS:
  - 2008-10
  - 2012-03
  - ...
```

### 2. Check the master progress file

```bash
cat .master_download_progress.txt
```

Each line shows month and status:
```
2005-01: SUCCESS
2005-02: SUCCESS
...
2008-10: FAILED
```

### 3. Check the archive folder

```bash
ls -la archive/2005-2007/
```

You should see 36 CSV files (for 2005, 2006, 2007) — one per month.

### 4. Run the Archive Auditor (for full verification)

```bash
mvn exec:java -Dexec.mainClass="com.rws.dukas.JForex4ArchiveAuditor"
```

It will scan every file in `archive/` and report any issues.

---

## How to Download the Archive to Your Local Machine

### For Mac or Linux (Terminal)

**Using `rsync` (recommended — supports resume):**
```bash
mkdir -p ~/Documents/ForexData
rsync -avz --progress root@<YOUR_VPS_IP>:/root/RWS-DUKAS-J4X/archive/ ~/Documents/ForexData/archive/
```
Replace `<YOUR_VPS_IP>` with your VPS's IP address.

**Using `scp` (simpler, no resume):**
```bash
scp -r root@<YOUR_VPS_IP>:/root/RWS-DUKAS-J4X/archive/ ~/Documents/ForexData/
```

### For Windows

**Option A — Using WinSCP (graphical):**
1. Download and install [WinSCP](https://winscp.net/).
2. Connect to your VPS using SFTP.
3. Navigate to `/root/RWS-DUKAS-J4X/archive/`.
4. Drag the folder to your local machine.

**Option B — Using PowerShell/CMD with scp:**
```powershell
scp -r root@<YOUR_VPS_IP>:/root/RWS-DUKAS-J4X/archive/ C:\Users\YourName\Documents\ForexData\
```

### Important Transfer Notes

- The entire `archive/` folder is about **3 GB** for 21 years of data.
- If using `rsync` and the connection drops, just run the same `rsync` command again — it will resume.
- If you plan to upload to Terabox, do **not** zip everything into one file. Terabox has a **2 GB per file limit**. Keep the individual CSV files (each < 20 MB) and upload the folder as-is.

---

## How to Restart from a Specific Month (Advanced)

If you want to restart from a specific month (for testing or re-download), delete the months you want to re-download from `.master_download_progress.txt`:

```bash
nano .master_download_progress.txt
```

Delete the lines for the months you want to re-download. Then run the downloader again.

**Example:** To re-download 2008-10, delete the line `2008-10: SUCCESS` (or `2008-10: FAILED`), save the file, and run the downloader.

---

## How to Clean Everything and Start Fresh

If you want to delete all data and start from zero, run:

```bash
cd ~/RWS-DUKAS-J4X
./start_fresh_download.sh
```

This script will:
1. Stop any running JForex processes.
2. Delete all CSV files in `ohlcv_output/` and `archive/`.
3. Delete the master progress file.
4. Empty the Trash/Recycle Bin.
5. Clear JForex cache (`/root/JForex/cache`).
6. Compile the project.
7. Start the downloader automatically.

**Warning:** This deletes **everything** — all downloaded data. Use only if you want to start over.

---

## File Structure (After a Successful Run)

```
RWS-DUKAS-J4X/
├── archive/
│   ├── 2005-2007/
│   │   ├── EURUSD_2005-01_1min_OHLCV.csv
│   │   └── ... (36 files)
│   ├── 2008-2010/
│   │   └── ... (36 files)
│   └── ... (up to 2023-2025, and partial 2026)
├── src/main/java/com/rws/dukas/
│   ├── JForex4Downloader.java
│   └── JForex4ArchiveAuditor.java
├── src/main/java/singlejartest/
│   └── (examples for future autotrading)
├── ohlcv_output/                      (usually empty after archiving)
├── .master_download_progress.txt      (tracks which months are done)
├── pom.xml
├── start_fresh_download.sh
├── README.md
└── CHANGELOG.md
```

---

## Known Limitations

- **Datafeed endpoint** — The tool uses `datafeed.66proxymity88.net`, which is the **only** source for Dukascopy historical data. There is no alternative.
- **Network timeouts** — If your VPS has poor routing to Dukascopy's servers, some months may still fail. The tool retries and uses weekly fallback, but extreme cases may require a VPN or moving your VPS to Europe.
- **Demo accounts expire** — The credentials are not permanent. You may need to renew them on the Dukascopy website.
- **`AV_SPREAD`** — Calculated from the ask and bid **close** prices of the minute bar, not from every individual tick. For most purposes, this is sufficient.

---

## License

MIT License — see the `LICENSE` file.

---

## Final Thoughts

This tool is designed to be **set and forget**. You run it once, wait, and come back to a complete dataset.

**Summary of what you need to do:**

1. **Build:** `mvn clean compile`
2. **Run (background):** `screen -S dukas-download` then `mvn exec:java -Dexec.mainClass="com.rws.dukas.JForex4Downloader"`
3. **Wait overnight.**
4. **Run Archive Auditor:** `mvn exec:java -Dexec.mainClass="com.rws.dukas.JForex4ArchiveAuditor"`
5. **Download to Mac:** `rsync -avz --progress root@<IP>:/root/RWS-DUKAS-J4X/archive/ ~/Documents/ForexData/`

That's it. Everything else is automatic.

If you encounter any issues, check the logs (see "How to Read Logs" above) and consult the troubleshooting section. The tool is designed to recover from most failures automatically.

---

**Version 2.1.0** — Reliable, automated, and documented.

---