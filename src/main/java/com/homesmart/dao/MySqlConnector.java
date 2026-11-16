package com.homesmart.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Connection settings shared by the MySQL DAOs. Every call opens a new connection. */
final class MySqlConnector {

    private final String url;
    private final String user;
    private final String password;

    MySqlConnector(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
        try {
            // Tomcat does not always auto-register JDBC drivers packaged in WEB-INF/lib
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("MySQL driver is missing from the build", e);
        }
    }

    Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    static IllegalStateException databaseError(SQLException e) {
        return new IllegalStateException("Database error: " + e.getMessage(), e);
    }
}
