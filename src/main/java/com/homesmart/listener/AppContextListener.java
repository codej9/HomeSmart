package com.homesmart.listener;

import com.homesmart.dao.DeviceDAO;
import com.homesmart.dao.InMemoryDeviceDAO;
import com.homesmart.dao.MySqlDeviceDAO;
import com.homesmart.service.DeviceService;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Creates one shared DeviceService when the application starts,
 * so every servlet and JSP works with the same device state.
 */
@WebListener
public class AppContextListener implements ServletContextListener {

    public static final String DEVICE_SERVICE = "deviceService";

    /** Set to true when the app could not reach MySQL and is running on sample data. */
    public static final String USING_SAMPLE_DATA = "usingSampleData";

    private static final String DB_CONFIG = "db.properties";

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        context.setAttribute(DEVICE_SERVICE, new DeviceService(createDeviceDAO(context)));
    }

    /**
     * Uses MySQL when db.properties is present and the database is reachable.
     * Otherwise falls back to in-memory sample data, so the app still runs without a database.
     */
    private static DeviceDAO createDeviceDAO(ServletContext context) {
        Properties config = loadDbConfig();
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
