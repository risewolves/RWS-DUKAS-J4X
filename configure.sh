#!/bin/bash
# ============================================================
# CONFIGURE SCRIPT — Easy Symbol & Date Range Updater
# 
# This script updates Instrument, Date Range, and Credentials.
# After successful compilation, it offers to start the download
# immediately (foreground or screen).
# 
# USAGE: ./configure.sh
# ============================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

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
    local value=$(grep -E "$pattern" "$file" | head -1 | sed -E 's/.*= (.*);/\1/' | xargs)
    echo "$value"
}

prompt_value() {
    local var_name=$1
    local current_value=$2
    local prompt_text=$3
    local new_value
    read -p "$(echo -e "${BLUE}Current $var_name:${NC} $current_value\n$prompt_text (press Enter to keep current): ")" new_value
    if [ -z "$new_value" ]; then
        echo "$current_value"
    else
        echo "$new_value"
    fi
}

is_valid_syntax() {
    local value=$1
    if [[ "$value" =~ ^Instrument\.[A-Z0-9_]+$ ]]; then
        return 0
    else
        return 1
    fi
}

validate_and_fix_instrument() {
    local input=$1
    input=$(echo "$input" | xargs)

    if [[ "$input" == *","* ]] || [[ "$input" == *" "* ]]; then
        echo -e "${RED}ERROR: Cannot contain commas or spaces. Enter ONE symbol.${NC}" >&2
        return 1
    fi

    if [[ ! "$input" =~ ^Instrument\. ]]; then
        input="Instrument.$input"
    fi

    local suffix=$(echo "$input" | sed 's/^Instrument\.//')
    if [[ ! "$suffix" =~ ^[A-Z0-9_]+$ ]]; then
        echo -e "${RED}ERROR: Invalid characters in '$suffix'. Use only A-Z, 0-9, _.${NC}" >&2
        return 1
    fi

    echo "$input"
    return 0
}

validate_month() {
    local month=$1
    if [[ ! "$month" =~ ^[0-9]+$ ]] || [ "$month" -lt 1 ] || [ "$month" -gt 12 ]; then
        echo -e "${RED}Invalid month: $month. Must be 1-12.${NC}"
        return 1
    fi
    return 0
}

validate_day() {
    local day=$1
    if [[ ! "$day" =~ ^[0-9]+$ ]] || [ "$day" -lt 1 ] || [ "$day" -gt 31 ]; then
        echo -e "${RED}Invalid day: $day. Must be 1-31.${NC}"
        return 1
    fi
    return 0
}

validate_year() {
    local year=$1
    if [[ ! "$year" =~ ^[0-9]{4}$ ]]; then
        echo -e "${RED}Invalid year: '$year'. Must be 4 digits.${NC}"
        return 1
    fi
    return 0
}

validate_date_exists() {
    if date -d "$1-$2-$3" >/dev/null 2>&1; then
        return 0
    else
        echo -e "${RED}Invalid date: $1-$2-$3 does not exist.${NC}"
        return 1
    fi
}

update_constant() {
    for file in "${FILES[@]}"; do
        sed -i "s/$1 = .*;/$1 = $2;/" "$file"
    done
}

update_string_constant() {
    for file in "${FILES[@]}"; do
        sed -i "s|$1 = \".*\";|$1 = \"$2\";|" "$file"
    done
}

