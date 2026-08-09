package org.example.dao;

import org.example.model.Librarian;
import org.example.util.DBConnection;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LibrarianDAOImpl implements LibrarianDAO {
    @Override
    public Optional<Librarian> findById(String id) {
        String sql = "SELECT p.id, p.name, p.email, p.phone, l.employee_code" +
                " FROM persons p JOIN librarians l ON p.id = l.person_id" +
                " WHERE p.id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRowToLibrarian(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate kerkimit te bibliotekarit: " + id, e);
        }
    }
    @Override
    public List <Librarian> findAll() {
        String sql = "SELECT p.id, p.name, p.email, p.phone, l.employee_code" +
                " FROM persons p JOIN librarians l ON p.id = l.person_id" ;

        List <Librarian> librarians = new ArrayList<>();

        try(Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                librarians.add(mapRowToLibrarian(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate marrjes se te gjithe bibliotekarve", e);
        }
        return librarians;
    }

    @Override
    public void save(Librarian librarian) {
        String insertPerson = "INSERT INTO persons (id, name, email, phone, person_type) " +
                "VALUES (?, ?, ?, ?, 'LIBRARIAN')";
        String insertLibrarian = "INSERT INTO librarians (person_id, employee_code) VALUES (?, ?)";
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement personStmt = conn.prepareStatement(insertPerson);
                 PreparedStatement librarianStmt = conn.prepareStatement(insertLibrarian)) {

                personStmt.setString(1, librarian.getId());
                personStmt.setString(2, librarian.getName());
                personStmt.setString(3, librarian.getEmail());
                personStmt.setString(4, librarian.getPhone());
                personStmt.executeUpdate();

                librarianStmt.setString(1, librarian.getId());
                librarianStmt.setString(2, librarian.getEmployeeCode());
                librarianStmt.executeUpdate();

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException("Gabim gjate ruajtjes se bibliotekarit", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate lidhjes me bazen e te dhenave", e);
        }

    }
    @Override
    public void update(Librarian librarian) {
        String sql = "UPDATE librarians SET employee_code = ? WHERE id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, librarian.getEmployeeCode());
            stmt.setString(2, librarian.getId());
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new RuntimeException("Nuk u gjet asnje bibliotekar me ID: " + librarian.getId());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate perditesimit te bibliotekarit: " + librarian.getId(), e);
        }

    }
    @Override
    public void delete(String id) {
        String sql = "DELETE FROM librarians WHERE person_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate fshirjes se bibliotekarit: " + id, e);
        }
    }

    private Librarian mapRowToLibrarian(ResultSet rs) throws SQLException {
     Librarian librarian = new Librarian(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("employee_code"));
        return librarian;
    }
}
