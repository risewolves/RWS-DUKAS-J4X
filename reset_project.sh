#!/bin/bash
# ============================================================
# RESET PROJECT SCRIPT — Clean Slate for RWS-DUKAS-J4X
#
# This script performs a complete reset of the project:
# 1. Stops all running JForex processes
# 2. Deletes ALL CSV files in ohlcv_output/ and archive/
# 3. Deletes the master progress file (.master_download_progress.txt)
# 4. Empties the system Trash/Recycle Bin
# 5. Clears JForex cache (/root/JForex/cache, data, tmp)
# 6. Recompiles the project (mvn clean compile)
#
# After running this, your project is completely clean.
# You can then run the downloader manually:
#   mvn exec:java -Dexec.mainClass="com.rws.dukas.JForex4Downloader"
# ============================================================

set -e  # Stop immediately if any command fails

# Colors for better readability
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

PROJECT_DIR=~/RWS-DUKAS-J4X

echo "============================================================"
echo "=== RWS-DUKAS-J4X — FULL PROJECT RESET ==="
echo "============================================================"
echo "WARNING: This will delete ALL downloaded CSV data,"
echo "         master progress, and JForex cache."
echo "Press Ctrl+C within 5 seconds to cancel, or wait to continue..."
sleep 5

# FIXED GAP #1: Check if project directory exists
if [ ! -d "$PROJECT_DIR" ]; then
    echo -e "${RED}ERROR: Project directory $PROJECT_DIR does not exist.${NC}"
    echo "Please make sure you are running this script from the correct user (root) and the project exists."
    exit 1
fi

cd $PROJECT_DIR

# ============================================================
# 1. STOP RUNNING JFOREK PROCESSES
# ============================================================
echo "[1/6] Stopping any running JForex processes..."
pkill -f "jforex" 2>/dev/null || true
pkill -f "JForex" 2>/dev/null || true
pkill -f "DDS2" 2>/dev/null || true
sleep 1
echo -e "${GREEN}  -> Done.${NC}"

# ============================================================
# 2. DELETE ALL CSV FILES (ohlcv_output/ AND archive/)
# ============================================================
echo "[2/6] Deleting all CSV files from ohlcv_output/ and archive/..."
rm -rf ohlcv_output/*.csv 2>/dev/null || true
# Delete ALL contents of archive/ (including subfolders like 2005-2007)
rm -rf archive/* 2>/dev/null || true
echo -e "${GREEN}  -> Done.${NC}"

# ============================================================
# 3. DELETE MASTER PROGRESS FILE
# ============================================================
echo "[3/6] Deleting master progress file (.master_download_progress.txt)..."
rm -f .master_download_progress.txt
echo -e "${GREEN}  -> Done.${NC}"

# ============================================================
# 4. EMPTY RECYCLE BIN / TRASH
# ============================================================
echo "[4/6] Emptying system Trash/Recycle Bin..."
rm -rf ~/.local/share/Trash/* 2>/dev/null || true
if command -v trash-empty &> /dev/null; then
    trash-empty -f 2>/dev/null || true
    echo -e "${GREEN}  -> Done (via trash-cli).${NC}"
else
    echo -e "${GREEN}  -> Done (manual).${NC}"
fi

# ============================================================
# 5. CLEAR JFOREK CACHE
# ============================================================
echo "[5/6] Clearing JForex cache (/root/JForex/cache, data, tmp)..."
rm -rf /root/JForex/cache 2>/dev/null || true
rm -rf /root/JForex/data 2>/dev/null || true
rm -rf /root/JForex/tmp 2>/dev/null || true
echo -e "${GREEN}  -> Done.${NC}"

# ============================================================
# 6. COMPILE THE PROJECT
# ============================================================
echo "[6/6] Compiling the project (mvn clean compile)..."
# FIXED GAP #2: Check if mvn exists before running
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}ERROR: Maven (mvn) is not installed. Cannot compile.${NC}"
    echo "Please install Maven first: sudo apt install maven"
    exit 1
fi
mvn clean compile
echo -e "${GREEN}  -> Compilation successful.${NC}"

# ============================================================
# FINAL REPORT
# ============================================================
echo "============================================================"
echo -e "${GREEN}PROJECT RESET COMPLETE.${NC}"
echo "============================================================"
echo ""
echo "STATUS:"
echo "  - ohlcv_output/ and archive/ are empty."
echo "  - .master_download_progress.txt is deleted."
echo "  - JForex cache is cleared."
echo "  - Project is compiled."
echo ""
echo "NEXT STEPS:"
echo "  To start a fresh download, run:"
echo "    mvn exec:java -Dexec.mainClass=\"com.rws.dukas.JForex4Downloader\""
echo ""
echo "  To run it in the background (screen):"
echo "    screen -S dukas-download"
echo "    mvn exec:java -Dexec.mainClass=\"com.rws.dukas.JForex4Downloader\""
echo "    (press Ctrl+A, then D to detach)"
echo "============================================================"