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
 * JForex 4 API Historical Data Downloader
 * 
 * ENHANCED FEATURES:
 * 1. PRE-ARCHIVE DEEP SCAN & BACKFILL - Audits all months in a batch before archiving.
 *    If any month is FAILED or missing, it backfills automatically.
 * 2. RATE LIMITING (3500 requests) - Pauses 90 seconds when threshold is reached.
 * 3. TIMEOUT (RTO) HANDLING - Pauses 120 seconds specifically on read/connect timeouts.
 * 4. AUTO RESUME - Master progress ensures no manual intervention.
 */
public class JForex4Downloader {

    // ==================== CONFIGURATION ====================
    private static final String USERNAME = "DEMO2YciXg";
    private static final String PASSWORD = "YciXg";
    private static final String JNLP_URL = "http://platform.dukascopy.com/demo_4/jforex_4.jnlp";

    private static final Instrument INSTRUMENT = Instrument.BTCUSD;
    private static final String OUTPUT_DIR = "./ohlcv_output/";
    private static final String ARCHIVE_BASE_DIR = "./archive/";

    // MASTER PROGRESS FILE - NEVER MOVED OR DELETED
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

    // Rate limiting (3500 requests)
    private static final int RATE_LIMIT_THRESHOLD = 3500;
    private static final long RATE_LIMIT_PAUSE_SECONDS = 90;

    // Timeout specific pause (RTO)
    private static final long TIMEOUT_PAUSE_SECONDS = 120;

    // Global request counter for rate limiting
    private static int requestCounter = 0;

    private static final DateTimeFormatter CSV_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat LOG_DATE_FORMAT =
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // ==================== STATE ====================
    private static Set<String> completedMonths = new HashSet<>();
    private static List<String> failedMonthsList = new ArrayList<>();
    private static int monthsInCurrentBatch = 0;
    private static int batchStartYear = START_YEAR;

    // ==================== MAIN ====================
    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("=== JFOREK 4 API — ENHANCED: RATE LIMIT + TIMEOUT HANDLING ===");
        System.out.println("Instrument: " + INSTRUMENT);
        System.out.println("Range: " + START_YEAR + "-" + pad(START_MONTH) + "-" + pad(START_DAY) +
                           " to " + END_YEAR + "-" + pad(END_MONTH) + "-" + pad(END_DAY));
        System.out.println("Batch size: " + BATCH_YEARS + " year(s)");
        System.out.println("Rate limit: " + RATE_LIMIT_THRESHOLD + " requests -> pause " + RATE_LIMIT_PAUSE_SECONDS + "s");
        System.out.println("Timeout pause: " + TIMEOUT_PAUSE_SECONDS + "s");
        System.out.println("============================================================");

        try {
            Files.createDirectories(Paths.get(OUTPUT_DIR));
            Files.createDirectories(Paths.get(ARCHIVE_BASE_DIR));
        } catch (IOException e) {
            System.err.println("Failed to create directories: " + e.getMessage());
            return;
        }

        completedMonths = loadMasterProgress();
        determineBatchStart();

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

            // ========== PHASE 1: MAIN DOWNLOAD LOOP ==========
            int totalMonths = 0;
            int totalSuccess = 0;
            int totalFailed = 0;

