package org.example.dao;

import org.example.model.Book;
import org.example.model.Category;
import org.example.model.InvalidCategoryException;
import org.example.util.CategoryParser;
import org.example.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BookDAOImpl implements BookDAO {

    @Override
    public Optional<Book> findById(String id) {
        String sql = "SELECT li.id, li.title, li.category, li.available, b.author, b.isbn " +
                "FROM library_items li JOIN books b ON li.id = b.item_id " +
                "WHERE li.id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRowToBook(rs));
            }
            return Optional.empty();

        } catch (SQLException | InvalidCategoryException e) {
            throw new RuntimeException("Gabim gjate kerkimit te librit: " + id, e);
        }
    }

    @Override
    public List<Book> findAll() {
        String sql = "SELECT li.id, li.title, li.category, li.available, b.author, b.isbn " +
                "FROM library_items li JOIN books b ON li.id = b.item_id";

        List<Book> books = new ArrayList<>();

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                books.add(mapRowToBook(rs));
            }
            return books;

        } catch (SQLException | InvalidCategoryException e) {
            throw new RuntimeException("Gabim gjate leximit te te gjithe librave", e);
        }
    }

    @Override
    public void save(Book book) {
        String insertItem = "INSERT INTO library_items (id, title, category, available, item_type) " +
                "VALUES (?, ?, ?, ?, 'BOOK')";
        String insertBook = "INSERT INTO books (item_id, author, isbn) VALUES (?, ?, ?)";

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement stmt1 = conn.prepareStatement(insertItem)) {
                stmt1.setString(1, book.getId());
                stmt1.setString(2, book.getTitle());
                stmt1.setString(3, book.getCategory().name());
                stmt1.setBoolean(4, book.isAvailable());
                stmt1.executeUpdate();
            }

            try (PreparedStatement stmt2 = conn.prepareStatement(insertBook)) {
                stmt2.setString(1, book.getId());
                stmt2.setString(2, book.getAuthor());
                stmt2.setString(3, book.getIsbn());
                stmt2.executeUpdate();
            }

            conn.commit();

        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate ruajtjes se librit: " + book.getId(), e);
        }
    }

    @Override
    public void update(Book book) {
        String sql = "UPDATE library_items SET title = ?, category = ?, available = ? WHERE id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, book.getTitle());
            stmt.setString(2, book.getCategory().name());
            stmt.setBoolean(3, book.isAvailable());
            stmt.setString(4, book.getId());
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate perditesimit te librit: " + book.getId(), e);
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
            throw new RuntimeException("Gabim gjate fshirjes se librit: " + id, e);
        }
    }

    private Book mapRowToBook(ResultSet rs) throws SQLException, InvalidCategoryException {
        Category category = CategoryParser.fromInput(rs.getString("category"));
        Book book = new Book(
                rs.getString("id"),
                rs.getString("title"),
                category,
                rs.getString("author"),
                rs.getString("isbn")
        );
        book.setAvailable(rs.getBoolean("available"));
        return book;
    }
}