update_instrument() {
    for file in "${FILES[@]}"; do
        sed -i "s/private static final Instrument INSTRUMENT = .*;/private static final Instrument INSTRUMENT = $1;/" "$file"
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
CURRENT_JNLP_URL=$(read_current_value "private static final String JNLP_URL")
CURRENT_BATCH_YEARS=$(read_current_value "private static final int BATCH_YEARS")

echo -e "${YELLOW}Current System Settings (Read-Only):${NC}"
echo "----------------------------------------"
echo "  JNLP URL        : $CURRENT_JNLP_URL"
echo "  Batch Years     : $CURRENT_BATCH_YEARS"
echo "----------------------------------------"
echo ""

# ------------------------------------------------------------
# 3. Prompt for Instrument
# ------------------------------------------------------------
echo -e "${YELLOW}Step 1: Instrument (Symbol)${NC}"
echo "Format: Instrument.GBPUSD or just GBPUSD"
echo ""

while true; do
    if ! is_valid_syntax "$CURRENT_INSTRUMENT"; then
        echo -e "${YELLOW}Current value '$CURRENT_INSTRUMENT' is INVALID (contains comma/spaces).${NC}"
        echo "You MUST type a new valid instrument."
        read -p "$(echo -e "${BLUE}Enter Instrument (required):${NC} ")" RAW_INPUT
    else
        RAW_INPUT=$(prompt_value "INSTRUMENT" "$CURRENT_INSTRUMENT" "Enter Instrument")
    fi

    if [ -z "$RAW_INPUT" ]; then
        if is_valid_syntax "$CURRENT_INSTRUMENT"; then
            NEW_INSTRUMENT="$CURRENT_INSTRUMENT"
            echo -e "${GREEN}Keeping: $NEW_INSTRUMENT${NC}"
            break
        else
            continue
        fi
    fi

    if FIXED=$(validate_and_fix_instrument "$RAW_INPUT" 2>/dev/null); then
        NEW_INSTRUMENT="$FIXED"
        echo -e "${GREEN}Using: $NEW_INSTRUMENT${NC}"
        break
    else
        continue
    fi
done

# ------------------------------------------------------------
# 4. Prompt for Date Range
# ------------------------------------------------------------
echo ""
echo -e "${YELLOW}Step 2: Date Range${NC}"
echo "----------------------------------------"

while true; do
    NEW_START_YEAR=$(prompt_value "START_YEAR" "$CURRENT_START_YEAR" "Enter START_YEAR")
    validate_year "$NEW_START_YEAR" && break
done
while true; do
    NEW_START_MONTH=$(prompt_value "START_MONTH" "$CURRENT_START_MONTH" "Enter START_MONTH (1-12)")
    validate_month "$NEW_START_MONTH" && break
done
while true; do
    NEW_START_DAY=$(prompt_value "START_DAY" "$CURRENT_START_DAY" "Enter START_DAY (1-31)")
    validate_day "$NEW_START_DAY" && break
done
validate_date_exists "$NEW_START_YEAR" "$NEW_START_MONTH" "$NEW_START_DAY" || exit 1

while true; do
    NEW_END_YEAR=$(prompt_value "END_YEAR" "$CURRENT_END_YEAR" "Enter END_YEAR")
    validate_year "$NEW_END_YEAR" && break
done
while true; do
    NEW_END_MONTH=$(prompt_value "END_MONTH" "$CURRENT_END_MONTH" "Enter END_MONTH (1-12)")
    validate_month "$NEW_END_MONTH" && break
done
while true; do
    NEW_END_DAY=$(prompt_value "END_DAY" "$CURRENT_END_DAY" "Enter END_DAY (1-31)")
    validate_day "$NEW_END_DAY" && break
done
validate_date_exists "$NEW_END_YEAR" "$NEW_END_MONTH" "$NEW_END_DAY" || exit 1

START_EPOCH=$(date -d "$NEW_START_YEAR-$NEW_START_MONTH-$NEW_START_DAY" +%s 2>/dev/null)
END_EPOCH=$(date -d "$NEW_END_YEAR-$NEW_END_MONTH-$NEW_END_DAY" +%s 2>/dev/null)
if [ "$START_EPOCH" -gt "$END_EPOCH" ]; then
    echo -e "${RED}Start date must be before end date.${NC}"
    exit 1
fi

# ------------------------------------------------------------
# 5. Prompt for Credentials
# ------------------------------------------------------------
echo ""
echo -e "${YELLOW}Step 3: Credentials${NC}"
echo "----------------------------------------"
NEW_USERNAME=$(prompt_value "USERNAME" "$CURRENT_USERNAME" "Enter USERNAME")
NEW_PASSWORD=$(prompt_value "PASSWORD" "$CURRENT_PASSWORD" "Enter PASSWORD")

# ------------------------------------------------------------
# 6. Summary & Confirmation
# ------------------------------------------------------------
echo ""
echo "============================================================"
echo -e "${YELLOW}Summary:${NC}"
echo "  Instrument : $NEW_INSTRUMENT"
echo "  Start      : $NEW_START_YEAR-$NEW_START_MONTH-$NEW_START_DAY"
echo "  End        : $NEW_END_YEAR-$NEW_END_MONTH-$NEW_END_DAY"
echo "  Username   : $NEW_USERNAME"
echo "============================================================"
read -p "Apply changes? (y/N): " CONFIRM
if [[ ! "$CONFIRM" =~ ^[Yy]$ ]]; then
    echo -e "${RED}Cancelled.${NC}"
    exit 0
fi

# ------------------------------------------------------------
# 7. Backup & Apply
# ------------------------------------------------------------
BACKUP_SUFFIX=$(date +%Y%m%d_%H%M%S)
for file in "${FILES[@]}"; do
    cp "$file" "$file.bak_$BACKUP_SUFFIX"
    echo "Backup: $file.bak_$BACKUP_SUFFIX"
done

update_instrument "$NEW_INSTRUMENT"
update_constant "private static final int START_YEAR" "$NEW_START_YEAR"
update_constant "private static final int START_MONTH" "$NEW_START_MONTH"
update_constant "private static final int START_DAY" "$NEW_START_DAY"
update_constant "private static final int END_YEAR" "$NEW_END_YEAR"
update_constant "private static final int END_MONTH" "$NEW_END_MONTH"
update_constant "private static final int END_DAY" "$NEW_END_DAY"
update_string_constant "private static final String USERNAME" "$NEW_USERNAME"
update_string_constant "private static final String PASSWORD" "$NEW_PASSWORD"

echo -e "${GREEN}Configuration applied.${NC}"

# ------------------------------------------------------------
# 8. Compile Project
# ------------------------------------------------------------
echo ""
echo "Step 4: Compiling project with new settings..."
mvn clean compile
echo -e "${GREEN}Compilation successful.${NC}"

# ------------------------------------------------------------
# 9. Ask to start download immediately
# ------------------------------------------------------------
echo ""
read -p "Do you want to start the download now? (y/N): " START_NOW
if [[ ! "$START_NOW" =~ ^[Yy]$ ]]; then
    echo ""
    echo "To start the download later, run:"
    echo "  mvn exec:java -Dexec.mainClass=\"com.rws.dukas.JForex4Downloader\""
    echo "Or with screen: screen -S dukas-download"
    exit 0
fi

echo ""
echo "Choose how to run:"
echo "  (f) Foreground  - logs appear in this terminal (press Ctrl+C to stop)"
echo "  (s) Screen      - runs in background, survives SSH disconnect (recommended)"
read -p "Your choice (f/s): " RUN_MODE

if [[ "$RUN_MODE" == "s" ]] || [[ "$RUN_MODE" == "S" ]]; then
    # Check if screen is installed
    if ! command -v screen &> /dev/null; then
        echo -e "${YELLOW}screen is not installed. Installing...${NC}"
        sudo apt update && sudo apt install -y screen
    fi
    echo "Starting download in screen session 'dukas-download'..."
    screen -S dukas-download -dm bash -c "cd $PROJECT_DIR && mvn exec:java -Dexec.mainClass=\"com.rws.dukas.JForex4Downloader\""
    echo -e "${GREEN}Download started in background.${NC}"
    echo "To view logs: screen -r dukas-download"
    echo "To detach from screen: Ctrl+A, D"
    echo "To re-attach later: screen -r dukas-download"
else
    echo "Starting download in foreground (press Ctrl+C to stop)..."
    echo ""
    exec mvn exec:java -Dexec.mainClass="com.rws.dukas.JForex4Downloader"
fi