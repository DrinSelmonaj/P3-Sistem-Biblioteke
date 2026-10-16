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

/**
 * Mban te sinkronizuara {@code fines} dhe bilancin e papaguar te anetarit
 * ({@code members.unpaid_fines}), dhe zbaton kush lejohet te beje cfare
 * (autorizim ne backend, jo vetem ndarje menuje ne CLI — rregullat e Kapitullit 6).
 *
 * <ul>
 *   <li>{@link #issue}: vetem Librarian (krijimi i gjobave eshte pune stafi)</li>
 *   <li>{@link #payFine}: Member paguan VETEM gjoben e vet; Librarian gjithkujt
 *       (p.sh. pagese cash ne sportel ne emer te anetarit)</li>
 *   <li>{@link #getFinesForMember}: Member sheh vetem gjobat e veta; Librarian cilindo</li>
 *   <li>{@link #getAllPayments}: vetem Librarian — historia e plote e sistemit</li>
 * </ul>
 *
 * <p>Njesoj si LoanService: SQL direkt brenda nje transaksioni per shkrimet (jo permes
 * FineDAO). FineDAO perdoret vetem per lexime, jashte cdo transaksioni shkrimi.
 * Gjobat per kthim te vonuar krijohen automatikisht nga {@link LoanService#returnItem}.
 */
public class FineService {

    private final FineDAO fineDAO;

    /** @param fineDAO per leximet e gjobave */
    public FineService(FineDAO fineDAO) {
        this.fineDAO = fineDAO;
    }

    /**
     * Krijon gjobe manuale per nje huazim te vonuar (p.sh. huazim ende aktiv) dhe rrit
     * bilancin e anetarit. Nese huazimi ka gjoba tashme, ngarkohet vetem diferenca — e njejta
     * logjike si gjoba automatike ne kthim, qe anetari te mos paguaje dy here.
     *
     * @param actor personi qe e kryen veprimin; duhet te jete bibliotekar
     * @param fine  gjoba me shumen e llogaritur nga ditet e vonese; pas thirrjes ka ID dhe
     *              shumen e ngarkuar realisht
     * @throws SecurityException        nese actor s'eshte bibliotekar
     * @throws IllegalArgumentException nese huazimi s'eshte ruajtur ose shuma s'eshte pozitive
     * @throws IllegalStateException    nese huazimi eshte gjobitur tashme per te gjitha ditet
     */
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
                // Huazimi mund te kete gjobe tashme (automatike ne kthim, ose manuale me
                // pare) — ngarkohet vetem diferenca, njesoj si LoanService.issueOverdueFine().
                // FOR UPDATE mbi huazimin: dy gjobitje njekohesisht s'e llogarisin dy here.
                try (PreparedStatement lock = connection.prepareStatement(
                        "SELECT id FROM loans WHERE id = ? FOR UPDATE");
                     PreparedStatement statement = connection.prepareStatement(
                        "SELECT COALESCE(SUM(amount), 0) FROM fines WHERE loan_id = ?")) {
                    lock.setInt(1, fine.getLoan().getId());
                    lock.executeQuery().close();
                    statement.setInt(1, fine.getLoan().getId());
                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        double remaining = fine.getAmount() - result.getDouble(1);
                        if (remaining <= 0) {
                            throw new IllegalStateException("Ky huazim eshte gjobitur tashme per te gjitha ditet e vonese.");
                        }
                        fine.setAmount(remaining);
                    }
                }

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

    /**
     * Paguan nje gjobe dhe zbret bilancin e anetarit (kurre nen zero). Veprim aktiv qe
     * vete anetari mund ta kryeje, jo vetem "dikush e shenon te paguar" nga stafi.
     *
     * @param actor  anetari qe paguan gjoben e vet, ose bibliotekar
     * @param fineId ID-ja e gjobes
     * @throws IllegalArgumentException nese gjoba s'ekziston
     * @throws IllegalStateException    nese gjoba eshte paguar tashme
     * @throws SecurityException        nese anetari provon te paguaje gjoben e dikujt tjeter
     */
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

    /**
     * "Gjobat e mia" — perfshin te paguarat dhe te papaguarat; CLI-ja i filtron me
     * {@code isPaid()} sipas nevojes se ekranit.
     *
     * @param actor    anetari vete, ose bibliotekar
     * @param memberId anetari, gjobat e te cilit kerkohen
     * @return gjobat, me te rejat ne fillim
     * @throws SecurityException nese anetari kerkon gjobat e dikujt tjeter
     */
    public List<Fine> getFinesForMember(Person actor, String memberId) {
        if (!actor.canManageInventory() && !actor.getId().equals(memberId)) {
            throw new SecurityException("Mund te shikosh vetem gjobat e tua.");
        }
        return fineDAO.findByMemberId(memberId);
    }

    /**
     * Historia e plote e gjobave ne sistem.
     *
     * @param actor duhet te jete bibliotekar
     * @return te gjitha gjobat
     * @throws SecurityException nese actor s'eshte bibliotekar
     */
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