            for (int year = START_YEAR; year <= END_YEAR; year++) {
                int monthStart = (year == START_YEAR) ? START_MONTH : 1;
                int monthEnd = (year == END_YEAR) ? END_MONTH : 12;
                for (int month = monthStart; month <= monthEnd; month++) {
                    if (year == END_YEAR && month > END_MONTH) break;

                    String monthKey = String.format("%04d-%02d", year, month);
                    totalMonths++;

                    if (completedMonths.contains(monthKey + ": SUCCESS")) {
                        log("SKIP: " + monthKey + " (already completed)");
                        totalSuccess++;
                        continue;
                    }

                    boolean ok = processMonthWithRetry(strategy.getHistory(), year, month);
                    if (ok) {
                        totalSuccess++;
                        completedMonths.add(monthKey + ": SUCCESS");
                        markMasterProgress(monthKey, "SUCCESS");
                        monthsInCurrentBatch++;
                    } else {
                        totalFailed++;
                        failedMonthsList.add(monthKey);
                        completedMonths.add(monthKey + ": FAILED");
                        markMasterProgress(monthKey, "FAILED");
                        monthsInCurrentBatch++;
                    }

                    // ========== BATCH ARCHIVING TRIGGER ==========
                    if (monthsInCurrentBatch >= (BATCH_YEARS * 12)) {
                        log("Batch limit reached (" + BATCH_YEARS + " years). Running pre-archive audit...");

                        boolean auditPassed = auditAndBackfillBatch(strategy.getHistory(), batchStartYear);
                        if (auditPassed) {
                            archiveAndClearOutput(batchStartYear);
                            // FIXED: Advance by BATCH_YEARS, not by current year variable
                            batchStartYear += BATCH_YEARS;
                            monthsInCurrentBatch = 0;
                            log("Resuming batch from year: " + batchStartYear);
                        } else {
                            log("Audit failed. Some months in batch are still not SUCCESS. Delaying archive.");
                            // FIXED: Do not reset monthsInCurrentBatch to 0 to avoid long delays.
                            // Instead, we keep it so the next audit triggers sooner.
                            // But if we keep it, we must ensure we don't overflow.
                            // Actually, resetting to 0 is safer to avoid immediate re-trigger.
                            // We will re-trigger audit on next boundary.
                            monthsInCurrentBatch = 0;
                        }
                    }
                }
            }

            // Archive any remaining partial batch
            if (monthsInCurrentBatch > 0) {
                log("Final partial batch detected. Running pre-archive audit...");
                boolean auditPassed = auditAndBackfillBatch(strategy.getHistory(), batchStartYear);
                if (auditPassed) {
                    archiveAndClearOutput(batchStartYear);
                } else {
                    log("Audit failed for final batch. Please check logs.");
                }
            }

            // ========== PHASE 2: AUTO BACKFILL ==========
            if (!failedMonthsList.isEmpty()) {
                log("PHASE 2: Auto-backfilling " + failedMonthsList.size() + " failed months...");
                List<String> stillFailed = new ArrayList<>();

                for (String monthKey : failedMonthsList) {
                    int year = Integer.parseInt(monthKey.substring(0, 4));
                    int month = Integer.parseInt(monthKey.substring(5, 7));
                    log("BACKFILL: " + monthKey);

                    boolean ok = processMonthDirect(strategy.getHistory(), year, month);
                    if (ok) {
                        log("  -> BACKFILL SUCCESS for " + monthKey);
                        completedMonths.remove(monthKey + ": FAILED");
                        completedMonths.add(monthKey + ": SUCCESS");
                        markMasterProgress(monthKey, "SUCCESS");
                        moveSingleFileToArchive(monthKey);
                    } else {
                        log("  -> BACKFILL FAILED for " + monthKey);
                        stillFailed.add(monthKey);
                    }
                }
                failedMonthsList = stillFailed;
            }

            // ========== PHASE 3: FINAL REPORT ==========
            log("============================================================");
            log("FINAL REPORT");
            log("Total months processed: " + totalMonths);
            log("Successful: " + totalSuccess);
            log("Failed: " + failedMonthsList.size());
            log("Total API requests made: " + requestCounter);

