package org.example.service;

import org.example.dao.BookDAO;
import org.example.dao.DVDDAO;
import org.example.dao.MemberDAO;
import org.example.model.LibraryItem;
import org.example.model.Loan;
import org.example.model.Member;
import org.example.util.DBConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

// Koordinon ndryshimet e gjendjes se huazimit brenda 1 transaksioni DB te vetem.
//
// RISHKRIM (nga versioni fillestar me DAO-DAO te veçanta): borrowItem()/returnItem()
// perdorin tani SQL direkt brenda nje Connection te perbashket, me SELECT ... FOR UPDATE
// per te bllokuar rreshtat perkates (library_items, reservations, members) sa kohe
// zgjat transaksioni. Kjo eliminon rrezikun e gjendjes se pjesshme qe kishim shenuar
// si TODO ne versionin e meparshem (p.sh. loan i ruajtur, por item ende "available").
//
// Shenim: kjo klase QELLIMISHT s'perdor LoanDAO/ReservationDAO — te gjitha query-te
// jane brenda te njejtit transaksion, dhe DAO-t ekzistuese hapin Connection te vetin
// (do te thyente atomicitetin). memberDAO/bookDAO/dvdDAO perdoren vetem per lexime
// jashte transaksionit (rezolvim Member/LibraryItem per objektin Loan te kthyer).
public class LoanService {

    private final MemberDAO memberDAO;
    private final BookDAO bookDAO;
    private final DVDDAO dvdDAO;

    public LoanService(MemberDAO memberDAO, BookDAO bookDAO, DVDDAO dvdDAO) {
        this.memberDAO = memberDAO;
        this.bookDAO = bookDAO;
        this.dvdDAO = dvdDAO;
    }

    public Loan borrowItem(String memberId, String itemId) {
        Member member = memberDAO.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Anetari me ID " + memberId + " nuk u gjet."));
        LibraryItem item = resolveItem(itemId);
        LocalDate today = LocalDate.now();

        try (Connection connection = DBConnection.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            try {
                // FOR UPDATE bllokon rreshtin sa kohe zgjat transaksioni — nese 2 anetare
                // provojne te huazojne te njejtin artikull njekohesisht, i dyti pret
                // derisa i pari te commit/rollback-oje, duke shmangur race condition.
                boolean available = lockAndReadAvailability(connection, itemId);
                Integer readyReservationId = lockReadyReservation(connection, itemId, memberId);
                validateMemberCanBorrow(connection, memberId);

                if (!available && readyReservationId == null) {
                    throw new IllegalStateException("Artikulli " + itemId + " s'eshte i disponueshem per huazim.");
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
                    // Anetari e ka huazuar artikullin qe ishte mbajtur per te — rezervimi
                    // "konsumohet" plotesisht (fulfilled=true, ready_for_pickup=false).
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

    public void returnItem(int loanId) {
        try (Connection connection = DBConnection.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            try {
                String itemId = lockActiveLoanAndGetItem(connection, loanId);

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE loans SET return_date = ? WHERE id = ?")) {
                    statement.setDate(1, Date.valueOf(LocalDate.now()));
                    statement.setInt(2, loanId);
                    statement.executeUpdate();
                }

                // FIFO: radha percaktohet nga reservation_date ASC, njesoj si
                // ReservationDAO.findQueueForItem() — por ketu brenda te njejtit
                // transaksion, me FOR UPDATE per te bllokuar rreshtin e zgjedhur.
                Integer nextReservation = lockNextWaitingReservation(connection, itemId);

                if (nextReservation == null) {
                    updateAvailability(connection, itemId, true);
                } else {
                    // Artikulli mbahet per anetarin e radhes — s'behet automatikisht
                    // "available" per te tjeret, vetem "ready_for_pickup" per te.
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE reservations SET ready_for_pickup = true WHERE id = ?")) {
                        statement.setInt(1, nextReservation);
                        statement.executeUpdate();
                    }
                    updateAvailability(connection, itemId, false);
                }

                connection.commit();
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

    // Kontrollon nese ka nje rezervim "ready_for_pickup" per kete artikull.
    // Nese po, dhe eshte per NJE ANETAR TJETER — hidhet exception (mbrojtje FIFO,
    // dikush s'mund te "kapercej" radhen). Nese eshte per vete anetarin qe po huazon,
    // kthehet id-ja e rezervimit qe do te "konsumohet" ne borrowItem().
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

    private String lockActiveLoanAndGetItem(Connection connection, int loanId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT item_id, return_date FROM loans WHERE id = ? FOR UPDATE")) {
            statement.setInt(1, loanId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalArgumentException("Huazimi me ID " + loanId + " nuk u gjet.");
                if (result.getDate("return_date") != null) throw new IllegalStateException("Huazimi eshte kthyer tashme.");
                return result.getString("item_id");
            }
        }
    }

    private Integer lockNextWaitingReservation(Connection connection, String itemId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM reservations WHERE item_id = ? AND fulfilled = false AND ready_for_pickup = false " +
                        "ORDER BY reservation_date, id LIMIT 1 FOR UPDATE")) {
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

    // Provo Book, pastaj DVD — supozon hapesira ID te veçanta (p.sh. B001/D001).
    private LibraryItem resolveItem(String itemId) {
        return bookDAO.findById(itemId).map(item -> (LibraryItem) item)
                .or(() -> dvdDAO.findById(itemId).map(item -> (LibraryItem) item))
                .orElseThrow(() -> new IllegalArgumentException("Artikulli me ID " + itemId + " nuk u gjet."));
    }
}