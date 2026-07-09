# RWS-DUKAS-J4X — Tick-to-1-Minute OHLCV Exporter (v2.2.0)

**Version 2.2.0** — A complete, self-contained, and fully documented tool to download 21 years of EUR/USD tick data from Dukascopy and convert it into monthly 1‑minute OHLCV CSV files.

---

## Quick Start (For The Impatient)

If you just want to get it running right now:

```bash
# 1. Clone or navigate to the project
cd ~/RWS-DUKAS-J4X

# 2. Compile
mvn clean compile

# 3. Run in background (screen)
screen -S dukas-download
mvn exec:java -Dexec.mainClass="com.rws.dukas.JForex4Downloader"
# Press Ctrl+A, then D to detach

# 4. Check progress later
screen -r dukas-download
```

That's it. The tool will download all months from 2005‑01 to 2026‑07, archive every 3 years, and automatically retry any failed months. Come back in a few hours (or overnight) and your data will be ready.

---

## Table of Contents

1. [What This Tool Does](#what-this-tool-does)
2. [What You Get (Output Format)](#what-you-get-output-format)
3. [Prerequisites (What You Need)](#prerequisites-what-you-need)
4. [Project Structure](#project-structure)
5. [How to Build](#how-to-build)
6. [How to Run](#how-to-run)
7. [How to Run in Background (VPS)](#how-to-run-in-background-vps)
8. [How to Read Logs](#how-to-read-logs)
9. [How to Resume After Interruption](#how-to-resume-after-interruption)
10. [How to Verify the Results](#how-to-verify-the-results)
11. [How to Download the Archive to Your Local Machine](#how-to-download-the-archive-to-your-local-machine)
12. [How to Handle Errors](#how-to-handle-errors)
13. [How to Restart from a Specific Month](#how-to-restart-from-a-specific-month-advanced)
14. [How to Clean Everything and Start Fresh](#how-to-clean-everything-and-start-fresh)
15. [How the Archiving Logic Works (BATCH_YEARS)](#how-the-archiving-logic-works-batch_years)
16. [How the Weekly Fallback Works](#how-the-weekly-fallback-works)
17. [How to Update Credentials](#how-to-update-credentials)
18. [Known Limitations](#known-limitations)
19. [License](#license)
20. [Final Thoughts](#final-thoughts)

---

## What This Tool Does

This Java application connects to Dukascopy's JForex platform, pulls raw tick data for EUR/USD from **2005‑01‑01 to 2026‑07‑03**, and converts it into clean 1‑minute OHLCV bars (Open, High, Low, Close, Volume) with average spread. The output is one CSV file per month.

It is designed for **unattended, long‑running operation on a VPS**. You run it **once**, and it handles everything automatically:

- Downloads month by month with retries (3 attempts per month).
- If a month fails after 3 retries, it falls back to **weekly chunks** (7‑day slices) to isolate and recover from network issues.
- Every 3 years (36 months), it archives the CSV files and clears the working directory to keep storage usage low.
- If interrupted (e.g., SSH drops, `Ctrl+C`), it resumes exactly where it left off.
- After all months are processed, it automatically retries any failed months (Phase 2 backfill).
- You can also run a separate **Archive Auditor** to verify file integrity and backfill missing/corrupt files before moving data to your local machine.

**The tool is built on the JForex 4 API (`IClient`)**, which uses the same connection mechanism as the official JForex 4 GUI — proven stable and reliable. It does **not** use the problematic JForex 3 SDK (`ITesterClient`) that caused hardcoded 45‑second timeouts.

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

Each file is typically **5–15 MB** depending on the month (more trading days = larger file). The entire 21‑year dataset is about **3 GB**.

---

## Prerequisites (What You Need)

| Requirement | Version | Installation Command |
|-------------|---------|----------------------|
| **Java** | 21 (OpenJDK or Oracle) | `sudo apt install openjdk-21-jdk` |
| **Maven** | 3.6+ | `sudo apt install maven` |
| **screen** (recommended for background) | Any | `sudo apt install screen` |
| **Dukascopy demo account** | Active | [Create one here](https://www.dukascopy.com) |

The code includes demo credentials (`DEMO2YciXg` / `YciXg`), but they may expire. If you see connection errors, create a new demo account and update the credentials (see [How to Update Credentials](#how-to-update-credentials)).

---

## Project Structure

```
RWS-DUKAS-J4X/
├── src/
│   └── main/
│       └── java/
│           ├── com/
│           │   └── rws/
│           │       └── dukas/
│           │           ├── JForex4Downloader.java        # MAIN DOWNLOADER
│           │           └── JForex4ArchiveAuditor.java    # ARCHIVE INTEGRITY CHECKER
│           └── singlejartest/                            # Official SDK examples (kept for future autotrading)
│               ├── MA_Play.java
│               ├── Main.java
│               └── TesterMain.java
├── archive/                                               # WHERE CSV FILES ARE ARCHIVED (3-YEAR BATCHES)
│   ├── 2005-2007/
│   │   ├── EURUSD_2005-01_1min_OHLCV.csv
│   │   └── ... (36 files)
│   ├── 2008-2010/
│   │   └── ...
│   └── ...
├── ohlcv_output/                                          # TEMPORARY WORKING DIRECTORY (USUALLY EMPTY AFTER ARCHIVING)
├── .master_download_progress.txt                         # PERSISTENT PROGRESS TRACKER (NEVER DELETE UNLESS RESETTING)
├── pom.xml                                                # MAVEN CONFIGURATION
├── start_fresh_download.sh                               # ONE-CLICK RESET + DOWNLOAD (DELETES EVERYTHING + STARTS DOWNLOAD)
├── reset_project.sh                                      # ONE-CLICK RESET ONLY (DELETES EVERYTHING, COMPILES, BUT DOES NOT START DOWNLOAD)
├── README.md                                              # THIS FILE
└── CHANGELOG.md                                           # VERSION HISTORY
```

---

## How to Build

```bash
cd ~/RWS-DUKAS-J4X
mvn clean compile
```

If Maven reports errors about `JAVA_HOME`, set it explicitly:
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
mvn clean compile
```

---

## How to Run

### Option 1 — Run in Foreground (For Quick Testing)

```bash
cd ~/RWS-DUKAS-J4X
mvn exec:java -Dexec.mainClass="com.rws.dukas.JForex4Downloader"
```

You will see logs on the screen. Press `Ctrl+C` to stop. If you stop it, **just run the same command again** — it will resume from where it stopped.

**Use this option** if you want to watch the progress in real-time and don't mind keeping the terminal open.

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

**Using `tmux` (alternative to screen):**
```bash
tmux new -s dukas-download
cd ~/RWS-DUKAS-J4X
mvn exec:java -Dexec.mainClass="com.rws.dukas.JForex4Downloader"
# Detach: Ctrl+B, then D
# Re-attach: tmux attach -t dukas-download
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
- If you suspect some files might be incomplete (e.g., after a power outage).

---

## How to Run in Background (VPS)

Already covered in **Option 2** above. The key commands are:

| Tool | Start | Detach | Re-attach |
|------|-------|--------|-----------|
| **screen** | `screen -S dukas-download` | `Ctrl+A, D` | `screen -r dukas-download` |
| **tmux** | `tmux new -s dukas-download` | `Ctrl+B, D` | `tmux attach -t dukas-download` |
| **nohup** | `nohup ... &` | N/A | `tail -f download.log` |

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

### What to Look For
| Keyword | Meaning |
|---------|---------|
| `SUCCESS` | Month downloaded successfully. |
| `FAILED` | Month failed (will be retried in Phase 2). |
| `SKIP` | Month already downloaded, skipping. |
| `RATE LIMIT` | Pause triggered (normal, expected). |
| `TIMEOUT` | Network timeout (tool will pause and retry). |
| `BACKFILL` | Tool is re-downloading a failed month. |
| `PHASE 2` | Final retry pass for all failed months. |
| `FINAL REPORT` | End-of-run summary. |

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

## How to Verify the Results

### 1. Check the final report (printed at the end of the run)

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

## How to Handle Errors

### Error: "Connection refused" or "Read timed out"
- The tool has built-in retry logic (3 attempts) and 120-second timeout recovery.
- This is usually a network issue. If it keeps failing, try running with a VPN or move your VPS to a different region (Switzerland/Germany tend to have better routing to Dukascopy).
- Check that your demo account is still active.

### Error: "Rate limit exceeded" or "429"
- The tool automatically pauses for 90 seconds after every 3500 API requests.
- No action needed — just let it run.

### Error: "No ticks received"
- Your demo account may have expired.
- Go to Dukascopy, create a new demo account, and update `USERNAME` and `PASSWORD` in both Java files (see [How to Update Credentials](#how-to-update-credentials)).

### Error: "Out of disk space"
- The tool archives every 3 years and clears the working directory, so it never holds more than ~3 years of CSV files at once (about 450 MB).
- If you have less than 450 MB free, reduce `BATCH_YEARS` to 1 or 2 in the code.

### Error: "Maven not found" or "Java not found"
- Install Java and Maven:
  ```bash
  sudo apt update
  sudo apt install openjdk-21-jdk maven -y
  ```

### Error: "Permission denied" when running scripts
- Make the scripts executable:
  ```bash
  chmod +x start_fresh_download.sh reset_project.sh
  ```

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

There are **two scripts** for cleaning. Choose the one that fits your need:

### Script 1: `start_fresh_download.sh` — Reset + Compile + Start Download Automatically

```bash
cd ~/RWS-DUKAS-J4X
./start_fresh_download.sh
```

**What it does:**
1. Stops all running JForex processes.
2. Deletes all CSV files in `ohlcv_output/` and `archive/`.
3. Deletes the master progress file.
4. Empties the Trash/Recycle Bin.
5. Clears JForex cache (`/root/JForex/cache`).
6. Compiles the project (`mvn clean compile`).
7. **Starts the downloader automatically** (foreground).

**Use this if:** You want a complete reset **and** want the downloader to start immediately.

### Script 2: `reset_project.sh` — Reset + Compile (Does NOT Start Download)

```bash
cd ~/RWS-DUKAS-J4X
./reset_project.sh
```

**What it does:**
1. Stops all running JForex processes.
2. Deletes all CSV files in `ohlcv_output/` and `archive/`.
3. Deletes the master progress file.
4. Empties the Trash/Recycle Bin.
5. Clears JForex cache (`/root/JForex/cache`).
6. Compiles the project (`mvn clean compile`).

**Does NOT** start the downloader automatically.

**Use this if:** You want a clean slate but want to manually decide when to start the downloader (e.g., you want to run it in `screen` or `nohup` manually).

### Warning for Both Scripts

**These scripts delete ALL downloaded data.** Use only if you want to start over from zero. If you have already downloaded data you want to keep, back up the `archive/` folder before running these scripts.

---

## How the Archiving Logic Works (`BATCH_YEARS`)

The constant `BATCH_YEARS = 3` means the tool archives every **3 years** (36 months). Here's the exact logic:

1. The tool downloads months sequentially (2005-01, 2005-02, ...).
2. It counts how many months have been downloaded in the current batch.
3. When the count reaches 36 (3 years), it triggers the archiving process:
   - Moves all CSV files from `./ohlcv_output/` to `./archive/2005-2007/`.
   - Clears `./ohlcv_output/`.
   - Resets the batch counter.
   - Continues downloading the next batch (2008-2010, etc.).

**Why 3 years?**
- A 3‑year batch of 1‑minute OHLCV data is about **450 MB**.
- This keeps the working directory small and manageable.
- You can change it to `1` (1 year = 150 MB) or `5` (5 years = 750 MB) if you prefer.

**How to change it:**
Open `JForex4Downloader.java` and change:
```java
private static final int BATCH_YEARS = 3;  // Change to 1, 2, 3, 4, 5, etc.
```

---

## How the Weekly Fallback Works

If a full month fails to download after 3 retry attempts, the tool enters **weekly fallback mode**:

1. The month is split into **7‑day chunks** (e.g., 2008-10-01 to 2008-10-07, 2008-10-08 to 2008-10-14, etc.).
2. Each chunk is downloaded separately (with its own retry logic).
3. All chunks are merged into a single CSV file for the month.
4. If even one chunk fails, the month is marked `FAILED` and retried in Phase 2.

**Why this works:**
- A week of tick data is much smaller than a full month, so the network transfer is faster and less likely to time out.
- It isolates the problematic day(s) — if a specific day fails, only that day is retried.

---

## How to Update Credentials

The tool uses demo credentials hardcoded in the Java files. If your demo account expires, update these values:

**File 1:** `JForex4Downloader.java`
```java
private static final String USERNAME = "YOUR_NEW_USERNAME";
private static final String PASSWORD = "YOUR_NEW_PASSWORD";
```

**File 2:** `JForex4ArchiveAuditor.java`
```java
private static final String USERNAME = "YOUR_NEW_USERNAME";
private static final String PASSWORD = "YOUR_NEW_PASSWORD";
```

Then recompile:
```bash
mvn clean compile
```

---

## Known Limitations

| Limitation | Explanation |
|------------|-------------|
| **Datafeed endpoint** | The tool uses `datafeed.66proxymity88.net`, which is the **only** source for Dukascopy historical data. There is no alternative. |
| **Network timeouts** | If your VPS has poor routing to Dukascopy's servers (e.g., from certain regions in Asia or the US), some months may still fail. The tool retries and uses weekly fallback, but extreme cases may require a VPN or moving your VPS to Europe. |
| **Demo accounts expire** | The credentials are not permanent. You may need to renew them on the Dukascopy website every few months. |
| **`AV_SPREAD`** | Calculated from the ask and bid **close** prices of the minute bar, not from every individual tick. For most purposes, this is sufficient. |
| **File size limit** | Terabox has a 2 GB per file limit. Do not zip the entire `archive/` folder into one file. Keep individual CSV files (< 20 MB each). |

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

**Version 2.2.0** — Reliable, automated, and fully documented.

**Key improvements in 2.2.0:**
- Added `reset_project.sh` (clean reset without auto-start).
- Clarified the difference between `start_fresh_download.sh` and `reset_project.sh`.
- Expanded the "How to Clean Everything" section.
- Added detailed explanations of `BATCH_YEARS` and weekly fallback.
- Added "Quick Start" section for impatient users.
- Added complete table of contents.
- Updated all examples and command references.

---
```