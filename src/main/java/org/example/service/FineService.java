package org.example.service;

import org.example.dao.FineDAO;
import org.example.model.Fine;
import org.example.model.Person;
import org.example.util.DBConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

// Mban te sinkronizuara fines dhe bilancin e papaguar te anetarit (members.unpaid_fines),
// dhe tani gjithashtu zbaton kush lejohet te beje cfare (authorization ne backend,
// jo vetem ndarje menuje ne CLI — shih rregullat e Kapitullit 6).
//
// Rregulli i autorizimit:
//  - issue()            : vetem Librarian (krijimi i gjobave eshte pune stafi)
//  - payFine()           : Member mund te paguaje VETEM gjoben e vet; Librarian gjithkujt
//                          (p.sh. pagese cash ne sportel ne emer te anetarit)
//  - getFinesForMember() : Member sheh vetem gjobat e veta; Librarian sheh cilindo
//  - getAllPayments()    : vetem Librarian — historia e plote e sistemit
//
// Njesoj si LoanService: SQL direkt brenda 1 transaksioni te vetem per shkrimet
// (jo permes FineDAO). FineDAO perdoret vetem per lexime (getFinesForMember/
// getAllPayments), qe jane jashte cdo transaksioni shkrimi.
public class FineService {

    private final FineDAO fineDAO;

    public FineService(FineDAO fineDAO) {
        this.fineDAO = fineDAO;
    }

    public void issue(Person actor, Fine fine) {
        requireLibrarian(actor, "Vetem librarianet mund te krijojne gjoba.");

        if (fine.getLoan().getId() == null) {
            throw new IllegalArgumentException("Huazimi duhet te ruhet para se te krijohet gjoba.");
        }
        if (fine.getAmount() <= 0) {
            throw new IllegalArgumentException("Gjoba duhet te kete shume pozitive.");
        }

        try (Connection connection = DBConnection.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO fines (loan_id, amount, issued_date) VALUES (?, ?, ?) RETURNING id")) {
                    statement.setInt(1, fine.getLoan().getId());
                    statement.setDouble(2, fine.getAmount());
                    statement.setDate(3, Date.valueOf(fine.getIssuedDate()));
                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        fine.setId(result.getInt("id"));
                    }
                }

                // Rrit bilancin e papaguar te anetarit — kjo eshte lidhja qe mungonte.
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE members SET unpaid_fines = unpaid_fines + ? WHERE person_id = ?")) {
                    statement.setDouble(1, fine.getAmount());
                    statement.setString(2, fine.getLoan().getMember().getId());
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException("Anetari i huazimit nuk u gjet.");
                    }
                }

                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate krijimit te gjobes", e);
        }
    }

    // Riemertuar nga markPaid() -> payFine(): tani eshte veprim aktiv qe vete
    // anetari mund ta kryeje (jo vetem "dikush e shenon te paguar" nga stafi).
    public void payFine(Person actor, int fineId) {
        try (Connection connection = DBConnection.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            try {
                double amount;
                String memberId;

                // JOIN me loans per te marre member_id — Fine s'e ruan direkt,
                // e ka vetem permes Loan (shih FK schema: fines.loan_id -> loans.id).
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT f.amount, l.member_id, f.paid FROM fines f JOIN loans l ON l.id = f.loan_id " +
                                "WHERE f.id = ? FOR UPDATE")) {
                    statement.setInt(1, fineId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) throw new IllegalArgumentException("Gjoba nuk u gjet.");
                        if (result.getBoolean("paid")) throw new IllegalStateException("Gjoba eshte paguar tashme.");
                        amount = result.getDouble("amount");
                        memberId = result.getString("member_id");
                    }
                }

                // Autorizim: kontrollohet PASI dime memberId real te gjobes (jo perpara),
                // sepse vetem tani mund te krahasojme aktorin me pronarin real te gjobes.
                if (!actor.canManageInventory() && !actor.getId().equals(memberId)) {
                    throw new SecurityException("Mund te paguash vetem gjobat e tua.");
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE fines SET paid = true, paid_date = NOW() WHERE id = ?")) {
                    statement.setInt(1, fineId);
                    statement.executeUpdate();
                }

                // GREATEST(0, ...) — njesoj si Member.payFines() (Math.max(0, ...)):
                // bilanci s'shkon kurre nen zero.
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE members SET unpaid_fines = GREATEST(0, unpaid_fines - ?) WHERE person_id = ?")) {
                    statement.setDouble(1, amount);
                    statement.setString(2, memberId);
                    statement.executeUpdate();
                }

                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate pageses se gjobes", e);
        }
    }

    // "Borgjet e mia" / "historia ime" — perfshin te dyja, te paguara dhe te
    // papaguara; CLI-ja mund t'i filtroje me isPaid() sipas nevojes se ekranit.
    public List<Fine> getFinesForMember(Person actor, String memberId) {
        if (!actor.canManageInventory() && !actor.getId().equals(memberId)) {
            throw new SecurityException("Mund te shikosh vetem gjobat e tua.");
        }
        return fineDAO.findByMemberId(memberId);
    }

    // Historia e plote e sistemit — vetem stafi ka qasje, siç kerkoi Drin.
    public List<Fine> getAllPayments(Person actor) {
        requireLibrarian(actor, "Vetem librarianet mund te shikojne historine e plote te pagesave.");
        return fineDAO.findAll();
    }

    private void requireLibrarian(Person actor, String message) {
        if (!actor.canManageInventory()) {
            throw new SecurityException(message);
        }
    }
}