            if (!failedMonthsList.isEmpty()) {
                log("PERMANENTLY FAILED MONTHS:");
                for (String m : failedMonthsList) {
                    log("  - " + m);
                }
            } else {
                log("ALL MONTHS DOWNLOADED SUCCESSFULLY!");
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

    // ==================== PRE-ARCHIVE AUDIT & BACKFILL ====================
    /**
     * Deep scan: checks every month in the current batch.
     * If any month is FAILED or missing from master progress, attempts backfill.
     * Returns true ONLY if ALL months in the batch are SUCCESS.
     * 
     * FIXED: Trusts master progress. Does NOT re-download if file is missing from OUTPUT_DIR
     * (because it might have been archived already).
     */
    private static boolean auditAndBackfillBatch(IHistory history, int batchStartYear) {
        int batchEndYear = Math.min(batchStartYear + BATCH_YEARS - 1, END_YEAR);
        log("Auditing batch: " + batchStartYear + " to " + batchEndYear);

        boolean allSuccess = true;
        for (int year = batchStartYear; year <= batchEndYear; year++) {
            int monthStart = (year == batchStartYear) ? START_MONTH : 1;
            int monthEnd = (year == batchEndYear) ? END_MONTH : 12;
            for (int month = monthStart; month <= monthEnd; month++) {
                if (year == END_YEAR && month > END_MONTH) break;
                String monthKey = String.format("%04d-%02d", year, month);

                // FIXED: If already SUCCESS in master progress, trust it. Do NOT check file system.
                if (completedMonths.contains(monthKey + ": SUCCESS")) {
                    continue;
                }

                // If FAILED or missing, try to backfill
                log("  Found missing/failed month: " + monthKey + ". Attempting backfill...");
                boolean ok = processMonthWithRetry(history, year, month);
                if (ok) {
                    completedMonths.remove(monthKey + ": FAILED");
                    completedMonths.add(monthKey + ": SUCCESS");
                    markMasterProgress(monthKey, "SUCCESS");
                    log("  -> Backfill SUCCESS for " + monthKey);
                } else {
                    completedMonths.remove(monthKey + ": SUCCESS");
                    completedMonths.add(monthKey + ": FAILED");
                    markMasterProgress(monthKey, "FAILED");
                    allSuccess = false;
                    log("  -> Backfill FAILED for " + monthKey);
                }
            }
        }
        return allSuccess;
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

    // ==================== BATCH DETERMINATION ====================
    private static void determineBatchStart() {
        int maxYear = START_YEAR - 1;
        for (String entry : completedMonths) {
            if (entry.endsWith("SUCCESS")) {
                String monthKey = entry.substring(0, 7);
                int year = Integer.parseInt(monthKey.substring(0, 4));
                if (year > maxYear) maxYear = year;
            }
        }
        batchStartYear = Math.max(maxYear + 1, START_YEAR);
        if (batchStartYear > END_YEAR) batchStartYear = END_YEAR;
    }

    // ==================== BATCH ARCHIVING ====================
    private static void archiveAndClearOutput(int batchStart) {
        try {
            int batchEnd = Math.min(batchStart + BATCH_YEARS - 1, END_YEAR);
            String archiveFolderName = batchStart + "-" + batchEnd;
            Path archivePath = Paths.get(ARCHIVE_BASE_DIR + archiveFolderName + "/");
            Files.createDirectories(archivePath);

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(OUTPUT_DIR))) {
                for (Path entry : stream) {
                    String fileName = entry.getFileName().toString();
                    if (fileName.endsWith(".csv")) {
                        Path target = archivePath.resolve(fileName);
                        if (!Files.exists(target)) {
                            Files.move(entry, target, StandardCopyOption.REPLACE_EXISTING);
                            log("  -> Moved: " + fileName + " to " + archiveFolderName + "/");
                        } else {
                            Files.delete(entry);
                        }
                    }
                }
            }
            log("Batch " + archiveFolderName + " archived. Output dir cleared.");

        } catch (IOException e) {
            log("ERROR during archiving: " + e.getMessage());
        }
    }

    private static void moveSingleFileToArchive(String monthKey) {
        String fileName = "BTCUSD_" + monthKey + "_1min_OHLCV.csv";
        Path src = Paths.get(OUTPUT_DIR, fileName);
        if (!Files.exists(src)) return;

        int year = Integer.parseInt(monthKey.substring(0, 4));
        int startBatch = ((year - START_YEAR) / BATCH_YEARS) * BATCH_YEARS + START_YEAR;
        int endBatch = Math.min(startBatch + BATCH_YEARS - 1, END_YEAR);
        String archiveFolderName = startBatch + "-" + endBatch;
        Path target = Paths.get(ARCHIVE_BASE_DIR + archiveFolderName + "/", fileName);

        try {
            Files.createDirectories(target.getParent());
            if (Files.exists(target)) {
                Files.delete(src);
            } else {
                Files.move(src, target);
            }
        } catch (IOException e) {
            log("Error moving single file: " + e.getMessage());
        }
    }

