package singlejartest;

import com.dukascopy.api.Instrument;
import com.dukascopy.api.system.ClientFactory;
import com.dukascopy.api.system.IClient;
import com.dukascopy.api.system.ISystemListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.HashSet;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    // CHANGE THIS TO YOUR CREDENTIALS
    private static String jnlpUrl = "http://platform.dukascopy.com/demo_3/jforex_3.jnlp";
    private static String userName = "DEMO2YciXg";
    private static String password = "YciXg";

    private static IClient client;
    private static int lightReconnects = 3;

    public static void main(String[] args) throws Exception {
        client = ClientFactory.getDefaultInstance();
        setSystemListener();
        tryToConnect();
        subscribeToInstruments();

        LOGGER.info("Starting strategy");
        client.startStrategy(new MA_Play());
        // now it's running
    }

    private static void setSystemListener() {
        client.setSystemListener(new ISystemListener() {
            @Override public void onStart(long processId) {
                LOGGER.info("Strategy started: " + processId);
            }
            @Override public void onStop(long processId) {
                LOGGER.info("Strategy stopped: " + processId);
                if (client.getStartedStrategies().size() == 0) {
                    System.exit(0);
                }
            }
            @Override public void onConnect() {
                LOGGER.info("Connected");
                lightReconnects = 3;
            }
            @Override public void onDisconnect() {
                tryToReconnect();
            }
        });
    }

    private static void tryToConnect() throws Exception {
        LOGGER.info("Connecting...");
        client.connect(jnlpUrl, userName, password);
        int i = 10;
        while (i > 0 && !client.isConnected()) {
            Thread.sleep(1000);
            i--;
        }
        if (!client.isConnected()) {
            LOGGER.error("Failed to connect");
            System.exit(1);
        }
    }

    private static void tryToReconnect() {
        // ... (reconnection logic)
    }

    private static void subscribeToInstruments() {
        Set<Instrument> instruments = new HashSet<>();
        instruments.add(Instrument.EURUSD);
        LOGGER.info("Subscribing instruments...");
        client.setSubscribedInstruments(instruments);
    }
}