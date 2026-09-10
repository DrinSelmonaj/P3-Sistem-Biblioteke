package org.example.service;

import org.example.model.Person;
import org.example.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

// Vetem lexim — asnje metode ketu s'ndryshon te dhena, keshtu qe s'ka nevoje per
// transaksione (setAutoCommit/commit) si te LoanService/FineService/ReservationService.
// Autorizim: vetem Librarian, njesoj si FineService.getAllPayments() — te dhena
// administrative te gjithe sistemit, jo personale te nje anetari.
public class ReportService {

    public record SystemSummary(int activeLoans, int overdueLoans, double totalUnpaidFines, int pendingReservations) {}
    public record ItemBorrowCount(String itemId, String title, int timesBorrowed) {}
    public record MemberDebt(String memberId, String name, double unpaidFines) {}

    public SystemSummary getSystemSummary(Person actor) {
        requireLibrarian(actor);

        // Nje SELECT i vetem me nen-pyetje (subqueries) ne vend te 4 pyetjeve te
        // veçanta — 1 roundtrip ne DB ne vend te 4, per nje raport qe kombinon
        // agregate nga tabela te ndryshme qe s'lidhen direkt me JOIN.
        String sql =
                "SELECT " +
                        "(SELECT COUNT(*) FROM loans WHERE return_date IS NULL) AS active_loans, " +
                        "(SELECT COUNT(*) FROM loans WHERE return_date IS NULL AND due_date < CURRENT_DATE) AS overdue_loans, " +
                        "(SELECT COALESCE(SUM(unpaid_fines), 0) FROM members) AS total_unpaid_fines, " +
                        "(SELECT COUNT(*) FROM reservations WHERE fulfilled = false AND cancelled = false) AS pending_reservations";

        try (Connection connection = DBConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return new SystemSummary(
                    result.getInt("active_loans"),
                    result.getInt("overdue_loans"),
                    result.getDouble("total_unpaid_fines"),
                    result.getInt("pending_reservations"));
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate gjenerimit te permbledhjes", e);
        }
    }

    public List<ItemBorrowCount> getMostBorrowedItems(Person actor, int limit) {
        requireLibrarian(actor);

        // COUNT(l.id) numeron çdo huazim historik (jo vetem aktiv) — "me te
        // kerkuarit" duhet te pasqyroje popullaritetin gjithe kohes, jo vetem tani.
        String sql = "SELECT li.id, li.title, COUNT(l.id) AS times_borrowed " +
                "FROM library_items li JOIN loans l ON l.item_id = li.id " +
                "GROUP BY li.id, li.title " +
                "ORDER BY times_borrowed DESC " +
                "LIMIT ?";

        List<ItemBorrowCount> results = new ArrayList<>();
        try (Connection connection = DBConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    results.add(new ItemBorrowCount(
                            result.getString("id"),
                            result.getString("title"),
                            result.getInt("times_borrowed")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate gjenerimit te raportit te artikujve me te huazuar", e);
        }
        return results;
    }

    public List<MemberDebt> getMembersWithUnpaidFines(Person actor) {
        requireLibrarian(actor);

        String sql = "SELECT m.person_id, p.name, m.unpaid_fines " +
                "FROM members m JOIN persons p ON p.id = m.person_id " +
                "WHERE m.unpaid_fines > 0 " +
                "ORDER BY m.unpaid_fines DESC";

        List<MemberDebt> results = new ArrayList<>();
        try (Connection connection = DBConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                results.add(new MemberDebt(
                        result.getString("person_id"),
                        result.getString("name"),
                        result.getDouble("unpaid_fines")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate gjenerimit te raportit te anetareve me gjoba", e);
        }
        return results;
    }

    private void requireLibrarian(Person actor) {
        if (!actor.canManageInventory()) {
            throw new SecurityException("Vetem librarianet mund te shikojne raportet.");
        }
    }
}