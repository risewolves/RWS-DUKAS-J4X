#!/bin/bash
# ============================================================
# CONFIGURE SCRIPT — Easy Symbol & Date Range Updater
# 
# This script safely updates ONLY the constants that users
# actually need to change:
#   - Instrument (Symbol)
#   - Start Date (Year, Month, Day)
#   - End Date (Year, Month, Day)
#   - Dukascopy Username & Password
# 
# Other constants (JNLP_URL, BATCH_YEARS, OUTPUT_DIR, etc.)
# are displayed as read-only information and NOT modified.
# 
# USAGE: ./configure.sh
# ============================================================

set -e  # Exit on error

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

PROJECT_DIR=~/RWS-DUKAS-J4X
FILES=(
    "src/main/java/com/rws/dukas/JForex4Downloader.java"
    "src/main/java/com/rws/dukas/JForex4ArchiveAuditor.java"
)

# ------------------------------------------------------------
# 0. Pre-flight checks
# ------------------------------------------------------------
if [ ! -d "$PROJECT_DIR" ]; then
    echo -e "${RED}ERROR: Project directory $PROJECT_DIR does not exist.${NC}"
    exit 1
fi

cd $PROJECT_DIR

for file in "${FILES[@]}"; do
    if [ ! -f "$file" ]; then
        echo -e "${RED}ERROR: Required file $file not found.${NC}"
        exit 1
    fi
done

# ------------------------------------------------------------
# 1. Helper Functions
# ------------------------------------------------------------

read_current_value() {
    local pattern=$1
    local file=${FILES[0]}
    local value=$(grep -E "$pattern" "$file" | head -1 | sed -E "s/.*= (.*);/\1/" | xargs)
    echo "$value"
}

prompt_value() {
    local var_name=$1
    local current_value=$2
    local prompt_text=$3
    local new_value

    echo -e "${BLUE}Current $var_name:${NC} $current_value"
    read -p "$prompt_text (press Enter to keep current): " new_value

    if [ -z "$new_value" ]; then
        echo "$current_value"
    else
        echo "$new_value"
    fi
}

validate_month() {
    local month=$1
    if [[ ! "$month" =~ ^[0-9]+$ ]] || [ "$month" -lt 1 ] || [ "$month" -gt 12 ]; then
        echo -e "${RED}Invalid month: $month. Must be between 1 and 12.${NC}"
        return 1
    fi
    return 0
}

validate_day() {
    local day=$1
    if [[ ! "$day" =~ ^[0-9]+$ ]] || [ "$day" -lt 1 ] || [ "$day" -gt 31 ]; then
        echo -e "${RED}Invalid day: $day. Must be between 1 and 31.${NC}"
        return 1
    fi
    return 0
}

validate_year() {
    local year=$1
    if [[ ! "$year" =~ ^[0-9]{4}$ ]]; then
        echo -e "${RED}Invalid year: $year. Must be 4 digits (e.g., 2010).${NC}"
        return 1
    fi
    return 0
}

update_constant() {
    local search_pattern=$1
    local new_value=$2
    for file in "${FILES[@]}"; do
        if [ -f "$file" ]; then
            sed -i "s/$search_pattern = .*;/$search_pattern = $new_value;/" "$file"
        fi
    done
}

update_string_constant() {
    local search_pattern=$1
    local new_value=$2
    for file in "${FILES[@]}"; do
        if [ -f "$file" ]; then
            sed -i "s|$search_pattern = \".*\";|$search_pattern = \"$new_value\";|" "$file"
        fi
    done
}

update_instrument() {
    local new_value=$1
    for file in "${FILES[@]}"; do
        if [ -f "$file" ]; then
            sed -i "s/private static final Instrument INSTRUMENT = .*;/private static final Instrument INSTRUMENT = $new_value;/" "$file"
        fi
    done
}

# ------------------------------------------------------------
# 2. Read Current Values
# ------------------------------------------------------------
echo "============================================================"
echo "=== RWS-DUKAS-J4X — QUICK CONFIGURATION TOOL ==="
echo "============================================================"
echo ""

