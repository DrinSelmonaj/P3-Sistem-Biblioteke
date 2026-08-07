package org.example.util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DBConnection {
    private static DBConnection instance;
    private Connection connection;

    private DBConnection() throws SQLException {
        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new RuntimeException("config.properties nuk u gjet ne resources/");
            }
            props.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Gabim gjate leximit te config.properties", e);
        }

        String url = props.getProperty("db.url");
        String user = props.getProperty("db.user");
        String password = props.getProperty("db.password");

        this.connection = DriverManager.getConnection(url, user, password);
    }

    public static DBConnection getInstance() throws SQLException {
        if (instance == null || instance.connection.isClosed()) {
            instance = new DBConnection();
        }
        return instance;
    }

    public Connection getConnection() {
        return connection;
    }
}