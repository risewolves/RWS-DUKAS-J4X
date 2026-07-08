# RWS-DUKAS-J4X — Tick-to-1-Minute OHLCV Exporter

This Java project connects to the Dukascopy JForex platform. It pulls raw tick data (currently tested on **EUR/USD**) and converts it into clean 1‑minute OHLCV bars (Open, High, Low, Close, Volume) along with the average spread. The output is a set of CSV files, one per month.

You can use it in two modes:
- **Live capture** – subscribes to real‑time ticks and builds bars as they arrive. This is the most stable option and gives you accurate spreads.
- **Historical download** – tries to fetch ticks from 2005 onwards. It uses daily chunks with a pause between them to avoid overwhelming the connection.

---

## What It Actually Does

1. Connects to Dukascopy via either the live client (`IClient`) or the tester client (`ITesterClient`).
2. Collects tick data (bid, ask, volume).
3. Groups ticks into 1‑minute buckets.
4. For each bucket, it calculates:
   - `OPEN` – first bid price
   - `HIGH` – highest bid price
   - `LOW` – lowest bid price
   - `CLOSE` – last bid price
   - `AV_SPREAD` – average bid‑ask spread over that minute
   - `VOLUME` – total volume in absolute units (converted from millions)
5. Saves everything to a CSV file with a clear, month‑based naming scheme.

---

## Output Format

Every CSV has these 7 columns:

| Column | Description |
|--------|-------------|
| `DATETIME` | UTC timestamp of the bar start |
| `OPEN` | First bid price |
| `HIGH` | Highest bid price |
| `LOW` | Lowest bid price |
| `CLOSED` | Last bid price |
| `AV_SPREAD` | Average spread (ask minus bid) |
| `VOLUME` | Total volume in units |

Example row:
```
2026-07-08 07:30:00,1.14150,1.14165,1.14140,1.14155,0.00012,1500000.0
```

---

## Project Structure

```
RWS-DUKAS-J4X/
├── src/main/java/
│   ├── singlejartest/                    # Official examples + live capture
│   │   ├── Main.java                     # Dukascopy’s IClient example
│   │   ├── MA_Play.java                  # Sample strategy (prints ticks)
│   │   └── LiveCaptureStrategy.java      # Live tick capture + CSV export
│   └── com/rws/dukas/
│       ├── DukascopyHistoricalDownloader.java  # Historical downloader (chunked)
│       └── LiveTickTest.java             # Quick connection test
├── ohlcv_output/                         # All CSV files are saved here
├── logs/                                 # Log files (if enabled)
├── pom.xml                               # Maven configuration
└── README.md                             # This file
```

---

## Prerequisites

- **Java 21** or later (OpenJDK works fine).
- **Maven 3.6+**.
- A Dukascopy demo account. The code includes a demo login, but if it expires, you will need to renew it on the Dukascopy website and update the credentials in the source files.

---

## How to Build

Run this from the project root:

```bash
mvn clean package -DskipTests
```

Maven will produce a fat JAR at `target/dukascopy-downloader-1.0.0.jar`. This single file contains everything you need.

---

## How to Run

### Option 1 – Live capture (recommended for new data)

This uses `IClient`, which has been proven to work reliably. It receives ticks immediately and writes monthly CSV files.

```bash
java -cp target/dukascopy-downloader-1.0.0.jar singlejartest.LiveCaptureStrategy
```

- Let it run as long as you need.
- Press `Ctrl+C` to stop. The strategy will save the current month’s data before exiting.

### Option 2 – Historical download (2005 to 2026)

This uses `ITesterClient`. It processes one day at a time with a 3‑minute wait between days.

```bash
java -jar target/dukascopy-downloader-1.0.0.jar
```

- It will first download the entire historical range into the local cache (may take a few minutes).
- After that, it processes each month and saves CSV files.
- Months that already have a CSV file are skipped automatically.

---

## Customising the Settings

Most settings are at the top of the source files as `static final` constants:

- **Credentials** – `USERNAME`, `PASSWORD`, and `JNLP_URL` in each main class.
- **Instrument** – currently set to `Instrument.EURUSD`.
- **Date range** – `START` and `END` in `DukascopyHistoricalDownloader.java`.
- **Pause between chunks** – `WAIT_MS` (default is 3 minutes).

---

## Known Issues and Limitations

- **Historical downloads can time out** – The SDK’s internal HTTP client sometimes gives up after about 45 seconds, especially when loading large chunks. Even with daily chunking, some VPS networks (notably in London) trigger this timeout. This is a network routing problem, not a bug in the code.
- **Demo accounts expire** – The credentials `DEMO2YciXg` / `YciXg` are not permanent. If you see connection errors, visit the Dukascopy website, create a fresh demo account, and update the credentials in the Java files.
- **Live capture gives accurate spreads** – Since it works directly with ticks, the `AV_SPREAD` column is correct.
- **Historical bars have no spread** – When using pre‑aggregated `IBar` objects, the SDK does not provide spread information. In that case, `AV_SPREAD` is set to `0.0`.

---

## Troubleshooting

### Maven says `JAVA_HOME` is not set
Export it before building:
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

### No ticks arrive during live capture
Check that your demo account is still active. You can quickly test the connection with:
```bash
java -cp target/dukascopy-downloader-1.0.0.jar com.rws.dukas.LiveTickTest
```
If that prints ticks, the live capture strategy will work too.

### Historical download keeps timing out
You can try two things:
1. Reduce the chunk size further (e.g., to 1 hour) and increase the wait time.
2. Run the download on a VPS with better routing to Dukascopy’s servers (Switzerland or Germany usually work better).

---

## Final Thoughts

This project gives you a solid starting point for working with Dukascopy tick data. If you only care about collecting new data, the live capture strategy is the simplest and most reliable path. For backfilling many years of history, you may need to experiment with chunk sizes or network setup, but the tool gives you a clear starting point.

---