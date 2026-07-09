## EXECUTIVE SUMMARY — KONSORTIUM

**STATUS: FULL README.MD — VERSION 3.1.0 (COMPLETE)**

Below is the **entire README.md** file for version 3.1.0. This version incorporates all the recent critical fixes:

1. **Dynamic Filename Generation** — CSV files now use `INSTRUMENT.name()` (e.g., `GBPUSD_2005-01_1min_OHLCV.csv`), not hardcoded "EURUSD" or "BTCUSD".
2. **Improved `configure.sh`** — now validates instrument input, rejects commas/spaces, automatically adds `Instrument.` prefix, and offers to auto-start the download after configuration.
3. **Enhanced `reset_project.sh`** — now removes `.bak_*` backup files and cleans the `logs/` folder.
4. **Updated `JForex4ArchiveAuditor.java`** — now uses dynamic filenames for archive verification.

All sections have been reviewed and updated to reflect these changes.

---

```markdown
# RWS-DUKAS-J4X — Tick-to-1-Minute OHLCV Exporter (v3.1.0)

**Version 3.1.0** — A complete, self-contained, and fully documented tool to download decades of tick data from Dukascopy and convert it into monthly 1‑minute OHLCV CSV files. Now with **dynamic filenames** that automatically match your chosen instrument, and enhanced reset and configuration scripts for a smoother experience.

---

## Quick Start (For The Impatient)

If you just want to get it running right now:

```bash
# 1. Navigate to the project
cd ~/RWS-DUKAS-J4X

# 2. Make scripts executable (first time only)
chmod +x configure.sh start_fresh_download.sh reset_project.sh

# 3. (Optional) Configure symbol, date range, and credentials
./configure.sh

# 4. Compile
mvn clean compile

# 5. Run in background (screen)
screen -S dukas-download
mvn exec:java -Dexec.mainClass="com.rws.dukas.JForex4Downloader"
# Press Ctrl+A, then D to detach

