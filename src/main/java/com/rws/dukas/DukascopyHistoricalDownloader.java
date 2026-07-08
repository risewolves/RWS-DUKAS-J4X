package com.rws.dukas;

import com.dukascopy.api.*;
import com.dukascopy.api.system.ITesterClient;
import com.dukascopy.api.system.TesterFactory;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DukascopyHistoricalDownloader {

    private static final String USERNAME = "DEMO2YciXg";
    private static final String PASSWORD = "YciXg";
    private static final String URL = "http://platform.dukascopy.com/demo_3/jforex_3.jnlp";

    private static final Instrument INSTRUMENT = Instrument.EURUSD;
    private static final String OUTPUT_DIR = "./ohlcv_output/";

    private static final YearMonth START = YearMonth.of(2005, 1);
    private static final YearMonth END = YearMonth.of(2026, 6);
    private static final int PARTIAL_DAY = 3;
    private static final int PARTIAL_MONTH = 7;
    private static final int PARTIAL_YEAR = 2026;

    private static final long STRATEGY_TIMEOUT_MINUTES = 60;
    private static final long WAIT_MS = 180000; // 3 minutes

    private static final SimpleDateFormat LOG_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter CSV_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        System.out.println("=== Historical Downloader (Daily chunks, 3min wait) ===");
        System.out.println("Instrument: " + INSTRUMENT);
        System.out.println("Start: " + START + "-01");
        System.out.println("End:   " + PARTIAL_YEAR + "-" + String.format("%02d", PARTIAL_MONTH) + "-" + PARTIAL_DAY);
        System.out.println("Chunk size: 1 day");
        System.out.println("Wait between days: 3 minutes");
        System.out.println("========================================================");

        try {
            Files.createDirectories(Paths.get(OUTPUT_DIR));
        } catch (IOException e) {
            System.err.println("Failed to create output directory: " + e.getMessage());
            return;
        }

        ITesterClient client = null;
        try {
            client = TesterFactory.getDefaultInstance();
            System.out.println("Connecting to Dukascopy Tester...");
            client.connect(URL, USERNAME, PASSWORD);

            int wait = 0;
            while (!client.isConnected() && wait < 30) {
                Thread.sleep(1000);
                wait++;
            }
            if (!client.isConnected()) {
                throw new RuntimeException("Failed to connect after 30 seconds");
            }
            System.out.println("Connected successfully.");

            // Set full data interval
            LocalDateTime startLdt = LocalDateTime.of(START.getYear(), START.getMonth(), 1, 0, 0, 0);
            LocalDateTime endLdt = LocalDateTime.of(PARTIAL_YEAR, PARTIAL_MONTH, PARTIAL_DAY, 23, 59, 59);
            long fromMillis = toEpochMillis(startLdt);
            long toMillis = toEpochMillis(endLdt);

            client.setDataInterval(ITesterClient.DataLoadingMethod.ALL_TICKS, fromMillis, toMillis);
            System.out.println("Data interval set: " + startLdt + " to " + endLdt);

            Set<Instrument> instruments = new HashSet<>();
            instruments.add(INSTRUMENT);
            client.setSubscribedInstruments(instruments);
            System.out.println("Subscribed to " + INSTRUMENT);

            System.out.println("Downloading historical data for " + INSTRUMENT + " ...");
            System.out.println("This may take 5-15 minutes. Please wait.");
            client.downloadData(null);
            System.out.println("Data download completed.");

            YearMonth current = START;
            while (current.isBefore(END) || current.equals(END)) {
                processMonth(client, current);
                current = current.plusMonths(1);
            }

            processPartialMonth(client);

            System.out.println("All months processed successfully.");

        } catch (Exception e) {
            System.err.println("FATAL ERROR: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (client != null) {
                try {
                    client.disconnect();
                } catch (Exception ignored) {}
            }
        }
    }

    private static void processMonth(ITesterClient client, YearMonth yearMonth) throws Exception {
        String monthStr = yearMonth.toString();
        String outputFile = OUTPUT_DIR + "EURUSD_" + monthStr + "_1min_OHLCV.csv";

        if (Files.exists(Paths.get(outputFile))) {
            log("Skipping " + monthStr + " (file already exists)");
            return;
        }

        log("Processing " + monthStr + " (daily chunks)");

        CountDownLatch latch = new CountDownLatch(1);
        DailyChunkStrategy strategy = new DailyChunkStrategy(
                yearMonth.atDay(1).atStartOfDay(),
                yearMonth.atEndOfMonth().atTime(23, 59, 59),
                outputFile,
                latch
        );

        long strategyId = client.startStrategy(strategy);

        boolean finished = latch.await(STRATEGY_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            log("WARNING: Strategy timed out for " + monthStr);
        }

        client.stopStrategy(strategyId);
        log("Done: " + monthStr);
    }

    private static void processPartialMonth(ITesterClient client) throws Exception {
        String monthStr = String.format("%d-%02d", PARTIAL_YEAR, PARTIAL_MONTH);
        String outputFile = OUTPUT_DIR + "EURUSD_" + monthStr + "_1min_OHLCV.csv";

        if (Files.exists(Paths.get(outputFile))) {
            log("Skipping partial " + monthStr + " (file already exists)");
            return;
        }

        log("Processing partial " + monthStr + " (1-" + PARTIAL_DAY + ")");

        CountDownLatch latch = new CountDownLatch(1);
        DailyChunkStrategy strategy = new DailyChunkStrategy(
                LocalDateTime.of(PARTIAL_YEAR, PARTIAL_MONTH, 1, 0, 0, 0),
                LocalDateTime.of(PARTIAL_YEAR, PARTIAL_MONTH, PARTIAL_DAY, 23, 59, 59),
                outputFile,
                latch
        );

        long strategyId = client.startStrategy(strategy);

        boolean finished = latch.await(STRATEGY_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            log("WARNING: Strategy timed out for partial " + monthStr);
        }

        client.stopStrategy(strategyId);
        log("Done: partial " + monthStr);
    }

    private static long toEpochMillis(LocalDateTime ldt) {
        return ldt.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    private static void log(String msg) {
        System.out.println("[" + LOG_DATE_FORMAT.format(new Date()) + "] " + msg);
    }

    // ==================== STRATEGY CLASS (DAILY CHUNKS) ====================

    private static class DailyChunkStrategy implements IStrategy {

        private final LocalDateTime startLdt;
        private final LocalDateTime endLdt;
        private final String outputFile;
        private final CountDownLatch latch;

        private final Map<Long, BarData> allBars = new TreeMap<>();

        public DailyChunkStrategy(LocalDateTime startLdt, LocalDateTime endLdt, String outputFile, CountDownLatch latch) {
            this.startLdt = startLdt;
            this.endLdt = endLdt;
            this.outputFile = outputFile;
            this.latch = latch;
        }

        @Override
        public void onStart(IContext context) throws JFException {
            System.out.println("Strategy started: " + startLdt + " → " + endLdt);

            try {
                context.setSubscribedInstruments(new HashSet<>(Arrays.asList(INSTRUMENT)), true);
                System.out.println("Instrument " + INSTRUMENT + " subscribed.");

                IHistory history = context.getHistory();

                LocalDateTime currentDay = startLdt.withHour(0).withMinute(0).withSecond(0).withNano(0);
                int dayCount = 0;

                while (!currentDay.isAfter(endLdt)) {
                    LocalDateTime dayEnd = currentDay.plusDays(1).minusSeconds(1);
                    if (dayEnd.isAfter(endLdt)) {
                        dayEnd = endLdt;
                    }

                    long from = toEpochMillis(currentDay);
                    long to = toEpochMillis(dayEnd);

                    dayCount++;
                    System.out.println("Fetching day " + dayCount + ": " + currentDay + " → " + dayEnd);

                    List<IBar> bars = history.getBars(
                            INSTRUMENT,
                            Period.ONE_MIN,
                            OfferSide.BID,
                            from,
                            to
                    );

                    System.out.println("  -> Fetched " + bars.size() + " bars");
                    addBars(bars);

                    currentDay = currentDay.plusDays(1);

                    // Wait 3 minutes after each day, except the last day of the month
                    if (!currentDay.isAfter(endLdt)) {
                        System.out.println("Waiting 3 minutes before next day...");
                        try {
                            Thread.sleep(WAIT_MS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }

                if (allBars.isEmpty()) {
                    System.out.println("No bars fetched. Skipping.");
                    return;
                }

                writeCsv();
                System.out.println("Wrote " + allBars.size() + " bars to " + outputFile);

            } catch (Exception e) {
                System.err.println("Error in strategy execution: " + e.getMessage());
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        }

        private void addBars(List<IBar> bars) {
            for (IBar bar : bars) {
                long time = bar.getTime();
                if (!allBars.containsKey(time)) {
                    BarData data = new BarData();
                    data.open = bar.getOpen();
                    data.high = bar.getHigh();
                    data.low = bar.getLow();
                    data.close = bar.getClose();
                    data.avgSpread = 0.0;
                    data.volumeUnits = bar.getVolume() * 1_000_000.0;
                    allBars.put(time, data);
                }
            }
        }

        private void writeCsv() throws IOException {
            try (PrintWriter w = new PrintWriter(new FileWriter(outputFile))) {
                w.println("DATETIME,OPEN,HIGH,LOW,CLOSED,AV_SPREAD,VOLUME");
                for (Map.Entry<Long, BarData> entry : allBars.entrySet()) {
                    BarData b = entry.getValue();
                    String dt = Instant.ofEpochMilli(entry.getKey())
                            .atZone(ZoneOffset.UTC)
                            .format(CSV_DATE_FORMAT);
                    w.printf("%s,%.5f,%.5f,%.5f,%.5f,%.5f,%.0f%n",
                            dt, b.open, b.high, b.low, b.close, b.avgSpread, b.volumeUnits);
                }
            }
        }

        @Override public void onTick(Instrument instrument, ITick tick) throws JFException {}
        @Override public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {}
        @Override public void onMessage(IMessage message) throws JFException {}
        @Override public void onAccount(IAccount account) throws JFException {}
        @Override public void onStop() throws JFException {}
    }

    private static class BarData {
        double open, high, low, close;
        double avgSpread;
        double volumeUnits;
    }
}