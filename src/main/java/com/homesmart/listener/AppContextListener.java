package com.homesmart.listener;

import com.homesmart.dao.ActivityLogDAO;
import com.homesmart.dao.DeviceDAO;
import com.homesmart.dao.InMemoryActivityLogDAO;
import com.homesmart.dao.InMemoryDeviceDAO;
import com.homesmart.dao.MySqlActivityLogDAO;
import com.homesmart.dao.MySqlDeviceDAO;
import com.homesmart.job.DoorAlertJob;
import com.homesmart.service.DeviceService;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Creates one shared DeviceService and ActivityLogDAO when the application starts,
 * so every servlet and JSP works with the same state, and runs the background jobs.
 */
@WebListener
public class AppContextListener implements ServletContextListener {

    public static final String DEVICE_SERVICE = "deviceService";
    public static final String ACTIVITY_LOG = "activityLog";

    /** Formats activity timestamps in the JSPs, e.g. "Oct 23, 2026 at 10:37 AM". */
    public static final String TIME_FORMAT = "timeFormat";

    /** Set to true when the app could not reach MySQL and is running on sample data. */
    public static final String USING_SAMPLE_DATA = "usingSampleData";

    /** How long a door stays unlocked before it raises an alert, as a {@link Duration}. */
    public static final String DOOR_ALERT_THRESHOLD = "doorAlertThreshold";

    private static final String DB_CONFIG = "db.properties";

    // Each can be set as a context-param in web.xml, and a JVM system property of the same name wins
    private static final String DOOR_ALERT_MINUTES = "homesmart.doorAlertMinutes";
    private static final String JOB_DELAY_SECONDS = "homesmart.jobDelaySeconds";
    private static final int DEFAULT_DOOR_ALERT_MINUTES = 10;
    private static final int DEFAULT_JOB_DELAY_SECONDS = 30;
    private static final int JOB_START_DELAY_SECONDS = 5;

    private ScheduledExecutorService jobs;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        Properties config = loadDbConfig();
        DeviceDAO deviceDAO = createDeviceDAO(context, config);
        ActivityLogDAO activityLog = deviceDAO instanceof MySqlDeviceDAO
                ? createActivityLogDAO(context, config) : new InMemoryActivityLogDAO();
        context.setAttribute(DEVICE_SERVICE, new DeviceService(deviceDAO, activityLog));
        context.setAttribute(ACTIVITY_LOG, activityLog);
        context.setAttribute(TIME_FORMAT, DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a", Locale.ENGLISH));

        Duration doorAlertThreshold = Duration.ofMinutes(positiveSetting(context, DOOR_ALERT_MINUTES, DEFAULT_DOOR_ALERT_MINUTES));
        context.setAttribute(DOOR_ALERT_THRESHOLD, doorAlertThreshold);
        startJobs(context, new DoorAlertJob((DeviceService) context.getAttribute(DEVICE_SERVICE), activityLog,
                Clock.systemDefaultZone(), doorAlertThreshold));
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (jobs == null) {
            return;
        }
        jobs.shutdownNow();
        try {
            if (!jobs.awaitTermination(15, TimeUnit.SECONDS)) {
                log(event.getServletContext(), "WARNING: background jobs did not stop within 15 seconds.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Runs every job on 1 daemon thread. A job catches its own errors, so 1 failure never stops later runs. */
    private void startJobs(ServletContext context, Runnable... tasks) {
        int delaySeconds = positiveSetting(context, JOB_DELAY_SECONDS, DEFAULT_JOB_DELAY_SECONDS);
        jobs = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "homesmart-jobs");
            thread.setDaemon(true);
            return thread;
        });
        for (Runnable task : tasks) {
            jobs.scheduleWithFixedDelay(task, JOB_START_DELAY_SECONDS, delaySeconds, TimeUnit.SECONDS);
        }
    }

    private static int positiveSetting(ServletContext context, String name, int fallback) {
        String value = System.getProperty(name, context.getInitParameter(name));
        if (value == null) {
            return fallback;
        }
        try {
            int number = Integer.parseInt(value.trim());
            if (number > 0) {
                return number;
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        log(context, "WARNING: " + name + " must be a whole number above 0, not \"" + value + "\". Using " + fallback + ".");
        return fallback;
    }

    /**
     * Uses MySQL when db.properties is present and the database is reachable.
     * Otherwise falls back to in-memory sample data, so the app still runs without a database.
     */
    private static DeviceDAO createDeviceDAO(ServletContext context, Properties config) {
        if (config == null) {
            return useSampleData(context, DB_CONFIG + " not found");
        }

        String url = config.getProperty("db.url");
        MySqlDeviceDAO mySqlDAO = new MySqlDeviceDAO(
                url, config.getProperty("db.user"), config.getProperty("db.password", ""));
        try {
            mySqlDAO.checkConnection();
        } catch (IllegalStateException e) {
            return useSampleData(context, e.getMessage());
        }
        log(context, "Connected to MySQL at " + url);
        return mySqlDAO;
    }

    /** Uses the MySQL {@code activity_log} table, or an in-memory log when the table is missing. */
    private static ActivityLogDAO createActivityLogDAO(ServletContext context, Properties config) {
        MySqlActivityLogDAO mySqlDAO = new MySqlActivityLogDAO(
                config.getProperty("db.url"), config.getProperty("db.user"), config.getProperty("db.password", ""));
        try {
            mySqlDAO.checkTable();
        } catch (IllegalStateException e) {
            log(context, "WARNING: activity_log table not available (" + e.getMessage()
                    + "). Import src/homesmart.sql to create it. Using an in-memory activity log; history will not be saved.");
            return new InMemoryActivityLogDAO();
        }
        return mySqlDAO;
    }

    private static DeviceDAO useSampleData(ServletContext context, String reason) {
        log(context, "WARNING: " + reason + ". Using in-memory sample data; changes will not be saved.");
        context.setAttribute(USING_SAMPLE_DATA, true);
        return new InMemoryDeviceDAO();
    }

    private static Properties loadDbConfig() {
        try (InputStream in = AppContextListener.class.getClassLoader().getResourceAsStream(DB_CONFIG)) {
            if (in == null) {
                return null;
            }
            Properties config = new Properties();
            config.load(in);
            return config;
        } catch (IOException e) {
            return null;
        }
    }

    private static void log(ServletContext context, String message) {
        context.log("[HomeSmart] " + message);
        System.out.println("[HomeSmart] " + message);
    }
}