# 6. Check progress later
screen -r dukas-download
```

That's it. The tool will download all months from your configured start date to end date, archive every 3 years, and automatically retry any failed months. Come back in a few hours (or overnight) and your data will be ready.

---

## Table of Contents

1. [What This Tool Does](#what-this-tool-does)
2. [What You Get (Output Format)](#what-you-get-output-format)
3. [Prerequisites (What You Need)](#prerequisites-what-you-need)
4. [Project Structure](#project-structure)
5. [How to Build](#how-to-build)
6. [How to Configure](#how-to-configure)
7. [How to Run](#how-to-run)
8. [How to Run in Background (VPS)](#how-to-run-in-background-vps)
9. [How to Read Logs](#how-to-read-logs)
10. [How to Resume After Interruption](#how-to-resume-after-interruption)
11. [How to Verify the Results](#how-to-verify-the-results)
12. [How to Download the Archive to Your Local Machine](#how-to-download-the-archive-to-your-local-machine)
13. [How to Handle Errors](#how-to-handle-errors)
14. [How to Restart from a Specific Month](#how-to-restart-from-a-specific-month-advanced)
15. [How to Clean Everything and Start Fresh](#how-to-clean-everything-and-start-fresh)
16. [How the Archiving Logic Works (BATCH_YEARS)](#how-the-archiving-logic-works-batch_years)
17. [How the Weekly Fallback Works](#how-the-weekly-fallback-works)
18. [Common Instruments (Symbols)](#common-instruments-symbols)
19. [Known Limitations](#known-limitations)
20. [License](#license)
21. [Final Thoughts](#final-thoughts)

---

## What This Tool Does

This Java application connects to Dukascopy's JForex platform, pulls raw tick data for **any supported forex symbol** from your configured date range, and converts it into clean 1‑minute OHLCV bars (Open, High, Low, Close, Volume) with average spread. The output is one CSV file per month.

It is designed for **unattended, long‑running operation on a VPS**. You run it **once**, and it handles everything automatically:

- Downloads month by month with retries (3 attempts per month).
- If a month fails after 3 retries, it falls back to **weekly chunks** (7‑day slices) to isolate and recover from network issues.
- Every 3 years (36 months), it archives the CSV files and clears the working directory to keep storage usage low.
- If interrupted (e.g., SSH drops, `Ctrl+C`), it resumes exactly where it left off.
- After all months are processed, it automatically retries any failed months (Phase 2 backfill).
- You can also run a separate **Archive Auditor** to verify file integrity and backfill missing/corrupt files before moving data to your local machine.
- **NEW in v3.1.0:** CSV files are now named dynamically using the instrument's name (e.g., `GBPUSD_2005-01_1min_OHLCV.csv`). No more hardcoded "EURUSD" or "BTCUSD" filenames.

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

Files are named dynamically using the instrument you configured:  
`SYMBOL_YYYY-MM_1min_OHLCV.csv`  
(e.g., `GBPUSD_2015-01_1min_OHLCV.csv`, `EURUSD_2005-01_1min_OHLCV.csv`)

Each file is typically **5–15 MB** depending on the month (more trading days = larger file). The entire 21‑year dataset for EUR/USD is about **3 GB**.

---

## Prerequisites (What You Need)

| Requirement | Version | Installation Command |
|-------------|---------|----------------------|
| **Java** | 21 (OpenJDK or Oracle) | `sudo apt install openjdk-21-jdk` |
| **Maven** | 3.6+ | `sudo apt install maven` |
| **screen** (recommended for background) | Any | `sudo apt install screen` |
| **Script Permissions** | — | `chmod +x configure.sh start_fresh_download.sh reset_project.sh` |
| **Dukascopy demo account** | Active | [Create one here](https://www.dukascopy.com) |

The code includes demo credentials (`DEMO2YciXg` / `YciXg`), but they may expire. If you see connection errors, create a new demo account and update the credentials using `./configure.sh` (see the [How to Configure](#how-to-configure) section).

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
│           │           ├── JForex4Downloader.java        # MAIN DOWNLOADER (dynamic filenames)
│           │           └── JForex4ArchiveAuditor.java    # ARCHIVE INTEGRITY CHECKER (dynamic filenames)
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
├── logs/                                                   # RUNTIME LOGS (AUTO-CLEANED BY reset_project.sh)
├── .master_download_progress.txt                         # PERSISTENT PROGRESS TRACKER (NEVER DELETE UNLESS RESETTING)
├── pom.xml                                                # MAVEN CONFIGURATION
├── configure.sh                                           # INTERACTIVE CONFIGURATION SCRIPT (v3.1.0 enhanced)
├── start_fresh_download.sh                               # ONE-CLICK RESET + DOWNLOAD (DELETES EVERYTHING + STARTS DOWNLOAD)
├── reset_project.sh                                      # ONE-CLICK RESET (CLEANS DATA, BACKUPS, LOGS, AND REPAIRS SOURCE CODE)
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

## How to Configure

**Version 3.1.0** introduces improvements to `configure.sh`: it now validates instrument names, rejects invalid entries (e.g., "GBPUSD, USDJPY"), automatically adds the `Instrument.` prefix if you forget it, and optionally starts the download immediately after configuration.

### What You Can Change with `configure.sh`

| Setting | Description | Example |
|---------|-------------|---------|
| **Symbol** | The instrument to download | `Instrument.GBPUSD`, `Instrument.USDJPY`, or just `GBPUSD` |
| **Start Year** | First year of the download | `2010` |
| **Start Month** | First month of the download (1-12) | `1` (January) |
| **Start Day** | First day of the download (1-31) | `1` |
| **End Year** | Last year of the download | `2025` |
| **End Month** | Last month of the download (1-12) | `12` (December) |
| **End Day** | Last day of the download (1-31) | `31` |
| **Username** | Dukascopy demo account username | `DEMO2YciXg` |
| **Password** | Dukascopy demo account password | `YciXg` |

### What You DON'T Need to Change (Read-Only)

These settings are **automatically displayed** for your reference but are **not modified** by the script:
- `JNLP_URL` — connection endpoint (rarely changes)
- `BATCH_YEARS` — archiving interval (3 years by default)
- `OUTPUT_DIR` — temporary working directory
- `ARCHIVE_BASE_DIR` — archive destination folder

### How to Use `configure.sh`

```bash
cd ~/RWS-DUKAS-J4X

# Make it executable (first time only)
chmod +x configure.sh

