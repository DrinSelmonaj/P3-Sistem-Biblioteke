package org.example.util;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

// Test integrimi (kerkon Postgres real, si LoanServiceAuthorizationTest).
//
// PSE ky test ekziston: me versionin e vjeter, DBConnection ndante nje Connection
// te vetem — mbyllja e tij nga nje thirrje (p.sh. scheduler-i) prishte lidhjen e
// nje thirrjeje tjeter qe ishte ende ne mes te punes. Ky test e deshmon izolimin.
class DBConnectionTest {

    @Test
    void closingOneConnectionDoesNotAffectAnother() throws Exception {
        DBConnection db = DBConnection.getInstance();

        try (Connection first = db.getConnection()) {
            Connection second = db.getConnection();
            assertNotSame(first, second, "Cdo thirrje duhet te ktheje lidhje te re.");

            second.close(); // simulon nje thread tjeter qe mbaron punen e vet

            assertFalse(first.isClosed(), "Lidhja e pare s'duhet te mbyllet nga e dyta.");
            try (Statement statement = first.createStatement();
                 ResultSet result = statement.executeQuery("SELECT 1")) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
        }
    }
}
