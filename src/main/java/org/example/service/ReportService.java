package org.example.service;

import org.example.model.Person;
import org.example.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Raportet administrative per bibliotekarin. Vetem lexim — asnje metode s'ndryshon te
 * dhena, keshtu qe s'ka nevoje per transaksione si te LoanService/FineService.
 * Autorizim: vetem Librarian, njesoj si {@link FineService#getAllPayments} — te dhena te
 * gjithe sistemit, jo personale te nje anetari.
 */
public class ReportService {

    /**
     * @param activeLoans         huazimet e pakthyera
     * @param overdueLoans        huazimet e pakthyera me afat te kaluar
     * @param totalUnpaidFines    shuma e gjobave te papaguara te te gjithe anetareve
     * @param pendingReservations rezervimet aktive (ne prite ose gati per marrje)
     */
    public record SystemSummary(int activeLoans, int overdueLoans, double totalUnpaidFines, int pendingReservations) {}
    /**
     * @param itemId        ID-ja e artikullit
     * @param title         titulli
     * @param timesBorrowed sa here eshte huazuar gjithsej
     */
    public record ItemBorrowCount(String itemId, String title, int timesBorrowed) {}
    /**
     * @param memberId    username-i i anetarit
     * @param name        emri
     * @param unpaidFines bilanci i papaguar
     */
    public record MemberDebt(String memberId, String name, double unpaidFines) {}

    /**
     * Permbledhja e gjendjes se sistemit, me nje pyetje te vetme ne DB.
     *
     * @param actor duhet te jete bibliotekar
     * @return numrat aktuale te huazimeve, gjobave dhe rezervimeve
     * @throws SecurityException nese actor s'eshte bibliotekar
     */
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

    /**
     * Artikujt me te huazuar gjithe kohes (huazimet historike, jo vetem aktive).
     *
     * @param actor duhet te jete bibliotekar
     * @param limit sa artikuj te kthehen
     * @return artikujt, me te huazuarin ne fillim
     * @throws SecurityException nese actor s'eshte bibliotekar
     */
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

    /**
     * Anetaret me bilanc te papaguar.
     *
     * @param actor duhet te jete bibliotekar
     * @return anetaret, me borxhin me te madh ne fillim
     * @throws SecurityException nese actor s'eshte bibliotekar
     */
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