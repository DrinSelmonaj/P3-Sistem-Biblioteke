package org.example.service;

import org.example.model.Member;
import org.example.model.Person;
import org.example.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

// SQL direkt brenda 1 transaksioni te vetem — i njejti pattern si LoanService/FineService.
// PSE ky service ekziston: deri ne Kapitullin 7, rezervimet "ready_for_pickup" mbeteshin
// te mbajtura per anetarin per gjithmone, pa afat — nese anetari kurre s'vinte ta merrte,
// artikulli mbetej i bllokuar perhere. expireStaleReadyPickups() mbyll kete gap.
public class ReservationService {

    // Thelbi i "job-it" periodik — thirret nga nje scheduler ne Main (ose mund te
    // thirret manualisht/nga teste). Per çdo rezervim "ready_for_pickup" me kohe
    // te kaluar, e anulon dhe i jep radhen tjetrit ne rradhe (ose liron artikullin).
    public void expireStaleReadyPickups(int daysAllowed) {
        List<Integer> expiredIds = findExpiredReadyPickupIds(daysAllowed);

        for (int reservationId : expiredIds) {
            expireOneReservation(reservationId);
        }
    }

    private List<Integer> findExpiredReadyPickupIds(int daysAllowed) {
        String sql = "SELECT id FROM reservations WHERE ready_for_pickup = true AND fulfilled = false " +
                "AND cancelled = false AND ready_since < NOW() - (? * INTERVAL '1 day')";

        List<Integer> ids = new ArrayList<>();
        try (Connection connection = DBConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, daysAllowed);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ids.add(result.getInt("id"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate kerkimit te rezervimeve te skaduara", e);
        }
        return ids;
    }

    // Nje transaksion i vetem per rezervim te skaduar — njesoj si returnItem() ne
    // LoanService: rezervimi bllokohet me FOR UPDATE, anulohet, dhe kontrollohet
    // nese ka radhe tjeter per te njejtin artikull.
    private void expireOneReservation(int reservationId) {
        try (Connection connection = DBConnection.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            try {
                String itemId;
                String memberId;

                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT item_id, member_id, ready_for_pickup, fulfilled, cancelled " +
                                "FROM reservations WHERE id = ? FOR UPDATE")) {
                    statement.setInt(1, reservationId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) return; // eshte fshire/ndryshuar nderkohe, injorohet
                        if (!result.getBoolean("ready_for_pickup") || result.getBoolean("fulfilled")
                                || result.getBoolean("cancelled")) {
                            return; // gjendja eshte ndryshuar nderkohe (dikush e huazoi/anuloi), injorohet
                        }
                        itemId = result.getString("item_id");
                        memberId = result.getString("member_id");
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE reservations SET cancelled = true, ready_for_pickup = false WHERE id = ?")) {
                    statement.setInt(1, reservationId);
                    statement.executeUpdate();
                }

                Integer nextReservation = lockNextWaitingReservation(connection, itemId);
                if (nextReservation == null) {
                    updateAvailability(connection, itemId, true);
                } else {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE reservations SET ready_for_pickup = true, ready_since = NOW() WHERE id = ?")) {
                        statement.setInt(1, nextReservation);
                        statement.executeUpdate();
                    }
                }

                connection.commit();
                notifyMember(memberId, "Rezervimi yt #" + reservationId + " skadoi sepse s'e more artikullin ne kohe.");

            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate skadimit te rezervimit " + reservationId, e);
        }
    }

    // Autorizim: Member mund te anuloje VETEM rezervimet e veta; Librarian cilendo.
    public void cancelReservation(Person actor, int reservationId) {
        try (Connection connection = DBConnection.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            try {
                String itemId;
                String memberId;
                boolean wasReady;

                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT item_id, member_id, ready_for_pickup, fulfilled, cancelled " +
                                "FROM reservations WHERE id = ? FOR UPDATE")) {
                    statement.setInt(1, reservationId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) throw new IllegalArgumentException("Rezervimi nuk u gjet.");
                        if (result.getBoolean("fulfilled")) throw new IllegalStateException("Rezervimi eshte permbushur tashme.");
                        if (result.getBoolean("cancelled")) throw new IllegalStateException("Rezervimi eshte anuluar tashme.");
                        itemId = result.getString("item_id");
                        memberId = result.getString("member_id");
                        wasReady = result.getBoolean("ready_for_pickup");
                    }
                }

                if (!actor.canManageInventory() && !actor.getId().equals(memberId)) {
                    throw new SecurityException("Mund te anulosh vetem rezervimet e tua.");
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE reservations SET cancelled = true, ready_for_pickup = false WHERE id = ?")) {
                    statement.setInt(1, reservationId);
                    statement.executeUpdate();
                }

                // Vetem nese rezervimi ishte "ready_for_pickup" (artikulli aktualisht
                // mbahej per kete anetar) ka nevoje te promovohet radha ose te lirohet
                // artikulli. Nese ishte vetem ne prite (jo ende ready), asgje s'ndryshon
                // ne library_items — artikulli eshte diku tjeter (i huazuar nga dikush,
                // ose ne prite te dikujt tjeter ne radhe).
                if (wasReady) {
                    Integer nextReservation = lockNextWaitingReservation(connection, itemId);
                    if (nextReservation == null) {
                        updateAvailability(connection, itemId, true);
                    } else {
                        try (PreparedStatement statement = connection.prepareStatement(
                                "UPDATE reservations SET ready_for_pickup = true, ready_since = NOW() WHERE id = ?")) {
                            statement.setInt(1, nextReservation);
                            statement.executeUpdate();
                        }
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
            throw new RuntimeException("Gabim gjate anulimit te rezervimit " + reservationId, e);
        }
    }

    // I njejti pattern si lockNextWaitingReservation() ne LoanService — e perseritim
    // ketu (jo e ndajme ne nje klase te perbashket) sepse eshte SQL i thjeshte dhe
    // shtimi i nje abstraksioni te ri per 6 rreshta kod do te ishte tepricë.
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
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE library_items SET available = ? WHERE id = ?")) {
            statement.setBoolean(1, available);
            statement.setString(2, itemId);
            statement.executeUpdate();
        }
    }

    // Placeholder per njoftim real (email/SMS) — tani thjesht printon ne konsol.
    // Pike e qarte zgjerimi per me vone, jashte qellimit te Kapitullit 7.
    private void notifyMember(String memberId, String message) {
        System.out.println("[NJOFTIM per " + memberId + "]: " + message);
    }
}