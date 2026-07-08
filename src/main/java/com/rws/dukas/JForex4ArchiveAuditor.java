package com.rws.dukas;

import com.dukascopy.api.*;
import com.dukascopy.api.system.IClient;
import com.dukascopy.api.system.ClientFactory;

import java.io.*;
import java.net.SocketTimeoutException;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * JForex 4 Archive Auditor & Backfill Tool
 * 
 * This tool scans the ENTIRE archive directory for all months.
 * If a CSV file is missing, corrupt, or too small, it automatically
 * backfills (re-downloads) that month and replaces the corrupt file.
 * 
 * Purpose: Run this BEFORE migrating files to your local Mac.
 * 
 * Features:
 * - Physical file verification (exists & size > 5KB).
 * - Auto-backfill for corrupt/missing months.
 * - Updates master progress file.
 */
public class JForex4ArchiveAuditor {

    // ==================== CONFIGURATION ====================
    private static final String USERNAME = "DEMO2YciXg";
    private static final String PASSWORD = "YciXg";
    private static final String JNLP_URL = "http://platform.dukascopy.com/demo_4/jforex_4.jnlp";

    private static final Instrument INSTRUMENT = Instrument.EURUSD;
    private static final String OUTPUT_DIR = "./ohlcv_output/";
    private static final String ARCHIVE_BASE_DIR = "./archive/";
    private static final String MASTER_PROGRESS_FILE = "./.master_download_progress.txt";

    private static final int START_YEAR = 2005;
    private static final int START_MONTH = 1;
    private static final int START_DAY = 1;
    private static final int END_YEAR = 2026;
    private static final int END_MONTH = 7;
    private static final int END_DAY = 3;

    private static final int BATCH_YEARS = 3;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BASE_DELAY_SECONDS = 30;
    private static final boolean ENABLE_WEEKLY_FALLBACK = true;
    private static final int CHUNK_DAYS = 7;
    private static final long TIMEOUT_PAUSE_SECONDS = 120;

    // Minimum file size in bytes (5KB is safe for a 1-minute OHLCV month)
    private static final long MIN_VALID_FILE_SIZE = 5 * 1024;

    private static final DateTimeFormatter CSV_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat LOG_DATE_FORMAT =
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // ==================== STATE ====================
    private static Set<String> completedMonths = new HashSet<>();
    private static int requestCounter = 0;
    private static final int RATE_LIMIT_THRESHOLD = 3500;
    private static final long RATE_LIMIT_PAUSE_SECONDS = 90;

    // ==================== MAIN ====================
    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("=== JFOREK 4 ARCHIVE AUDITOR & BACKFILL ===");
        System.out.println("Instrument: " + INSTRUMENT);
        System.out.println("Range: " + START_YEAR + "-" + pad(START_MONTH) + "-" + pad(START_DAY) +
                           " to " + END_YEAR + "-" + pad(END_MONTH) + "-" + pad(END_DAY));
        System.out.println("Archive base: " + ARCHIVE_BASE_DIR);
        System.out.println("Min valid file size: " + MIN_VALID_FILE_SIZE + " bytes");
        System.out.println("============================================================");

        // 1. Load master progress
        completedMonths = loadMasterProgress();
        log("Loaded " + completedMonths.size() + " entries from master progress.");

        // 2. Prepare directories
        try {
            Files.createDirectories(Paths.get(OUTPUT_DIR));
            Files.createDirectories(Paths.get(ARCHIVE_BASE_DIR));
        } catch (IOException e) {
            System.err.println("Failed to create directories: " + e.getMessage());
            return;
        }

