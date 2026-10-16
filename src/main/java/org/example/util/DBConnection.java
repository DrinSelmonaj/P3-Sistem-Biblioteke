package org.example.util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

// Singleton per KONFIGURIMIN (url/user/password lexohen nje here nga
// config.properties), jo per lidhjen. getConnection() kthen nje Connection te
// ri ne cdo thirrje.
//
// PSE jo nje Connection i perbashket: çdo DAO/Service e perdor lidhjen me
// try-with-resources, pra e mbyll ne fund. Me nje objekt te vetem, nje thirrje
// mbyllte lidhjen qe nje tjeter po e perdorte — dhe scheduler-i i rezervimeve
// (thread tjeter) mund te bente close()/setAutoCommit(true) ne mes te nje
// transaksioni te LoanService ne thread-in kryesor. Lidhje e vecante per cdo
// thirrje = çdo transaksion eshte i izoluar.
public class DBConnection {
    // volatile + synchronized ne getInstance(): garanton qe dy thread-e
    // (main + scheduler) s'krijojne dy instanca njekohesisht.
    private static volatile DBConnection instance;

    private final String url;
    private final String user;
    private final String password;

    private DBConnection() {
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

        this.url = props.getProperty("db.url");
        this.user = props.getProperty("db.user");
        this.password = props.getProperty("db.password");
    }

    // Mban "throws SQLException" qe thirresit ekzistues te mos ndryshojne.
    public static synchronized DBConnection getInstance() throws SQLException {
        if (instance == null) {
            instance = new DBConnection();
        }
        return instance;
    }

    // Lidhje e re cdo here — thirresi eshte pergjegjes ta mbylle
    // (te gjithe DAO-t e bejne kete me try-with-resources).
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}
