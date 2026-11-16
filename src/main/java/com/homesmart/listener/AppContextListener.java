package com.homesmart.listener;

import com.homesmart.dao.ActivityLogDAO;
import com.homesmart.dao.DeviceDAO;
import com.homesmart.dao.InMemoryActivityLogDAO;
import com.homesmart.dao.InMemoryDeviceDAO;
import com.homesmart.dao.MySqlActivityLogDAO;
import com.homesmart.dao.MySqlDeviceDAO;
import com.homesmart.service.DeviceService;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Properties;

/**
 * Creates one shared DeviceService and ActivityLogDAO when the application starts,
 * so every servlet and JSP works with the same state.
 */
@WebListener
public class AppContextListener implements ServletContextListener {

    public static final String DEVICE_SERVICE = "deviceService";
    public static final String ACTIVITY_LOG = "activityLog";

    /** Formats activity timestamps in the JSPs, e.g. "Oct 23, 2026 at 10:37 AM". */
    public static final String TIME_FORMAT = "timeFormat";

    /** Set to true when the app could not reach MySQL and is running on sample data. */
    public static final String USING_SAMPLE_DATA = "usingSampleData";

    private static final String DB_CONFIG = "db.properties";

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        Properties config = loadDbConfig();
        DeviceDAO deviceDAO = createDeviceDAO(context, config);
        context.setAttribute(DEVICE_SERVICE, new DeviceService(deviceDAO));
        context.setAttribute(ACTIVITY_LOG, deviceDAO instanceof MySqlDeviceDAO
                ? createActivityLogDAO(context, config) : new InMemoryActivityLogDAO());
        context.setAttribute(TIME_FORMAT, DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a", Locale.ENGLISH));
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
