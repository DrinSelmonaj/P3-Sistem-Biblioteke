package org.example;

import org.example.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

// Krijon te dhena prove me prefiks "TEST_" dhe i fshin te gjitha ne cleanup().
//
// PSE: testet e integrimit xhirojne mbi DB-ne reale te config.properties. Pa
// pastrim, cdo ekzekutim do te linte rezervime/huazime/gjoba te reja ne DB
// (si historiku qe rritet te M001). Ketu cdo rresht qe krijohet regjistrohet,
// dhe cleanup() i fshin ne rendin e kundert te foreign key-ve.
public class TestData implements AutoCloseable {

    private final List<String> personIds = new ArrayList<>();
    private final List<String> itemIds = new ArrayList<>();
    private int counter = 0;

    // ID unike per cdo ekzekutim — dy teste paralele ose nje ekzekutim i
    // nderprere me pare s'perplasen me njeri-tjetrin.
    private final String runId = Long.toString(System.nanoTime(), 36);

    public String createMember() throws SQLException {
        String id = "TEST_M_" + runId + "_" + (counter++);
        execute("INSERT INTO persons (id, name, email, phone, person_type, password_hash) VALUES (?, ?, ?, NULL, 'MEMBER', 'test-placeholder')",
                id, "Anetar Test " + id, id + "@test.local");
        execute("INSERT INTO members (person_id) VALUES (?)", id);
        personIds.add(id);
        return id;
    }

    public String createBook(boolean available) throws SQLException {
        String id = "TEST_B_" + runId + "_" + (counter++);
        try (Connection connection = DBConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO library_items (id, title, category, available, item_type) VALUES (?, ?, 'SCIENCE', ?, 'BOOK')")) {
            statement.setString(1, id);
            statement.setString(2, "Liber Test " + id);
            statement.setBoolean(3, available);
            statement.executeUpdate();
        }
        execute("INSERT INTO books (item_id, author, isbn) VALUES (?, 'Autor Test', ?)", id, id);
        itemIds.add(id);
        return id;
    }

    public void setUnpaidFines(String memberId, double amount) throws SQLException {
        try (Connection connection = DBConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE members SET unpaid_fines = ? WHERE person_id = ?")) {
            statement.setDouble(1, amount);
            statement.setString(2, memberId);
            statement.executeUpdate();
        }
    }

    // Fshin gjithcka qe lidhet me personat/artikujt e krijuar — edhe rreshtat
    // qe i krijuan vete service-t gjate testit (rezervime, huazime, gjoba).
    @Override
    public void close() throws SQLException {
        for (String itemId : itemIds) {
            execute("DELETE FROM reservations WHERE item_id = ?", itemId);
            execute("DELETE FROM fines WHERE loan_id IN (SELECT id FROM loans WHERE item_id = ?)", itemId);
            execute("DELETE FROM loans WHERE item_id = ?", itemId);
        }
        for (String personId : personIds) {
            execute("DELETE FROM reservations WHERE member_id = ?", personId);
            execute("DELETE FROM fines WHERE loan_id IN (SELECT id FROM loans WHERE member_id = ?)", personId);
            execute("DELETE FROM loans WHERE member_id = ?", personId);
            execute("DELETE FROM persons WHERE id = ?", personId); // ON DELETE CASCADE → members/librarians
        }
        for (String itemId : itemIds) {
            execute("DELETE FROM library_items WHERE id = ?", itemId); // ON DELETE CASCADE → books/dvds
        }
    }

    private void execute(String sql, String... params) throws SQLException {
        try (Connection connection = DBConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setString(i + 1, params[i]);
            }
            statement.executeUpdate();
        }
    }
}
