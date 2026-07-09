#!/bin/bash
# ============================================================
# RESET PROJECT SCRIPT — Clean Slate + Source Code Repair
# 
# This script performs a complete reset of the project:
# 1. Stops all running JForex processes
# 2. Deletes ALL CSV files in ohlcv_output/ and archive/
# 3. Deletes the master progress file (.master_download_progress.txt)
# 4. Removes ALL configuration backup files (*.bak_*)
# 5. Cleans the logs/ folder
# 6. Empties the system Trash/Recycle Bin
# 7. Clears JForex cache (/root/JForex/cache, data, tmp)
# 8. Resets source code (Instrument to EURUSD) and recompiles
# ============================================================

set -e  # Stop immediately if any command fails

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

PROJECT_DIR=~/RWS-DUKAS-J4X

echo "============================================================"
echo "=== RWS-DUKAS-J4X — FULL PROJECT RESET ==="
echo "============================================================"
echo "WARNING: This will delete ALL data, progress, cache,"
echo "         configuration backups, and old logs."
echo "         It will also RESET instrument to EUR/USD."
echo "Press Ctrl+C within 5 seconds to cancel, or wait to continue..."
sleep 5

if [ ! -d "$PROJECT_DIR" ]; then
    echo -e "${RED}ERROR: Project directory $PROJECT_DIR does not exist.${NC}"
    exit 1
fi

cd $PROJECT_DIR

# ============================================================
# 1. STOP RUNNING JFOREK PROCESSES
# ============================================================
echo "[1/8] Stopping any running JForex processes..."
pkill -f "jforex" 2>/dev/null || true
pkill -f "JForex" 2>/dev/null || true
pkill -f "DDS2" 2>/dev/null || true
sleep 1
echo -e "${GREEN}  -> Done.${NC}"

# ============================================================
# 2. DELETE ALL CSV FILES
# ============================================================
echo "[2/8] Deleting all CSV files from ohlcv_output/ and archive/..."
rm -rf ohlcv_output/*.csv 2>/dev/null || true
rm -rf archive/* 2>/dev/null || true
echo -e "${GREEN}  -> Done.${NC}"

# ============================================================
# 3. DELETE MASTER PROGRESS FILE
# ============================================================
echo "[3/8] Deleting master progress file (.master_download_progress.txt)..."
rm -f .master_download_progress.txt
echo -e "${GREEN}  -> Done.${NC}"

# ============================================================
# 4. DELETE CONFIGURATION BACKUP FILES (*.bak_*)
# ============================================================
echo "[4/8] Removing configuration backup files (*.bak_*)..."
# Remove all .bak_* files in the Java source directory
rm -f src/main/java/com/rws/dukas/*.bak_* 2>/dev/null || true
echo -e "${GREEN}  -> Done.${NC}"

# ============================================================
# 5. CLEAN LOGS FOLDER
# ============================================================
echo "[5/8] Cleaning logs folder (keeping only the directory)..."
rm -rf logs/* 2>/dev/null || true
echo -e "${GREEN}  -> Done.${NC}"

# ============================================================
# 6. EMPTY RECYCLE BIN / TRASH
# ============================================================
echo "[6/8] Emptying system Trash/Recycle Bin..."
rm -rf ~/.local/share/Trash/* 2>/dev/null || true
if command -v trash-empty &> /dev/null; then
    trash-empty -f 2>/dev/null || true
    echo -e "${GREEN}  -> Done (via trash-cli).${NC}"
else
    echo -e "${GREEN}  -> Done (manual).${NC}"
fi

# ============================================================
# 7. CLEAR JFOREK CACHE
# ============================================================
echo "[7/8] Clearing JForex cache (/root/JForex/cache, data, tmp)..."
rm -rf /root/JForex/cache 2>/dev/null || true
rm -rf /root/JForex/data 2>/dev/null || true
rm -rf /root/JForex/tmp 2>/dev/null || true
echo -e "${GREEN}  -> Done.${NC}"

# ============================================================
# 8. REPAIR SOURCE CODE & COMPILE
# ============================================================
echo "[8/8] Repairing source code (resetting Instrument to EURUSD) and compiling..."
sed -i 's/private static final Instrument INSTRUMENT = .*;/private static final Instrument INSTRUMENT = Instrument.EURUSD;/' src/main/java/com/rws/dukas/JForex4Downloader.java
sed -i 's/private static final Instrument INSTRUMENT = .*;/private static final Instrument INSTRUMENT = Instrument.EURUSD;/' src/main/java/com/rws/dukas/JForex4ArchiveAuditor.java

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
echo "  - *.bak_* backup files are removed."
echo "  - logs/ folder is cleaned."
echo "  - JForex cache is cleared."
echo "  - Instrument reset to Instrument.EURUSD."
echo "  - Project is compiled successfully."
echo ""
echo "NEXT STEPS:"
echo "  To configure a different symbol or date range, run:"
echo "    ./configure.sh"
echo ""
echo "  To start a fresh download with default EUR/USD, run:"
echo "    mvn exec:java -Dexec.mainClass=\"com.rws.dukas.JForex4Downloader\""
echo ""
echo "  To run it in the background (screen):"
echo "    screen -S dukas-download"
echo "    mvn exec:java -Dexec.mainClass=\"com.rws.dukas.JForex4Downloader\""
echo "    (press Ctrl+A, then D to detach)"
echo "============================================================"