CURRENT_INSTRUMENT=$(read_current_value "private static final Instrument INSTRUMENT")
CURRENT_START_YEAR=$(read_current_value "private static final int START_YEAR")
CURRENT_START_MONTH=$(read_current_value "private static final int START_MONTH")
CURRENT_START_DAY=$(read_current_value "private static final int START_DAY")
CURRENT_END_YEAR=$(read_current_value "private static final int END_YEAR")
CURRENT_END_MONTH=$(read_current_value "private static final int END_MONTH")
CURRENT_END_DAY=$(read_current_value "private static final int END_DAY")
CURRENT_USERNAME=$(read_current_value "private static final String USERNAME")
CURRENT_PASSWORD=$(read_current_value "private static final String PASSWORD")

# Read-only constants (displayed for info, NOT modified)
CURRENT_JNLP_URL=$(read_current_value "private static final String JNLP_URL")
CURRENT_BATCH_YEARS=$(read_current_value "private static final int BATCH_YEARS")
CURRENT_OUTPUT_DIR=$(grep -E "private static final String OUTPUT_DIR" "${FILES[0]}" | head -1 | sed -E "s/.*= \"(.*)\";/\1/" | xargs)
CURRENT_ARCHIVE_DIR=$(grep -E "private static final String ARCHIVE_BASE_DIR" "${FILES[0]}" | head -1 | sed -E "s/.*= \"(.*)\";/\1/" | xargs)

# ------------------------------------------------------------
# 3. Display Read-Only Settings (Info)
# ------------------------------------------------------------
echo -e "${YELLOW}Current System Settings (Read-Only):${NC}"
echo "----------------------------------------"
echo "  JNLP URL        : $CURRENT_JNLP_URL"
echo "  Batch Years     : $CURRENT_BATCH_YEARS"
echo "  Output Dir      : $CURRENT_OUTPUT_DIR"
echo "  Archive Dir     : $CURRENT_ARCHIVE_DIR"
echo "----------------------------------------"
echo "These settings rarely change. They are shown for your reference."
echo ""

# ------------------------------------------------------------
# 4. Prompt for User-Changable Values
# ------------------------------------------------------------
echo -e "${YELLOW}Step 1: Instrument (Symbol)${NC}"
echo "----------------------------------------"
echo "Common symbols: EURUSD, GBPUSD, USDJPY, XAUUSD, BTCUSD"
echo "Format: Instrument.XXX (e.g., Instrument.GBPUSD)"
NEW_INSTRUMENT=$(prompt_value "INSTRUMENT" "$CURRENT_INSTRUMENT" "Enter new Instrument")

echo ""
echo -e "${YELLOW}Step 2: Date Range${NC}"
echo "----------------------------------------"
echo "Enter start date and end date for the download."

while true; do
    NEW_START_YEAR=$(prompt_value "START_YEAR" "$CURRENT_START_YEAR" "Enter START_YEAR")
    if validate_year "$NEW_START_YEAR"; then break; fi
done

while true; do
    NEW_START_MONTH=$(prompt_value "START_MONTH" "$CURRENT_START_MONTH" "Enter START_MONTH (1-12)")
    if validate_month "$NEW_START_MONTH"; then break; fi
done

while true; do
    NEW_START_DAY=$(prompt_value "START_DAY" "$CURRENT_START_DAY" "Enter START_DAY (1-31)")
    if validate_day "$NEW_START_DAY"; then break; fi
done

while true; do
    NEW_END_YEAR=$(prompt_value "END_YEAR" "$CURRENT_END_YEAR" "Enter END_YEAR")
    if validate_year "$NEW_END_YEAR"; then break; fi
done

while true; do
    NEW_END_MONTH=$(prompt_value "END_MONTH" "$CURRENT_END_MONTH" "Enter END_MONTH (1-12)")
    if validate_month "$NEW_END_MONTH"; then break; fi
done

while true; do
    NEW_END_DAY=$(prompt_value "END_DAY" "$CURRENT_END_DAY" "Enter END_DAY (1-31)")
    if validate_day "$NEW_END_DAY"; then break; fi
done

echo ""
echo -e "${YELLOW}Step 3: Dukascopy Account Credentials${NC}"
echo "----------------------------------------"
NEW_USERNAME=$(prompt_value "USERNAME" "$CURRENT_USERNAME" "Enter Dukascopy USERNAME")
NEW_PASSWORD=$(prompt_value "PASSWORD" "$CURRENT_PASSWORD" "Enter Dukascopy PASSWORD")

