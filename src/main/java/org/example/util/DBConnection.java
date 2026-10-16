package org.example.util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Singleton per KONFIGURIMIN (url/user/password lexohen nje here nga
 * {@code config.properties}, ose nga {@code -Ddb.url}/{@code -Ddb.user}/{@code -Ddb.password}),
 * jo per lidhjen — {@link #getConnection()} kthen nje Connection te ri ne cdo thirrje.
 *
 * <p>PSE jo nje Connection i perbashket: cdo DAO/Service e perdor lidhjen me
 * try-with-resources, pra e mbyll ne fund. Me nje objekt te vetem, nje thirrje mbyllte
 * lidhjen qe nje tjeter po e perdorte — dhe scheduler-i i rezervimeve (thread tjeter) mund
 * te bente close()/setAutoCommit(true) ne mes te nje transaksioni te LoanService ne
 * thread-in kryesor. Lidhje e vecante per cdo thirrje = cdo transaksion eshte i izoluar.
 */
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

        // System property (-Ddb.url=...) ka perparesi ndaj config.properties — keshtu
        // testet mund te xhirojne mbi nje DB te vecante (biblioteka_test) pa ndryshuar
        // skedarin e konfigurimit: mvn test -Ddb.url=jdbc:postgresql://localhost:5432/biblioteka_test
        this.url = System.getProperty("db.url", props.getProperty("db.url"));
        this.user = System.getProperty("db.user", props.getProperty("db.user"));
        this.password = System.getProperty("db.password", props.getProperty("db.password"));
    }

    /**
     * synchronized: dy thread-e (main + scheduler) s'krijojne dy instanca njekohesisht.
     *
     * @return instanca e vetme e konfigurimit
     * @throws SQLException ruhet ne nenshkrim qe thirresit ekzistues te mos ndryshojne
     */
    public static synchronized DBConnection getInstance() throws SQLException {
        if (instance == null) {
            instance = new DBConnection();
        }
        return instance;
    }

    /**
     * @return lidhje e re me databazen — thirresi e mbyll (te gjithe DAO-t me try-with-resources)
     * @throws SQLException nese lidhja deshton
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}
