package org.example.dao;

import org.example.model.LibraryItem;
import org.example.model.Member;
import org.example.model.Reservation;
import org.example.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReservationDAOImpl implements ReservationDAO {

    // Njesoj si LoanDAOImpl: Composition + Dependency Injection.
    // Na duhen te tria per resolveItem() (Book/DVD) dhe per mapRowToReservation() (Member).
    private final MemberDAO memberDAO;
    private final BookDAO bookDAO;
    private final DVDDAO dvdDAO;

    public ReservationDAOImpl(MemberDAO memberDAO, BookDAO bookDAO, DVDDAO dvdDAO) {
        this.memberDAO = memberDAO;
        this.bookDAO = bookDAO;
        this.dvdDAO = dvdDAO;
    }

    @Override
    public Optional<Reservation> findById(int id) {
        String sql = "SELECT id, member_id, item_id, reservation_date, fulfilled " +
                "FROM reservations WHERE id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRowToReservation(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate kerkimit te rezervimit " + id, e);
        }
    }

    @Override
    public List<Reservation> findAll() {
        String sql = "SELECT id, member_id, item_id, reservation_date, fulfilled FROM reservations";

        List<Reservation> reservations = new ArrayList<>();

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                reservations.add(mapRowToReservation(rs));
            }
            return reservations;
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate kerkimit te rezervimeve", e);
        }
    }

    @Override
    public void save(Reservation reservation) {
        // RETURNING id — njesoj si te LoanDAOImpl.save(): e vendosim id-ne e gjeneruar
        // direkt ne objektin qe therret save(), keshtu qe pas kesaj thirrjeje
        // reservation.getId() s'eshte me null.
        // fulfilled s'perfshihet ne INSERT — ka DEFAULT false ne skeme.
        String sql = "INSERT INTO reservations (member_id, item_id, reservation_date) VALUES (?, ?, ?) RETURNING id";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, reservation.getMember().getId());
            stmt.setString(2, reservation.getItem().getId());
            stmt.setDate(3, Date.valueOf(reservation.getReservationDate()));

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                reservation.setId(rs.getInt("id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate ruajtjes se rezervimit", e);
        }
    }

    @Override
    public void markFulfilled(int reservationId) {
        String sql = "UPDATE reservations SET fulfilled = true WHERE id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, reservationId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate azhurnimit te rezervimit " + reservationId, e);
        }
    }

    @Override
    public List<Reservation> findQueueForItem(String itemId) {
        // Radha FIFO "on the fly": vetem rezervimet e papermbushura per kete artikull,
        // renditur sipas reservation_date ASC. Elementi i pare i listes = radhes.
        String sql = "SELECT id, member_id, item_id, reservation_date, fulfilled " +
                "FROM reservations WHERE item_id = ? AND fulfilled = false " +
                "ORDER BY reservation_date ASC";

        List<Reservation> queue = new ArrayList<>();

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, itemId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                queue.add(mapRowToReservation(rs));
            }
            return queue;
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate kerkimit te radhes per artikullin " + itemId, e);
        }
    }

    // Njesoj si resolveItem() ne LoanDAOImpl — polimorfizem mbi item_type
    // per te ditur nese duhet thirrur bookDAO apo dvdDAO.
    private LibraryItem resolveItem(String itemId) {
        String sql = "SELECT * FROM library_items WHERE id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, itemId);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                throw new RuntimeException("Artikulli s'u gjet ne library_items: " + itemId);
            }

            String itemType = rs.getString("item_type");

            return switch (itemType) {
                case "BOOK" -> bookDAO.findById(itemId)
                        .orElseThrow(() -> new RuntimeException("Book me ID " + itemId + " nuk u gjet."));
                case "DVD" -> dvdDAO.findById(itemId)
                        .orElseThrow(() -> new RuntimeException("DVD me ID " + itemId + " nuk u gjet."));
                default -> throw new RuntimeException("Lloji i artikullit nuk eshte i njohur: " + itemType);
            };

        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate kerkimit te llojit", e);
        }
    }

    private Reservation mapRowToReservation(ResultSet rs) throws SQLException {
        String memberId = rs.getString("member_id");
        String itemId = rs.getString("item_id");

        Member member = memberDAO.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Perdoruesi me ID " + memberId + " nuk u gjet."));
        LibraryItem item = resolveItem(itemId);

        Reservation reservation = new Reservation(member, item, rs.getDate("reservation_date").toLocalDate());
        reservation.setId(rs.getInt("id"));

        if (rs.getBoolean("fulfilled")) {
            reservation.markFulfilled();
        }

        return reservation;
    }
}