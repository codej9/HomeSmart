package com.homesmart.listener;

import com.homesmart.dao.ActivityLogDAO;
import com.homesmart.dao.DeviceDAO;
import com.homesmart.dao.InMemoryActivityLogDAO;
import com.homesmart.dao.InMemoryDeviceDAO;
import com.homesmart.dao.InMemoryScheduleDAO;
import com.homesmart.dao.InMemoryUserDAO;
import com.homesmart.dao.MySqlActivityLogDAO;
import com.homesmart.dao.MySqlDeviceDAO;
import com.homesmart.dao.MySqlScheduleDAO;
import com.homesmart.dao.ScheduleDAO;
import com.homesmart.job.DoorAlertJob;
import com.homesmart.job.ScheduleJob;
import com.homesmart.dao.MySqlUserDAO;
import com.homesmart.dao.UserDAO;
import com.homesmart.service.DeviceService;
import com.homesmart.service.ScheduleService;
import com.homesmart.service.LoginThrottle;
import com.homesmart.service.SetupService;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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

    /** Missing when the {@code schedules} table is not available, so the schedule views show the re-import text. */
    public static final String SCHEDULE_SERVICE = "scheduleService";

    /** Formats activity timestamps in the JSPs, e.g. "Oct 23, 2026 at 10:37 AM". */
    public static final String TIME_FORMAT = "timeFormat";

    /** Set to true when the app could not reach MySQL and is running on sample data. */
    public static final String USING_SAMPLE_DATA = "usingSampleData";

    /** How long a door stays unlocked before it raises an alert, as a {@link Duration}. */
    public static final String DOOR_ALERT_THRESHOLD = "doorAlertThreshold";

    public static final String USER_DAO = "userDAO";
    public static final String SETUP_SERVICE = "setupService";
    public static final String LOGIN_THROTTLE = "loginThrottle";

    /** Set to true when MySQL is in use but the {@code users} table is missing. */
    public static final String USERS_TABLE_MISSING = "usersTableMissing";

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

        initAccounts(context, config, deviceDAO instanceof MySqlDeviceDAO);

        Duration doorAlertThreshold = Duration.ofMinutes(positiveSetting(context, DOOR_ALERT_MINUTES, DEFAULT_DOOR_ALERT_MINUTES));
        context.setAttribute(DOOR_ALERT_THRESHOLD, doorAlertThreshold);
        DeviceService deviceService = (DeviceService) context.getAttribute(DEVICE_SERVICE);
        List<Runnable> tasks = new ArrayList<>();
        tasks.add(new DoorAlertJob(deviceService, activityLog, Clock.systemDefaultZone(), doorAlertThreshold));
        ScheduleService scheduleService = createScheduleService(context, config, deviceService, deviceDAO instanceof MySqlDeviceDAO);
        if (scheduleService != null) {
            context.setAttribute(SCHEDULE_SERVICE, scheduleService);
            tasks.add(new ScheduleJob(scheduleService));
        }
        startJobs(context, tasks);
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
    private void startJobs(ServletContext context, List<Runnable> tasks) {
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
     * Sets up accounts: MySQL when the app uses it, otherwise in memory. A missing {@code users} table
     * is logged and flagged, and no setup code is made, because nobody could sign in.
     */
    private static void initAccounts(ServletContext context, Properties config, boolean useMySql) {
        UserDAO userDAO = useMySql ? createUserDAO(context, config) : new InMemoryUserDAO();
        context.setAttribute(USER_DAO, userDAO);
        context.setAttribute(LOGIN_THROTTLE, new LoginThrottle(Clock.systemUTC()));
        String setupCode = null;
        if (!Boolean.TRUE.equals(context.getAttribute(USERS_TABLE_MISSING)) && userDAO.count() == 0) {
            setupCode = SetupService.newCode();
            log(context, "Setup code: " + setupCode);
        }
        context.setAttribute(SETUP_SERVICE, new SetupService(userDAO, setupCode));
    }

    private static UserDAO createUserDAO(ServletContext context, Properties config) {
        MySqlUserDAO mySqlDAO = new MySqlUserDAO(
                config.getProperty("db.url"), config.getProperty("db.user"), config.getProperty("db.password", ""));
        try {
            mySqlDAO.checkTable();
        } catch (IllegalStateException e) {
            log(context, "SEVERE: users table not available (" + e.getMessage()
                    + "). Import src/homesmart.sql to create it. Sign-in is disabled until then.");
            context.setAttribute(USERS_TABLE_MISSING, true);
        }
        return mySqlDAO;
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

    /**
     * Sample mode keeps schedules in memory. With MySQL, a missing or old {@code schedules} table gives null:
     * there is no fallback, so the schedule views tell the user to re-import the schema and no schedule runs.
     */
    private static ScheduleService createScheduleService(ServletContext context, Properties config,
                                                         DeviceService deviceService, boolean mySql) {
        ScheduleDAO scheduleDAO = new InMemoryScheduleDAO();
        if (mySql) {
            MySqlScheduleDAO mySqlDAO = new MySqlScheduleDAO(
                    config.getProperty("db.url"), config.getProperty("db.user"), config.getProperty("db.password", ""));
            try {
                mySqlDAO.checkTable();
            } catch (IllegalStateException e) {
                log(context, "SEVERE: schedules table not available (" + e.getMessage()
                        + "). Import src/homesmart.sql to create it. Schedules will not run.");
                return null;
            }
            scheduleDAO = mySqlDAO;
        }
        return new ScheduleService(scheduleDAO, deviceService, Clock.systemDefaultZone());
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