# ------------------------------------------------------------
# 5. Display Summary & Confirm
# ------------------------------------------------------------
echo ""
echo "============================================================"
echo -e "${YELLOW}Configuration Summary:${NC}"
echo "============================================================"
echo "  Instrument      : $NEW_INSTRUMENT"
echo "  Start Date      : $NEW_START_YEAR-$NEW_START_MONTH-$NEW_START_DAY"
echo "  End Date        : $NEW_END_YEAR-$NEW_END_MONTH-$NEW_END_DAY"
echo "  Username        : $NEW_USERNAME"
echo "  Password        : ********"
echo ""
echo -e "${YELLOW}Unchanged (Read-Only):${NC}"
echo "  JNLP URL        : $CURRENT_JNLP_URL"
echo "  Batch Years     : $CURRENT_BATCH_YEARS"
echo "  Output Dir      : $CURRENT_OUTPUT_DIR"
echo "  Archive Dir     : $CURRENT_ARCHIVE_DIR"
echo "============================================================"
echo ""

read -p "Do you want to apply these changes? (y/N): " CONFIRM
if [[ ! "$CONFIRM" =~ ^[Yy]$ ]]; then
    echo -e "${RED}Configuration cancelled. No changes were made.${NC}"
    exit 0
fi

# ------------------------------------------------------------
# 6. Backup and Apply Changes
# ------------------------------------------------------------
echo ""
echo "Backing up original files..."
BACKUP_SUFFIX=$(date +%Y%m%d_%H%M%S)
for file in "${FILES[@]}"; do
    if [ -f "$file" ]; then
        cp "$file" "$file.bak_$BACKUP_SUFFIX"
        echo "  -> Backup created: $file.bak_$BACKUP_SUFFIX"
    fi
done

echo ""
echo "Applying new configuration..."

update_instrument "$NEW_INSTRUMENT"
update_constant "private static final int START_YEAR" "$NEW_START_YEAR"
update_constant "private static final int START_MONTH" "$NEW_START_MONTH"
update_constant "private static final int START_DAY" "$NEW_START_DAY"
update_constant "private static final int END_YEAR" "$NEW_END_YEAR"
update_constant "private static final int END_MONTH" "$NEW_END_MONTH"
update_constant "private static final int END_DAY" "$NEW_END_DAY"
update_string_constant "private static final String USERNAME" "$NEW_USERNAME"
update_string_constant "private static final String PASSWORD" "$NEW_PASSWORD"

echo -e "${GREEN}All files updated successfully.${NC}"

# ------------------------------------------------------------
# 7. Compile the Project
# ------------------------------------------------------------
echo ""
echo "Step 4: Compiling the project with new settings..."
mvn clean compile
echo -e "${GREEN}Compilation successful.${NC}"

# ------------------------------------------------------------
# 8. Final Instructions
# ------------------------------------------------------------
echo ""
echo "============================================================"
echo -e "${GREEN}CONFIGURATION COMPLETE${NC}"
echo "============================================================"
echo ""
echo "Your project is now configured for:"
echo "  Symbol    : $NEW_INSTRUMENT"
echo "  Date Range: $NEW_START_YEAR-$NEW_START_MONTH-$NEW_START_DAY to $NEW_END_YEAR-$NEW_END_MONTH-$NEW_END_DAY"
echo ""
echo "To start the download, run:"
echo "  mvn exec:java -Dexec.mainClass=\"com.rws.dukas.JForex4Downloader\""
echo ""
echo "Or with screen (recommended for VPS):"
echo "  screen -S dukas-download"
echo "  mvn exec:java -Dexec.mainClass=\"com.rws.dukas.JForex4Downloader\""
echo "  (Ctrl+A, D to detach)"
echo ""
echo "To restore previous configuration:"
echo "  cp src/main/java/com/rws/dukas/JForex4Downloader.java.bak_$BACKUP_SUFFIX src/main/java/com/rws/dukas/JForex4Downloader.java"
echo "  cp src/main/java/com/rws/dukas/JForex4ArchiveAuditor.java.bak_$BACKUP_SUFFIX src/main/java/com/rws/dukas/JForex4ArchiveAuditor.java"
echo "  mvn clean compile"
echo "============================================================"