        // 3. Connect to Dukascopy
        IClient client = null;
        try {
            client = ClientFactory.getDefaultInstance();
            log("Connecting to Dukascopy (IClient)...");
            client.connect(JNLP_URL, USERNAME, PASSWORD);

            int wait = 0;
            while (!client.isConnected() && wait < 30) {
                Thread.sleep(1000);
                wait++;
            }
            if (!client.isConnected()) {
                throw new RuntimeException("Connection failed.");
            }
            log("Connected.");

            Set<Instrument> instruments = new HashSet<>();
            instruments.add(INSTRUMENT);
            client.setSubscribedInstruments(instruments);
            log("Subscribed to " + INSTRUMENT);

            log("Starting minimal strategy to get IHistory...");
            CountDownLatch latch = new CountDownLatch(1);
            HistoryHolderStrategy strategy = new HistoryHolderStrategy(latch);
            long strategyId = client.startStrategy(strategy);

            boolean init = latch.await(30, TimeUnit.SECONDS);
            if (!init || strategy.getHistory() == null) {
                throw new RuntimeException("Failed to get IHistory.");
            }
            log("IHistory acquired.");

            // ========== AUDIT LOOP ==========
            int totalMonths = 0;
            int corruptOrMissing = 0;
            int backfillSuccess = 0;
            int backfillFailed = 0;

            for (int year = START_YEAR; year <= END_YEAR; year++) {
                int monthStart = (year == START_YEAR) ? START_MONTH : 1;
                int monthEnd = (year == END_YEAR) ? END_MONTH : 12;
                for (int month = monthStart; month <= monthEnd; month++) {
                    if (year == END_YEAR && month > END_MONTH) break;

                    String monthKey = String.format("%04d-%02d", year, month);
                    totalMonths++;

                    // 4. Check physical file in archive
                    boolean fileValid = checkArchiveFile(monthKey);

                    if (fileValid) {
                        // If file is valid, ensure master progress says SUCCESS
                        if (!completedMonths.contains(monthKey + ": SUCCESS")) {
                            log("FIX: " + monthKey + " file exists but master progress missing. Updating...");
                            markMasterProgress(monthKey, "SUCCESS");
                        }
                        continue;
                    }

                    // 5. File is missing or corrupt -> BACKFILL
                    corruptOrMissing++;
                    log("BACKFILL NEEDED: " + monthKey + " (missing or corrupt)");

                    // Remove any old FAILED/SUCCESS status to force clean download
                    completedMonths.remove(monthKey + ": SUCCESS");
                    completedMonths.remove(monthKey + ": FAILED");

                    boolean ok = processMonthWithRetry(strategy.getHistory(), year, month);
                    if (ok) {
                        backfillSuccess++;
                        markMasterProgress(monthKey, "SUCCESS");
                        // Move the newly created CSV to the correct archive folder
                        moveSingleFileToArchive(monthKey);
                        log("  -> BACKFILL SUCCESS for " + monthKey);
                    } else {
                        backfillFailed++;
                        markMasterProgress(monthKey, "FAILED");
                        log("  -> BACKFILL FAILED for " + monthKey);
                    }
                }
            }

            // ========== FINAL REPORT ==========
            log("============================================================");
            log("AUDIT FINAL REPORT");
            log("Total months expected: " + totalMonths);
            log("Corrupt / Missing found: " + corruptOrMissing);
            log("Backfill successful: " + backfillSuccess);
            log("Backfill failed: " + backfillFailed);
            log("Total API requests made: " + requestCounter);

            if (backfillFailed == 0) {
                log("ALL MONTHS ARE VALID AND ARCHIVED. SAFE TO MIGRATE TO MAC!");
            } else {
                log("WARNING: " + backfillFailed + " months could not be backfilled.");
                log("Review the logs above and re-run this auditor later.");
            }

            client.stopStrategy(strategyId);
            client.disconnect();

        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (client != null) {
                try { client.disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    // ==================== CHECK ARCHIVE FILE ====================
    private static boolean checkArchiveFile(String monthKey) {
        String fileName = "EURUSD_" + monthKey + "_1min_OHLCV.csv";
        int year = Integer.parseInt(monthKey.substring(0, 4));

        // Determine correct archive folder
        int startBatch = ((year - START_YEAR) / BATCH_YEARS) * BATCH_YEARS + START_YEAR;
        int endBatch = Math.min(startBatch + BATCH_YEARS - 1, END_YEAR);
        String archiveFolderName = startBatch + "-" + endBatch;
        Path target = Paths.get(ARCHIVE_BASE_DIR + archiveFolderName + "/", fileName);

        if (!Files.exists(target)) {
            log("  -> FILE MISSING: " + target);
            return false;
        }

        try {
            long size = Files.size(target);
            if (size < MIN_VALID_FILE_SIZE) {
                log("  -> FILE TOO SMALL: " + target + " (" + size + " bytes)");
                return false;
            }
            // Optional: Quick row count check (header + at least 1 bar)
            try (BufferedReader reader = Files.newBufferedReader(target)) {
                String header = reader.readLine();
                if (header == null || !header.startsWith("DATETIME,OPEN")) {
                    log("  -> FILE CORRUPT (invalid header): " + target);
                    return false;
                }
                String firstLine = reader.readLine();
                if (firstLine == null || firstLine.trim().isEmpty()) {
                    log("  -> FILE CORRUPT (no data rows): " + target);
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            log("  -> FILE READ ERROR: " + target + " (" + e.getMessage() + ")");
            return false;
        }
    }

    // ==================== MASTER PROGRESS ====================
    private static Set<String> loadMasterProgress() {
        Set<String> progress = new HashSet<>();
        Path path = Paths.get(MASTER_PROGRESS_FILE);
        if (!Files.exists(path)) return progress;
        try (BufferedReader r = Files.newBufferedReader(path)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) progress.add(line);
            }
        } catch (IOException e) {
            log("Warning: Could not load master progress.");
        }
        return progress;
    }

    private static void markMasterProgress(String monthKey, String status) {
        String entry = monthKey + ": " + status;
        completedMonths.remove(monthKey + ": SUCCESS");
        completedMonths.remove(monthKey + ": FAILED");
        completedMonths.add(entry);

        try (PrintWriter w = new PrintWriter(new FileWriter(MASTER_PROGRESS_FILE))) {
            for (String e : completedMonths) {
                w.println(e);
            }
        } catch (IOException e) {
            log("Warning: Could not write to master progress.");
        }
    }

    // ==================== MOVE SINGLE FILE TO ARCHIVE ====================
    private static void moveSingleFileToArchive(String monthKey) {
        String fileName = "EURUSD_" + monthKey + "_1min_OHLCV.csv";
        Path src = Paths.get(OUTPUT_DIR, fileName);
        if (!Files.exists(src)) return;

        int year = Integer.parseInt(monthKey.substring(0, 4));
        int startBatch = ((year - START_YEAR) / BATCH_YEARS) * BATCH_YEARS + START_YEAR;
        int endBatch = Math.min(startBatch + BATCH_YEARS - 1, END_YEAR);
        String archiveFolderName = startBatch + "-" + endBatch;
        Path target = Paths.get(ARCHIVE_BASE_DIR + archiveFolderName + "/", fileName);

        try {
            Files.createDirectories(target.getParent());
            // Overwrite the corrupt/missing file
            Files.move(src, target, StandardCopyOption.REPLACE_EXISTING);
            log("  -> Replaced archive file: " + target);
        } catch (IOException e) {
            log("  -> Error moving file to archive: " + e.getMessage());
        }
    }

    // ==================== REQUEST COUNTER ====================
    private static synchronized void incrementRequestCounter() {
        requestCounter++;
        if (requestCounter % RATE_LIMIT_THRESHOLD == 0) {
            log("RATE LIMIT: " + requestCounter + " requests reached. Pausing " + RATE_LIMIT_PAUSE_SECONDS + "s...");
            try { Thread.sleep(RATE_LIMIT_PAUSE_SECONDS * 1000); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log("Rate limit pause complete.");
        }
    }

    // ==================== PROCESS MONTH WITH RETRY (COPY PASTE) ====================
    private static boolean processMonthWithRetry(IHistory history, int year, int month) {
        String monthStr = String.format("%04d-%02d", year, month);
        String outputFile = OUTPUT_DIR + "EURUSD_" + monthStr + "_1min_OHLCV.csv";

        LocalDateTime startLdt = LocalDateTime.of(year, month, 1, 0, 0, 0);
        LocalDateTime endLdt;
        if (month == 12) {
            endLdt = LocalDateTime.of(year + 1, 1, 1, 0, 0, 0).minusSeconds(1);
        } else {
            endLdt = LocalDateTime.of(year, month + 1, 1, 0, 0, 0).minusSeconds(1);
        }
        LocalDateTime globalEnd = LocalDateTime.of(END_YEAR, END_MONTH, END_DAY, 23, 59, 59);
        if (endLdt.isAfter(globalEnd)) endLdt = globalEnd;

        long from = startLdt.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
        long to = endLdt.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                incrementRequestCounter();
                List<ITick> ticks = history.getTicks(INSTRUMENT, from, to);
                if (ticks == null || ticks.isEmpty()) return false;
                Map<Long, MinuteBar> bars = aggregate(ticks);
                writeCsv(outputFile, bars);
                return true;
            } catch (Exception e) {
                boolean isTimeout = (e instanceof SocketTimeoutException) ||
                                    (e.getMessage() != null && e.getMessage().toLowerCase().contains("timed out"));
                if (isTimeout) {
                    log("  -> TIMEOUT. Pausing " + TIMEOUT_PAUSE_SECONDS + "s...");
                    try { Thread.sleep(TIMEOUT_PAUSE_SECONDS * 1000); } catch (InterruptedException ignored) { return false; }
                }
                if (attempt < MAX_RETRIES && !isTimeout) {
                    long delay = RETRY_BASE_DELAY_SECONDS * (long) Math.pow(2, attempt - 1);
                    try { Thread.sleep(delay * 1000); } catch (InterruptedException ignored) {}
                }
            }
        }

        if (ENABLE_WEEKLY_FALLBACK) {
            log("  -> Falling back to weekly chunks...");
            return downloadMonthInChunks(history, year, month, outputFile);
        }
        return false;
    }

    private static boolean downloadMonthInChunks(IHistory history, int year, int month, String outputFile) {
        LocalDateTime startLdt = LocalDateTime.of(year, month, 1, 0, 0, 0);
        LocalDateTime endLdt;
        if (month == 12) {
            endLdt = LocalDateTime.of(year + 1, 1, 1, 0, 0, 0).minusSeconds(1);
        } else {
            endLdt = LocalDateTime.of(year, month + 1, 1, 0, 0, 0).minusSeconds(1);
        }
        LocalDateTime globalEnd = LocalDateTime.of(END_YEAR, END_MONTH, END_DAY, 23, 59, 59);
        if (endLdt.isAfter(globalEnd)) endLdt = globalEnd;

        List<LocalDateTime[]> chunks = new ArrayList<>();
        LocalDateTime current = startLdt;
        while (current.isBefore(endLdt)) {
            LocalDateTime chunkEnd = current.plusDays(CHUNK_DAYS).minusSeconds(1);
            if (chunkEnd.isAfter(endLdt)) chunkEnd = endLdt;
            chunks.add(new LocalDateTime[]{current, chunkEnd});
            current = chunkEnd.plusSeconds(1);
        }

        Map<Long, MinuteBar> allBars = new TreeMap<>();
        int successChunks = 0;

        for (LocalDateTime[] chunk : chunks) {
            long from = chunk[0].atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
            long to = chunk[1].atZone(ZoneOffset.UTC).toInstant().toEpochMilli();

            for (int retry = 1; retry <= 2; retry++) {
                try {
                    incrementRequestCounter();
                    List<ITick> ticks = history.getTicks(INSTRUMENT, from, to);
                    if (ticks != null && !ticks.isEmpty()) {
                        allBars.putAll(aggregate(ticks));
                        successChunks++;
                        break;
                    }
                } catch (Exception e) {
                    boolean isTimeout = (e instanceof SocketTimeoutException) ||
                                        (e.getMessage() != null && e.getMessage().toLowerCase().contains("timed out"));
                    if (isTimeout) {
                        log("    Chunk timeout. Pausing " + TIMEOUT_PAUSE_SECONDS + "s...");
                        try { Thread.sleep(TIMEOUT_PAUSE_SECONDS * 1000); } catch (InterruptedException ignored) {}
                    }
                }
            }
        }

        if (allBars.isEmpty()) return false;
        try { writeCsv(outputFile, allBars); return successChunks > 0; } catch (IOException e) { return false; }
    }

    // ==================== AGGREGATION & CSV ====================
    private static Map<Long, MinuteBar> aggregate(List<ITick> ticks) {
        Map<Long, MinuteBar> map = new TreeMap<>();
        for (ITick t : ticks) {
            long minute = (t.getTime() / 60000) * 60000;
            MinuteBar b = map.get(minute);
            if (b == null) {
                b = new MinuteBar();
                b.open = t.getBid(); b.high = t.getBid(); b.low = t.getBid(); b.close = t.getBid();
                b.askOpen = t.getAsk(); b.askHigh = t.getAsk(); b.askLow = t.getAsk(); b.askClose = t.getAsk();
                b.volume = t.getBidVolume();
                map.put(minute, b);
            } else {
                if (t.getBid() > b.high) b.high = t.getBid();
                if (t.getBid() < b.low) b.low = t.getBid();
                b.close = t.getBid();
                if (t.getAsk() > b.askHigh) b.askHigh = t.getAsk();
                if (t.getAsk() < b.askLow) b.askLow = t.getAsk();
                b.askClose = t.getAsk();
                b.volume += t.getBidVolume();
            }
        }
        return map;
    }

    private static void writeCsv(String path, Map<Long, MinuteBar> bars) throws IOException {
        try (PrintWriter w = new PrintWriter(new FileWriter(path))) {
            w.println("DATETIME,OPEN,HIGH,LOW,CLOSED,AV_SPREAD,VOLUME");
            for (Map.Entry<Long, MinuteBar> e : bars.entrySet()) {
                MinuteBar b = e.getValue();
                String dt = Instant.ofEpochMilli(e.getKey()).atZone(ZoneOffset.UTC).format(CSV_DATE_FORMAT);
                double spread = b.askClose - b.close;
                w.printf("%s,%.5f,%.5f,%.5f,%.5f,%.5f,%.0f%n",
                        dt, b.open, b.high, b.low, b.close, spread, b.volume);
            }
        }
    }

    // ==================== HELPERS ====================
    private static String pad(int n) { return String.format("%02d", n); }
    private static void log(String msg) {
        System.out.println("[" + LOG_DATE_FORMAT.format(new Date()) + "] " + msg);
    }

    // ==================== INNER CLASSES ====================
    private static class HistoryHolderStrategy implements IStrategy {
        private IHistory history;
        private final CountDownLatch latch;
        HistoryHolderStrategy(CountDownLatch latch) { this.latch = latch; }
        public IHistory getHistory() { return history; }
        @Override public void onStart(IContext ctx) throws JFException { this.history = ctx.getHistory(); latch.countDown(); }
        @Override public void onTick(Instrument i, ITick t) throws JFException {}
        @Override public void onBar(Instrument i, Period p, IBar a, IBar b) throws JFException {}
        @Override public void onMessage(IMessage m) throws JFException {}
        @Override public void onAccount(IAccount a) throws JFException {}
        @Override public void onStop() throws JFException {}
    }

    private static class MinuteBar {
        double open, high, low, close;
        double askOpen, askHigh, askLow, askClose;
        double volume;
    }
}