# Run it
./configure.sh
```

### What's New in v3.1.0

1. **Instrument validation** — If you enter `GBPUSD` (without `Instrument.`), the script automatically adds it. If you enter something invalid (like `GBPUSD, USDJPY` with a comma), it rejects it and asks again.
2. **Handles invalid current values** — If the current instrument value is malformed (e.g., due to a previous error), the script detects it and forces you to enter a valid one.
3. **Auto‑start download** — After configuration and compilation, the script asks:
   ```
   Do you want to start the download now? (y/N):
   ```
   If you answer `y`, you can choose:
   - `f` — Foreground (logs appear in your terminal).
   - `s` — Screen (runs in background, recommended for VPS).

### Example Interaction

```
============================================================
=== RWS-DUKAS-J4X — QUICK CONFIGURATION TOOL ===
============================================================

Current System Settings (Read-Only):
----------------------------------------
  JNLP URL        : http://platform.dukascopy.com/demo_4/jforex_4.jnlp
  Batch Years     : 3
  Output Dir      : ./ohlcv_output/
  Archive Dir     : ./archive/
----------------------------------------
These settings rarely change. They are shown for your reference.

Step 1: Instrument (Symbol)
----------------------------------------
Common symbols: EURUSD, GBPUSD, USDJPY, XAUUSD, BTCUSD
Format: Instrument.XXX (e.g., Instrument.GBPUSD)
You can also enter just the symbol (e.g., GBPUSD) and the script will add 'Instrument.' automatically.

Current INSTRUMENT: Instrument.BTCUSD
Enter new Instrument (press Enter to keep current): GBPUSD

Note: Added 'Instrument.' prefix -> Instrument.GBPUSD

Step 2: Date Range
----------------------------------------
Enter start date and end date for the download.
Current START_YEAR: 2005
Enter START_YEAR (press Enter to keep current): 2015
Current START_MONTH: 1
Enter START_MONTH (1-12) (press Enter to keep current): 1
Current START_DAY: 1
Enter START_DAY (1-31) (press Enter to keep current): 1
Current END_YEAR: 2026
Enter END_YEAR (press Enter to keep current): 2025
Current END_MONTH: 7
Enter END_MONTH (1-12) (press Enter to keep current): 12
Current END_DAY: 3
Enter END_DAY (1-31) (press Enter to keep current): 31

Step 3: Dukascopy Account Credentials
----------------------------------------
Current USERNAME: DEMO2YciXg
Enter Dukascopy USERNAME (press Enter to keep current): 
Current PASSWORD: YciXg
Enter Dukascopy PASSWORD (press Enter to keep current): 

============================================================
Configuration Summary:
============================================================
  Instrument      : Instrument.GBPUSD
  Start Date      : 2015-1-1
  End Date        : 2025-12-31
  Username        : DEMO2YciXg
  Password        : ********

Unchanged (Read-Only):
  JNLP URL        : http://platform.dukascopy.com/demo_4/jforex_4.jnlp
  Batch Years     : 3
  Output Dir      : ./ohlcv_output/
  Archive Dir     : ./archive/
============================================================

Do you want to apply these changes? (y/N): y

Backing up original files...
  -> Backup created: src/main/java/com/rws/dukas/JForex4Downloader.java.bak_20260709_120000
  -> Backup created: src/main/java/com/rws/dukas/JForex4ArchiveAuditor.java.bak_20260709_120000

Applying new configuration...
All files updated successfully.

Step 4: Compiling the project with new settings...
[INFO] BUILD SUCCESS

Do you want to start the download now? (y/N): y

Choose how to run:
  (f) Foreground  - logs appear in this terminal (press Ctrl+C to stop)
  (s) Screen      - runs in background, survives SSH disconnect (recommended)
Your choice (f/s): s

