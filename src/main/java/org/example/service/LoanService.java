package org.example.service;

import org.example.dao.BookDAO;
import org.example.dao.DVDDAO;
import org.example.dao.MemberDAO;
import org.example.model.Fine;
import org.example.model.LibraryItem;
import org.example.model.Loan;
import org.example.model.Member;
import org.example.model.Person;
import org.example.util.DBConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Huazimi dhe kthimi i artikujve. Cdo veprim eshte nje transaksion i vetem me
 * {@code SELECT ... FOR UPDATE}, qe dy kerkesa njekohesisht te mos e huazojne te njejtin
 * artikull dhe radha e rezervimeve te mbetet e sakte.
 */
public class LoanService {

    private final MemberDAO memberDAO;
    private final BookDAO bookDAO;
    private final DVDDAO dvdDAO;

    /**
     * @param memberDAO per te ngarkuar anetarin
     * @param bookDAO   per te gjetur artikullin si liber
     * @param dvdDAO    per te gjetur artikullin si DVD
     */
    public LoanService(MemberDAO memberDAO, BookDAO bookDAO, DVDDAO dvdDAO) {
        this.memberDAO = memberDAO;
        this.bookDAO = bookDAO;
        this.dvdDAO = dvdDAO;
    }

    /**
     * Regjistron huazim. Lejohet kur artikulli eshte i lire, ose kur eshte "gati per marrje"
     * per pikerisht kete anetar (rezervimi i tij behet i permbushur).
     *
     * @param actor    anetari vete, ose bibliotekar qe huazon ne emer te anetarit
     * @param memberId anetari qe merr artikullin
     * @param itemId   artikulli
     * @return huazimi i ruajtur, me afatin sipas tipit (21 dite liber, 7 dite DVD)
     * @throws SecurityException        nese anetari huazon per dike tjeter
     * @throws IllegalArgumentException nese anetari ose artikulli s'ekziston
     * @throws IllegalStateException    nese artikulli s'eshte i disponueshem, eshte rezervuar per
     *                                  dike tjeter, anetari ka gjoba te papaguara ose ka arritur
     *                                  kufirin e huazimeve aktive
     */
    public Loan borrowItem(Person actor, String memberId, String itemId) {
        if (!actor.canManageInventory() && !actor.getId().equals(memberId)) {
            throw new SecurityException("Mund te huazosh vetem per vete.");
        }

        Member member = memberDAO.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Anetari me ID " + memberId + " nuk u gjet."));
        LibraryItem item = resolveItem(itemId);
        LocalDate today = LocalDate.now();

        try (Connection connection = DBConnection.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean available = lockAndReadAvailability(connection, itemId);
                Integer readyReservationId = lockReadyReservation(connection, itemId, memberId);
                validateMemberCanBorrow(connection, memberId);

                if (!available && readyReservationId == null) {
                    throw new IllegalStateException("Artikulli " + itemId + " s'eshte i disponueshem per huazim — mund ta rezervosh.");
                }

                Loan loan = new Loan(member, item, today);
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO loans (member_id, item_id, borrow_date, due_date) VALUES (?, ?, ?, ?) RETURNING id")) {
                    statement.setString(1, memberId);
                    statement.setString(2, itemId);
                    statement.setDate(3, Date.valueOf(today));
                    statement.setDate(4, Date.valueOf(loan.getDueDate()));
                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        loan.setId(result.getInt("id"));
                    }
                }

                updateAvailability(connection, itemId, false);

