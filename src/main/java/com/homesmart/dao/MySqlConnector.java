package com.homesmart.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Connection settings shared by the MySQL DAOs. Every call opens a new connection. */
final class MySqlConnector {

    private final String url;
    private final Properties properties = new Properties();

    MySqlConnector(String url, String user, String password) {
        this.url = url;
        if (user != null) {
            properties.setProperty("user", user);
        }
        properties.setProperty("password", password);
        // A stopped database must not hold a request, or the service lock, for long
        properties.setProperty("connectTimeout", "5000");
        properties.setProperty("socketTimeout", "10000");
        try {
            // Tomcat does not always auto-register JDBC drivers packaged in WEB-INF/lib
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("MySQL driver is missing from the build", e);
        }
    }

    Connection open() throws SQLException {
        return DriverManager.getConnection(url, properties);
    }

    static IllegalStateException databaseError(SQLException e) {
        return new IllegalStateException("Database error: " + e.getMessage(), e);
    }
}
