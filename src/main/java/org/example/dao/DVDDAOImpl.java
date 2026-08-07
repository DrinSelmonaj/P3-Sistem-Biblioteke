package org.example.dao;

import org.example.model.Category;
import org.example.model.DVD;
import org.example.model.InvalidCategoryException;
import org.example.util.CategoryParser;
import org.example.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DVDDAOImpl implements DVDDAO {

    @Override
    public Optional<DVD> findById(String id) {
        String sql = "SELECT li.id, li.title, li.category, li.available, d.duration_minutes " +
                "FROM library_items li JOIN dvds d ON li.id = d.item_id " +
                "WHERE li.id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRowToDVD(rs));
            }
            return Optional.empty();

        } catch (SQLException | InvalidCategoryException e) {
            throw new RuntimeException("Gabim gjate kerkimit te DVD: " + id, e);
        }
    }
    @Override
    public List<DVD> findAll() {
        String sql = "SELECT li.id, li.title, li.category, li.available, d.duration_minutes " +
                "FROM library_items li JOIN dvds d ON li.id = d.item_id";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            List<DVD> dvds = new ArrayList<>();
            while (rs.next()) {
                dvds.add(mapRowToDVD(rs));
            }
            return dvds;
        } catch (SQLException | InvalidCategoryException e) {
            throw new RuntimeException("Gabim gjate marrjes së të gjitha DVD-ve", e);
        }
    }
    @Override
    public void save(DVD dvd) {
        String insertItem = "INSERT INTO library_items (id, title, category, available, item_type) " +
                "VALUES (?, ?, ?, ?, 'DVD')";
        String insertDVD = "INSERT INTO dvds (item_id, duration_minutes) VALUES (?, ?)";

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement stmt1 = conn.prepareStatement(insertItem)) {
                    stmt1.setString(1, dvd.getId());
                    stmt1.setString(2, dvd.getTitle());
                    stmt1.setString(3, dvd.getCategory().name());
                    stmt1.setBoolean(4, dvd.isAvailable());
                    stmt1.executeUpdate();
                }

                try (PreparedStatement stmt2 = conn.prepareStatement(insertDVD)) {
                    stmt2.setString(1, dvd.getId());
                    stmt2.setInt(2, dvd.getDurationMinutes());
                    stmt2.executeUpdate();
                }

                conn.commit();

            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException("Gabim gjate ruajtjes se DVD-it, ndryshimet u anuluan: " + dvd.getId(), e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate lidhjes me databazen", e);
        }
    }
    @Override
    public void update(DVD dvd) {
        String sql = "UPDATE library_items SET title = ?, category = ?, available = ? WHERE id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, dvd.getTitle());
            stmt.setString(2, dvd.getCategory().name());
            stmt.setBoolean(3, dvd.isAvailable());
            stmt.setString(4, dvd.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate azhurimit të DVD-it", e);
        }
    }
    @Override
    public void delete(String id) {
     String sql = "DELETE FROM library_items WHERE id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate fshirjes së DVD-it", e);
        }
    }

    private DVD mapRowToDVD(ResultSet rs) throws SQLException, InvalidCategoryException {
        Category category = CategoryParser.fromInput(rs.getString("category"));
        DVD dvd = new DVD(
                rs.getString("id"),
                rs.getString("title"),
                category,
                rs.getInt("duration_minutes")
        );
        dvd.setAvailable(rs.getBoolean("available"));
        return dvd;
    }
}