                if (readyReservationId != null) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE reservations SET fulfilled = true, ready_for_pickup = false WHERE id = ?")) {
                        statement.setInt(1, readyReservationId);
                        statement.executeUpdate();
                    }
                }

                connection.commit();
                item.setAvailable(false);
                return loan;

            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate regjistrimit te huazimit", e);
        }
    }

    /**
     * Regjistron kthimin. Nese ka radhe rezervimesh, artikulli mbahet per te parin ne radhe
     * ("gati per marrje"); perndryshe behet i lire. Kthimi me vonese krijon automatikisht
     * gjobe ne te njejtin transaksion.
     *
     * @param actor  anetari qe kthen huazimin e vet, ose bibliotekar
     * @param loanId ID-ja e huazimit
     * @return shuma e gjobes se krijuar automatikisht (0.0 nese s'ka vonese)
     * @throws SecurityException        nese anetari kthen huazimin e dikujt tjeter
     * @throws IllegalArgumentException nese huazimi s'ekziston
     * @throws IllegalStateException    nese huazimi eshte kthyer tashme
     */
    public double returnItem(Person actor, int loanId) {
        try (Connection connection = DBConnection.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            try {
                ActiveLoanInfo loanInfo = lockActiveLoanAndGetItem(connection, loanId);

                if (!actor.canManageInventory() && !actor.getId().equals(loanInfo.memberId())) {
                    throw new SecurityException("Mund te kthesh vetem huazimet e tua.");
                }

                String itemId = loanInfo.itemId();
                LocalDate returnDate = LocalDate.now();

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE loans SET return_date = ? WHERE id = ?")) {
                    statement.setDate(1, Date.valueOf(returnDate));
                    statement.setInt(2, loanId);
                    statement.executeUpdate();
                }

                double fineAmount = issueOverdueFine(connection, loanId, loanInfo, returnDate);

                Integer nextReservation = lockNextWaitingReservation(connection, itemId);

                if (nextReservation == null) {
                    updateAvailability(connection, itemId, true);
                } else {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE reservations SET ready_for_pickup = true, ready_since = NOW() WHERE id = ?")) {
                        statement.setInt(1, nextReservation);
                        statement.executeUpdate();
                    }
                    updateAvailability(connection, itemId, false);
                }

                connection.commit();
                return fineAmount;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate kthimit te huazimit " + loanId, e);
        }
    }

    // Gjoba automatike per kthim te vonuar — brenda te njejtit transaksion si kthimi,
    // qe s'mund te ndodhe "kthim pa gjobe" nese dicka deshton ne mes.
    //
    // PSE zbritet gjoba ekzistuese: bibliotekari mund ta kete gjobitur huazimin
    // manualisht ndersa ishte ende aktiv. Ngarkohet vetem diferenca deri ne diten
    // e kthimit — anetari s'paguan dy here per te njejtat dite vonese.
    private double issueOverdueFine(Connection connection, int loanId, ActiveLoanInfo loanInfo,
                                    LocalDate returnDate) throws SQLException {
        long daysOverdue = ChronoUnit.DAYS.between(loanInfo.dueDate(), returnDate);
        double totalDue = Fine.amountForDaysOverdue(daysOverdue);
        if (totalDue <= 0) return 0.0;

        double alreadyFined;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COALESCE(SUM(amount), 0) FROM fines WHERE loan_id = ?")) {
            statement.setInt(1, loanId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                alreadyFined = result.getDouble(1);
            }
        }

        double amount = totalDue - alreadyFined;
        if (amount <= 0) return 0.0;

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO fines (loan_id, amount, issued_date) VALUES (?, ?, ?)")) {
            statement.setInt(1, loanId);
            statement.setDouble(2, amount);
            statement.setDate(3, Date.valueOf(returnDate));
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE members SET unpaid_fines = unpaid_fines + ? WHERE person_id = ?")) {
            statement.setDouble(1, amount);
            statement.setString(2, loanInfo.memberId());
            statement.executeUpdate();
        }
        return amount;
    }

    private boolean lockAndReadAvailability(Connection connection, String itemId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT available FROM library_items WHERE id = ? FOR UPDATE")) {
            statement.setString(1, itemId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalArgumentException("Artikulli me ID " + itemId + " nuk u gjet.");
                return result.getBoolean("available");
            }
        }
    }

    private Integer lockReadyReservation(Connection connection, String itemId, String memberId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, member_id FROM reservations WHERE item_id = ? AND fulfilled = false " +
                        "AND ready_for_pickup = true FOR UPDATE")) {
            statement.setString(1, itemId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                if (!memberId.equals(result.getString("member_id"))) {
                    throw new IllegalStateException("Artikulli eshte rezervuar per nje anetar tjeter.");
                }
                return result.getInt("id");
            }
        }
    }

    private void validateMemberCanBorrow(Connection connection, String memberId) throws SQLException {
        try (PreparedStatement memberStatement = connection.prepareStatement(
                "SELECT unpaid_fines FROM members WHERE person_id = ? FOR UPDATE");
             PreparedStatement countStatement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM loans WHERE member_id = ? AND return_date IS NULL")) {
            memberStatement.setString(1, memberId);
            try (ResultSet memberResult = memberStatement.executeQuery()) {
                if (!memberResult.next()) throw new IllegalArgumentException("Anetari me ID " + memberId + " nuk u gjet.");
                if (memberResult.getDouble("unpaid_fines") > 0) throw new IllegalStateException("Anetari ka gjoba te papaguara.");
            }
            countStatement.setString(1, memberId);
            try (ResultSet countResult = countStatement.executeQuery()) {
                countResult.next();
                if (countResult.getInt(1) >= Member.getMaxLoans()) throw new IllegalStateException("Anetari ka arritur kufirin e huazimeve aktive.");
            }
        }
    }

    private ActiveLoanInfo lockActiveLoanAndGetItem(Connection connection, int loanId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT item_id, member_id, due_date, return_date FROM loans WHERE id = ? FOR UPDATE")) {
            statement.setInt(1, loanId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalArgumentException("Huazimi me ID " + loanId + " nuk u gjet.");
                if (result.getDate("return_date") != null) throw new IllegalStateException("Huazimi eshte kthyer tashme.");
                return new ActiveLoanInfo(result.getString("item_id"), result.getString("member_id"),
                        result.getDate("due_date").toLocalDate());
            }
        }
    }

    private Integer lockNextWaitingReservation(Connection connection, String itemId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM reservations WHERE item_id = ? AND fulfilled = false AND ready_for_pickup = false " +
                        "AND cancelled = false ORDER BY reservation_date, id LIMIT 1 FOR UPDATE")) {
            statement.setString(1, itemId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt("id") : null;
            }
        }
    }

    private void updateAvailability(Connection connection, String itemId, boolean available) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE library_items SET available = ? WHERE id = ?")) {
            statement.setBoolean(1, available);
            statement.setString(2, itemId);
            statement.executeUpdate();
        }
    }

    private LibraryItem resolveItem(String itemId) {
        return bookDAO.findById(itemId).map(item -> (LibraryItem) item)
                .or(() -> dvdDAO.findById(itemId).map(item -> (LibraryItem) item))
                .orElseThrow(() -> new IllegalArgumentException("Artikulli me ID " + itemId + " nuk u gjet."));
    }

    private record ActiveLoanInfo(String itemId, String memberId, LocalDate dueDate) {}
}