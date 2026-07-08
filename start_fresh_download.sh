#!/bin/bash
# ============================================================
# ONE-CLICK FULL RESET + DOWNLOAD SCRIPT
# 
# ACTIONS:
# 1. Stops running JForex processes (releases file locks)
# 2. Deletes ALL CSV files in ohlcv_output/ and archive/
# 3. Deletes master progress file (.master_download_progress.txt)
# 4. Empties Recycle Bin (Trash)
# 5. Deletes JForex cache (/root/JForex/cache, data, tmp)
# 6. Compiles the project (mvn clean compile)
# 7. Starts the JForex4Downloader automatically
# 
# USAGE: ./start_fresh_download.sh
# ============================================================

set -e  # Exit immediately if any command fails

PROJECT_DIR=~/RWS-DUKAS-J4X

echo "============================================================"
echo "=== JFOREK DOWNLOADER — FULL RESET & START ==="
echo "============================================================"
echo "WARNING: This will DELETE all existing CSV files and cache!"
echo "Press Ctrl+C within 5 seconds to cancel..."
sleep 5

cd $PROJECT_DIR

# STEP 1: Kill JForex processes
echo "[1/7] Stopping any running JForex processes..."
pkill -f "jforex" 2>/dev/null || true
pkill -f "JForex" 2>/dev/null || true
pkill -f "DDS2" 2>/dev/null || true
sleep 1
echo "  -> Done."

# STEP 2: Delete CSV files
echo "[2/7] Deleting all CSV output and archives..."
rm -rf ohlcv_output/*
rm -rf archive/*
echo "  -> Done."

# STEP 3: Delete master progress file
echo "[3/7] Deleting master progress file..."
rm -f .master_download_progress.txt
echo "  -> Done."

# STEP 4: Empty Recycle Bin
echo "[4/7] Emptying Recycle Bin (Trash)..."
rm -rf ~/.local/share/Trash/*
if command -v trash-empty &> /dev/null; then
    trash-empty -f
    echo "  -> trash-cli emptied."
else
    echo "  -> trash-cli not found (manually deleted Trash folder)."
fi
echo "  -> Done."

# STEP 5: Delete JForex Cache
echo "[5/7] Clearing JForex cache (/root/JForex)..."
rm -rf /root/JForex/cache
rm -rf /root/JForex/data
rm -rf /root/JForex/tmp
echo "  -> Done."

# STEP 6: Maven Compile
echo "[6/7] Compiling project with Maven..."
mvn clean compile
echo "  -> Done."

# STEP 7: Start Downloader
echo "[7/7] Starting the JForex4Downloader..."
echo "============================================================"
echo "EVERYTHING IS CLEAN. DOWNLOAD STARTING..."
echo "DO NOT INTERRUPT THIS PROCESS."
echo "============================================================"
mvn exec:java -Dexec.mainClass="com.rws.dukas.JForex4Downloader"