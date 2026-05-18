package com.fasal.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// Holds the JDBC connection settings in one place and hands out a Connection
// whenever any service needs to talk to MySQL.
// Change the constants below if your MySQL setup is different.
public class DatabaseConnection {

    // The JDBC URL telling Java where to find the MySQL server and which database to use.
    private static final String DB_URL = "jdbc:mysql://localhost:3306/fasal_db"
        + "?useSSL=false"
        + "&allowPublicKeyRetrieval=true"
        + "&serverTimezone=UTC"
        + "&characterEncoding=UTF-8";

    // The MySQL username. "root" is the default on most local installs.
    private static final String DB_USER = "root";

    // The password matching the user above. Empty string means no password.
    private static final String DB_PASSWORD = "";

    // The fully-qualified class name of the JDBC driver to load.
    private static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";

    // Forces the JDBC driver class to load when this class is first used.
    static {
        try {
            Class.forName(JDBC_DRIVER);
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC driver not found on the classpath: " + e.getMessage());
        }
    }

    // Private constructor stops anyone from creating an instance - the class is "static" by design.
    private DatabaseConnection() { }

    // Opens a new connection to MySQL. Callers should use try-with-resources
    // so the connection is automatically closed when they are done.
    public static Connection getConnection() throws SQLException {
        try {
            return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        } catch (SQLException e) {
            System.err.println("Failed to open MySQL connection: " + e.getMessage());
            throw e;
        }
    }
}
