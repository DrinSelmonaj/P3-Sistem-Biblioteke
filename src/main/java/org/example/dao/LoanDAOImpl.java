package org.example.dao;

import org.example.model.LibraryItem;
import org.example.model.Loan;
import org.example.model.Member;
import org.example.util.DBConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LoanDAOImpl implements LoanDAO {
    // Composition: LoanDAOImpl "ka" akses tek DAO-t e tjera, nuk i trashëgon

    private final MemberDAO memberDAO;
    private final BookDAO bookDAO;
    private final DVDDAO dvdDAO;

    // Dependency Injection permes konstruktorit — LoanDAOImpl s'i krijon vetë
    // implementimet konkrete, i merr nga jashte. Kjo lejon me vone (Kapitulli 7)
    // te injektohen DAO "false" (mock) per testim, pa prekur databazen reale.

    public LoanDAOImpl(MemberDAO memberDAO, BookDAO bookDAO, DVDDAO dvdDAO) {
        this.memberDAO = memberDAO;
        this.bookDAO = bookDAO;
        this.dvdDAO = dvdDAO;
    }
    @Override
    public Optional<Loan> findById(int id) {
        String sql = "SELECT id, member_id, item_id, borrow_date, due_date, return_date" +
                " FROM loans WHERE id = ?";

        try(Connection conn = DBConnection.getInstance().getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();

            if(rs.next()) {
                return Optional.of(mapRowToLoan(rs));
            }
            return Optional.empty();
        }catch (SQLException e){
            throw new RuntimeException("Gabim gjate kerkimit të llojit" + id + "", e);
        }
    }
    @Override
    public List<Loan> findAll() {
        String sql = "SELECT id , member_id , item_id, borrow_date,due_date, return_date FROM loans";

        List<Loan> loans = new ArrayList<>();

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            ;


            while (rs.next()) {
                loans.add(mapRowToLoan(rs));
            }
            return loans;
        }catch (SQLException e){
            throw new RuntimeException("Gabim gjate kerkimit të llojit", e);
        }
    }


    @Override
    public void save (Loan loan){
        // RETURNING id — PostgreSQL na kthen id-ne e gjeneruar (SERIAL) menjehere,
        // pa nevoje per nje SELECT te dyte. E vendosim direkt ne objektin Loan
        // qe e ka thirrur save(), keshtu qe pas kesaj thirrjeje loan.getId() s'eshte me null.
        String sql = "INSERT INTO loans (member_id, item_id, borrow_date, due_date) VALUES (?, ?, ?, ?) RETURNING id";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1,loan.getMember().getId());
            stmt.setString(2,loan.getItem().getId());
            stmt.setDate(3, Date.valueOf(loan.getLoanDate()));
            stmt.setDate(4,Date.valueOf(loan.getDueDate()));

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                loan.setId(rs.getInt("id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate ruajtjes se lloji", e);
        }
    }
    @Override
    public void markReturned(int loanId, LocalDate returnDate) {
        String sql = "UPDATE loans SET return_date = ? WHERE id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(returnDate));
            stmt.setInt(2, loanId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate azhurnimit te llojit", e);
        }
    }

    // Kjo eshte pika ku shfaqet polimorfizmi: s'e dime nese item_id i perket
    // nje Book apo DVD derisa te kontrollojme item_type. LibraryItem (abstrakte)
    // na lejon te kthejme te dyja llojet permes te njejtit tip referimi.
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
    private Loan mapRowToLoan(ResultSet rs) throws SQLException {
        String memberId = rs.getString("member_id");
        String itemId = rs.getString("item_id");

        Member member = memberDAO.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Perdoruesi me ID " + memberId + " nuk u gjet."));
        LibraryItem item = resolveItem(itemId);

        Loan loan = new Loan( member , item , rs.getDate("borrow_date").toLocalDate());
        loan.setId(rs.getInt("id"));

        // Mbishkruajme dueDate-in e rillogaritur automatikisht ne konstruktor
        // me vleren reale te ruajtur ne DB — shih komentin te Loan.setDueDate().
        loan.setDueDate(rs.getDate("due_date").toLocalDate());

        Date returnDate = rs.getDate("return_date");
        if (returnDate != null) {
            loan.markReturned(returnDate.toLocalDate());
        }
        return loan;
    }














}