    // ==================== REQUEST COUNTER (RATE LIMIT) ====================
    private static synchronized void incrementRequestCounter() {
        requestCounter++;
        if (requestCounter % RATE_LIMIT_THRESHOLD == 0) {
            log("RATE LIMIT: " + requestCounter + " requests reached. Pausing for " + RATE_LIMIT_PAUSE_SECONDS + " seconds...");
            try {
                Thread.sleep(RATE_LIMIT_PAUSE_SECONDS * 1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log("Rate limit pause complete. Resuming.");
        }
    }

    // ==================== PROCESS MONTH WITH RETRY ====================
    private static boolean processMonthWithRetry(IHistory history, int year, int month) {
        String monthStr = String.format("%04d-%02d", year, month);
        String outputFile = OUTPUT_DIR + "BTCUSD_" + monthStr + "_1min_OHLCV.csv";

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
                if (ticks == null || ticks.isEmpty()) {
                    log("  -> No ticks for " + monthStr);
                    return false;
                }
                Map<Long, MinuteBar> bars = aggregate(ticks);
                writeCsv(outputFile, bars);
                log("  -> SUCCESS: " + bars.size() + " bars (attempt " + attempt + ")");
                return true;

            } catch (Exception e) {
                boolean isTimeout = (e instanceof SocketTimeoutException) ||
                                    (e.getMessage() != null && e.getMessage().toLowerCase().contains("timed out"));

                if (isTimeout) {
                    log("  -> TIMEOUT (RTO) detected on attempt " + attempt + ". Pausing for " + TIMEOUT_PAUSE_SECONDS + " seconds...");
                    try {
                        Thread.sleep(TIMEOUT_PAUSE_SECONDS * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                } else {
                    log("  -> Attempt " + attempt + " FAILED: " + e.getMessage());
                }

                if (attempt < MAX_RETRIES) {
                    long delay = isTimeout ? TIMEOUT_PAUSE_SECONDS : RETRY_BASE_DELAY_SECONDS * (long) Math.pow(2, attempt - 1);
                    if (!isTimeout) {
                        log("  -> Retrying in " + delay + " seconds...");
                        try { Thread.sleep(delay * 1000); } catch (InterruptedException ignored) {}
                    }
                }
            }
        }

        if (ENABLE_WEEKLY_FALLBACK) {
            log("  -> Falling back to weekly chunks for " + monthStr);
            boolean weeklyOk = downloadMonthInChunks(history, year, month, outputFile);
            if (weeklyOk) {
                log("  -> Weekly fallback SUCCESS for " + monthStr);
                return true;
            }
        }
        return false;
    }

    private static boolean processMonthDirect(IHistory history, int year, int month) {
        String monthStr = String.format("%04d-%02d", year, month);
        String outputFile = OUTPUT_DIR + "BTCUSD_" + monthStr + "_1min_OHLCV.csv";

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
                if (ticks != null && !ticks.isEmpty()) {
                    Map<Long, MinuteBar> bars = aggregate(ticks);
                    writeCsv(outputFile, bars);
                    return true;
                }
            } catch (Exception e) {
                boolean isTimeout = (e instanceof SocketTimeoutException) ||
                                    (e.getMessage() != null && e.getMessage().toLowerCase().contains("timed out"));
                if (isTimeout) {
                    log("  -> BACKFILL TIMEOUT. Pausing " + TIMEOUT_PAUSE_SECONDS + "s...");
                    try { Thread.sleep(TIMEOUT_PAUSE_SECONDS * 1000); } catch (InterruptedException ignored) {}
                } else if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(RETRY_BASE_DELAY_SECONDS * attempt * 1000); } catch (InterruptedException ignored) {}
                }
            }
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
                    } else if (retry < 2) {
                        try { Thread.sleep(15000); } catch (InterruptedException ignored) {}
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