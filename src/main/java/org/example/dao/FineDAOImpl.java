package org.example.dao;

import org.example.model.Fine;
import org.example.model.Loan;
import org.example.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FineDAOImpl implements FineDAO {

    // Composition + Dependency Injection, njesoj si LoanDAOImpl dhe ReservationDAOImpl.
    // Na duhet LoanDAO per te rindertuar objektin Loan te plote (me Member, LibraryItem, etc.)
    // kur lexojme nje rresht Fine nga DB — Fine.loan s'eshte thjesht nje id, eshte objekt i plote.
    private final LoanDAO loanDAO;

    public FineDAOImpl(LoanDAO loanDAO) {
        this.loanDAO = loanDAO;
    }

    @Override
    public Optional<Fine> findById(int id) {
        String sql = "SELECT id, loan_id, amount, issued_date, paid, paid_date FROM fines WHERE id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRowToFine(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate kerkimit te gjobes " + id, e);
        }
    }

    @Override
    public List<Fine> findAll() {
        String sql = "SELECT id, loan_id, amount, issued_date, paid, paid_date FROM fines";

        List<Fine> fines = new ArrayList<>();

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                fines.add(mapRowToFine(rs));
            }
            return fines;
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate kerkimit te gjobave", e);
        }
    }

    @Override
    public void save(Fine fine) {
        // fines.loan_id eshte NOT NULL dhe FK — pa id te vlefshem te Loan-it,
        // INSERT do te deshtonte gjithsesi ne DB. E kapim ketu me nje mesazh te qarte
        // ne vend qe ta lejojme te deshtoje si SQLException i paqarte.
        if (fine.getLoan().getId() == null) {
            throw new IllegalStateException(
                    "Loan-i i lidhur me kete Fine s'eshte ruajtur ende ne DB (id eshte null).");
        }

        // RETURNING id — njesoj si te LoanDAOImpl/ReservationDAOImpl.
        String sql = "INSERT INTO fines (loan_id, amount, issued_date) VALUES (?, ?, ?) RETURNING id";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, fine.getLoan().getId());
            stmt.setDouble(2, fine.getAmount());
            stmt.setDate(3, Date.valueOf(fine.getIssuedDate()));

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                fine.setId(rs.getInt("id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate ruajtjes se gjobes", e);
        }
    }

    @Override
    public void markPaid(int fineId) {
        String sql = "UPDATE fines SET paid = true, paid_date = NOW() WHERE id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, fineId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate azhurnimit te gjobes " + fineId, e);
        }
    }

    @Override
    public Optional<Fine> findByLoanId(int loanId) {
        String sql = "SELECT id, loan_id, amount, issued_date, paid, paid_date FROM fines WHERE loan_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, loanId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRowToFine(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate kerkimit te gjobes per loan " + loanId, e);
        }
    }
    @Override
    public List<Fine> findByMemberId(String memberId) {
        // JOIN me loans per te filtruar sipas anetarit — fines s'e ka vete member_id.
        String sql = "SELECT f.id, f.loan_id, f.amount, f.issued_date, f.paid, f.paid_date " +
                "FROM fines f JOIN loans l ON f.loan_id = l.id " +
                "WHERE l.member_id = ? ORDER BY f.issued_date DESC";

        List<Fine> fines = new ArrayList<>();

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, memberId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    fines.add(mapRowToFine(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate kerkimit te gjobave per anetarin " + memberId, e);
        }
        return fines;
    }

    private Fine mapRowToFine(ResultSet rs) throws SQLException {
        int loanId = rs.getInt("loan_id");

        // Rindertojme Loan-in e plote permes LoanDAO — jo vetem id.
        // Kjo eshte arsyeja pse FineDAOImpl merr LoanDAO ne konstruktor.
        Loan loan = loanDAO.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan me ID " + loanId + " nuk u gjet."));

        Fine fine = new Fine(loan, rs.getDate("issued_date").toLocalDate());
        fine.setId(rs.getInt("id"));

        // Shuma e ruajtur ne DB mund te ndryshoje nga ajo qe do llogaritej rifreskimisht
        // (p.sh. nese Loan eshte kthyer me vone se sa ishte kur u krijua Fine).
        // E mbishkruajme me vleren reale te ruajtur, sepse ajo eshte "e verteta historike".
        fine.setAmount(rs.getDouble("amount"));

        fine.setPaid(rs.getBoolean("paid"));
        Timestamp paidTs = rs.getTimestamp("paid_date");
        if (paidTs != null) {
            fine.setPaidDate(paidTs.toLocalDateTime());
        }

        return fine;
    }
}