Starting download in screen session 'dukas-download'...
Download started in background.
To view logs: screen -r dukas-download
To detach from screen: Ctrl+A, D
To re-attach later: screen -r dukas-download
```

### What Happens Behind the Scenes

1. The script reads your **current settings** from the Java files.
2. It **prompts you** for new values — press Enter to keep the current value.
3. It **validates** your inputs (months 1-12, days 1-31, 4-digit years, and instrument syntax).
4. It displays a **summary** and asks for confirmation.
5. It **creates backups** of the original Java files with a timestamp.
6. It **updates both Java files** simultaneously.
7. It **runs `mvn clean compile`** automatically.
8. It **asks if you want to start the download** immediately, and offers foreground or screen mode.

### Restoring Previous Configuration

If you made a mistake or want to revert:

```bash
cd ~/RWS-DUKAS-J4X

# Find your backup files
ls -la src/main/java/com/rws/dukas/*.bak_*

# Restore (replace with your actual backup filename)
cp src/main/java/com/rws/dukas/JForex4Downloader.java.bak_20260709_120000 src/main/java/com/rws/dukas/JForex4Downloader.java
cp src/main/java/com/rws/dukas/JForex4ArchiveAuditor.java.bak_20260709_120000 src/main/java/com/rws/dukas/JForex4ArchiveAuditor.java

# Recompile
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

You should see 36 CSV files (for 2005, 2006, 2007) — one per month, named with your instrument's symbol (e.g., `GBPUSD_2005-01_1min_OHLCV.csv`).

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
- Run `./configure.sh` and update your username and password with a new demo account from Dukascopy.

### Error: "Out of disk space"
- The tool archives every 3 years and clears the working directory, so it never holds more than ~3 years of CSV files at once (about 450 MB).
- If you have less than 450 MB free, reduce `BATCH_YEARS` to 1 or 2 in the code (you'll need to edit the Java file manually for this).

### Error: "Maven not found" or "Java not found"
- Install Java and Maven:
  ```bash
  sudo apt update
  sudo apt install openjdk-21-jdk maven -y
  ```

### Error: "Permission denied" when running scripts
- Make the scripts executable:
  ```bash
  chmod +x configure.sh start_fresh_download.sh reset_project.sh
  ```

### Error: Invalid symbol name
- If you get a compilation error after running `configure.sh`, you may have entered an invalid instrument name.
- Check the list of valid instruments in the [Dukascopy API documentation](https://www.dukascopy.com/wiki/en/development/strategy-api/api-reference/com/dukascopy/api/Instrument.html).
- Run `./configure.sh` again and correct the symbol.

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

There are **three scripts** for cleaning. Choose the one that fits your need:

### Script 1: `configure.sh` — Change Settings (Does NOT Delete Data)

```bash
cd ~/RWS-DUKAS-J4X
./configure.sh
```

**What it does:**
- Prompts for new symbol, date range, and credentials.
- Backs up and updates the Java files.
- Compiles the project.
- **Does NOT** delete any data.

**Use this if:** You want to change the symbol, date range, or credentials without losing already-downloaded data.

### Script 2: `start_fresh_download.sh` — Reset + Compile + Start Download Automatically

```bash
cd ~/RWS-DUKAS-J4X
./start_fresh_download.sh
```

**What it does:**
1. Stops all running JForex processes.
2. Deletes all CSV files in `ohlcv_output/` and `archive/`.
3. Deletes the master progress file.
4. Removes all `.bak_*` backup files.
5. Cleans the `logs/` folder.
6. Empties the Trash/Recycle Bin.
7. Clears JForex cache (`/root/JForex/cache`).
8. Compiles the project (`mvn clean compile`).
9. **Starts the downloader automatically** (foreground).

**Use this if:** You want a complete reset **and** want the downloader to start immediately.

### Script 3: `reset_project.sh` — Reset + Compile (Does NOT Start Download)

```bash
cd ~/RWS-DUKAS-J4X
./reset_project.sh
```

**What it does:**
1. Stops all running JForex processes.
2. Deletes all CSV files in `ohlcv_output/` and `archive/`.
3. Deletes the master progress file.
4. **Removes all `.bak_*` backup files** (created by `configure.sh`).
5. **Cleans the `logs/` folder** (removes old log files).
6. Empties the Trash/Recycle Bin.
7. Clears JForex cache (`/root/JForex/cache`).
8. **Repairs source code** (resets Instrument to `Instrument.EURUSD`).
9. Compiles the project (`mvn clean compile`).

**Does NOT** start the downloader automatically.

**Use this if:** You want a clean slate but want to manually decide when to start the downloader (e.g., you want to run it in `screen` or `nohup` manually).

### Warning for Scripts 2 and 3

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

## Common Instruments (Symbols)

Here are the most common instruments available on Dukascopy demo accounts:

| Symbol | Instrument Enum | Description |
|--------|-----------------|-------------|
| EUR/USD | `Instrument.EURUSD` | Euro / US Dollar |
| GBP/USD | `Instrument.GBPUSD` | British Pound / US Dollar |
| USD/JPY | `Instrument.USDJPY` | US Dollar / Japanese Yen |
| AUD/USD | `Instrument.AUDUSD` | Australian Dollar / US Dollar |
| USD/CHF | `Instrument.USDCHF` | US Dollar / Swiss Franc |
| USD/CAD | `Instrument.USDCAD` | US Dollar / Canadian Dollar |
| NZD/USD | `Instrument.NZDUSD` | New Zealand Dollar / US Dollar |
| EUR/JPY | `Instrument.EURJPY` | Euro / Japanese Yen |
| GBP/JPY | `Instrument.GBPJPY` | British Pound / Japanese Yen |
| XAU/USD | `Instrument.XAUUSD` | Gold / US Dollar |
| XAG/USD | `Instrument.XAGUSD` | Silver / US Dollar |
| BTC/USD | `Instrument.BTCUSD` | Bitcoin / US Dollar |

**Note:** Not all instruments are available on demo accounts. Stick to major forex pairs for the best results.

---

## Known Limitations

| Limitation | Explanation |
|------------|-------------|
| **Datafeed endpoint** | The tool uses `datafeed.66proxymity88.net`, which is the **only** source for Dukascopy historical data. There is no alternative. |
| **Network timeouts** | If your VPS has poor routing to Dukascopy's servers (e.g., from certain regions in Asia or the US), some months may still fail. The tool retries and uses weekly fallback, but extreme cases may require a VPN or moving your VPS to Europe. |
| **Demo accounts expire** | The credentials are not permanent. You may need to renew them on the Dukascopy website every few months. Use `./configure.sh` to update them easily. |
| **`AV_SPREAD`** | Calculated from the ask and bid **close** prices of the minute bar, not from every individual tick. For most purposes, this is sufficient. |
| **File size limit** | Terabox has a 2 GB per file limit. Do not zip the entire `archive/` folder into one file. Keep individual CSV files (< 20 MB each). |
| **Instrument availability** | Not all instruments are available on demo accounts. Stick to major forex pairs (EURUSD, GBPUSD, USDJPY) for best results. |

---

## License

MIT License — see the `LICENSE` file.

---

## Final Thoughts

This tool is designed to be **set and forget**. You run it once, wait, and come back to a complete dataset.

**Summary of what you need to do:**

1. **(Optional) Configure:** `./configure.sh` to set symbol, date range, and credentials.
2. **Build:** `mvn clean compile`
3. **Run (background):** `screen -S dukas-download` then `mvn exec:java -Dexec.mainClass="com.rws.dukas.JForex4Downloader"`
4. **Wait overnight.**
5. **Run Archive Auditor:** `mvn exec:java -Dexec.mainClass="com.rws.dukas.JForex4ArchiveAuditor"`
6. **Download to Mac:** `rsync -avz --progress root@<IP>:/root/RWS-DUKAS-J4X/archive/ ~/Documents/ForexData/`

That's it. Everything else is automatic.

If you encounter any issues, check the logs (see "How to Read Logs" above) and consult the troubleshooting section. The tool is designed to recover from most failures automatically.

---

**Version 3.1.0** — Reliable, automated, and fully documented.

**Key improvements in 3.1.0:**
- **Dynamic filenames** — CSV files now use `INSTRUMENT.name()` instead of hardcoded "EURUSD" or "BTCUSD".
- **Enhanced `configure.sh`** — validates instrument input, rejects commas/spaces, automatically adds `Instrument.` prefix, and offers auto-start download after configuration.
- **Enhanced `reset_project.sh`** — now removes `.bak_*` backup files and cleans the `logs/` folder.
- **Updated `JForex4ArchiveAuditor.java`** — now uses dynamic filenames for archive verification.
- All scripts are now fully resilient to common user errors.

```