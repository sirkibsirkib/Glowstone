package net.glowstone.scheduler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Summary;
import io.prometheus.client.exporter.PushGateway;

public class YSCollector implements Runnable {

    public static final String NAME = "YSCollector";
    public static final String VERSION = "0.2";
    //
    private static final Logger LOGGER = Logger.getLogger(NAME);
    private static PushGateway GATEWAY = new_pushgateway();
    private static final CollectorRegistry REGISTRY = new CollectorRegistry();
    private static final Map<String, Summary> METRICS = new HashMap<String, Summary>();
    private static final Map<String, Long> TIMINGS = new HashMap<String, Long>();
    private static Thread pushThread;
    private static String module = "";

    private YSCollector() {
        // Only used when creating the push thread
    }
	
	private static PushGateway new_pushgateway() {
		String addr = System.getProperty("yardstick.gateway", "none");
        if (addr.equals("none")) {
            LOGGER.info("++ No pushgateway provided as Property. Disabling it.");
            return None;
        } else {
            LOGGER.info("++ Connecting to pushgateway at " + addr);
            LOGGER.info("++ Loaded " + NAME + " v" + VERSION);
            return new PushGateway(addr);
        }
	}

    public static synchronized void startModule(String newModule) {
        if (!module.isEmpty()) {
            LOGGER.log(Level.SEVERE, "Module '" + newModule + "' started whilst '" + module + "' was is still active.");
        }

        module = newModule;
    }

    public static synchronized void stopModule() {
        if (module.isEmpty()) {
            LOGGER.log(Level.SEVERE, "Module stopped twice!");
        }

        module = "";
    }

    public static synchronized void start(String key, String help) {
        if (pushgateway == null) return;
        ensureStarted();

        // Add module prefix, if necessary
        if (!module.isEmpty()) {
            key = module + "_" + key;
        }

        if (TIMINGS.containsKey(key)) {
            LOGGER.log(Level.SEVERE, "Timing: " + key + " started twice!");
            return;
        }

        // Ensure summary is present
        getSummary(key, help);

        TIMINGS.put(key, clock());
    }

    public static synchronized void stop(String key) {
        if (pushgateway == null) return;
        long stop = clock();

        // Add module prefix, if necessary
        if (!module.isEmpty()) {
            key = module + "_" + key;
        }

        if (!TIMINGS.containsKey(key)) {
            LOGGER.log(Level.SEVERE, "Timing: " + key + " stopped without start!");
            return;
        }

        long start = TIMINGS.get(key);

        TIMINGS.remove(key);

        Summary s = getSummary(key, null);
        s.observe(stop - start);
    }

    public static synchronized void ensureAllStopped() {
        for (String key : TIMINGS.keySet()) {
            LOGGER.log(Level.SEVERE, "Timing: " + key + " not stopped properly!");
        }
    }

    private static synchronized void ensureStarted() {
        if (pushThread != null) {
            return;
        }

        pushThread = new Thread(new YSCollector());
        pushThread.setName(NAME + "-Pusher");
        pushThread.setDaemon(true);
        pushThread.start();
        LOGGER.info("Started push thread");
    }

    private synchronized static Summary getSummary(String key, String help) {
        Summary s = METRICS.get(key);

        if (s != null) {
            return s;
        }

        if (help == null) {
            throw new IllegalArgumentException("Could not create summary: invalid help");
        }

        s = Summary.build()
                .namespace("yardstick")
                .name(key)
                .help(help)
                .register(REGISTRY);
        METRICS.put(key, s);

        LOGGER.info("Added metric: " + key);
        return s;
    }

    private static long clock() {
        return System.nanoTime();
    }

    @Override
    public void run() {
        // Push thread:
        while (true) {
            try {
                GATEWAY.pushAdd(REGISTRY, "yardstick", PushGateway.instanceIPGroupingKey());
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Could not push statistics", ex);
            }
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, "Statistics thread interrupted!", ex);
                return;
            }
        }
    }

}
