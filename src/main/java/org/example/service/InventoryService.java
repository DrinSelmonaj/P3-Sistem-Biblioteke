package org.example.service;

import org.example.dao.BookDAO;
import org.example.dao.DVDDAO;
import org.example.model.Book;
import org.example.model.Category;
import org.example.model.DVD;
import org.example.model.LibraryItem;
import org.example.model.Person;
import org.example.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

// Menaxhimi i katalogut nga bibliotekari: shto / ndrysho / fshi libra dhe DVD.
// PSE nje service me vete (jo DAO direkt nga Main): autorizimi dhe validimi
// jane rregulla biznesi — DAO-ja vetem lexon/shkruan, s'vendos kush ka te drejte.
public class InventoryService {

    private static final int MAX_TEXT = 255;          // VARCHAR(255) te title/author
    private static final int MAX_DURATION_MINUTES = 1000;

    private final BookDAO bookDAO;
    private final DVDDAO dvdDAO;

    public InventoryService(BookDAO bookDAO, DVDDAO dvdDAO) {
        this.bookDAO = bookDAO;
        this.dvdDAO = dvdDAO;
    }

    public Book addBook(Person actor, String id, String title, Category category, String author, String isbn) {
        requireLibrarian(actor);
        Book book = new Book(validateId(id), validateText(title, "Titulli"), requireCategory(category),
                validateText(author, "Autori"), normalizeIsbn(isbn));
        saveWithDuplicateCheck(() -> bookDAO.save(book));
        return book;
    }

    public DVD addDVD(Person actor, String id, String title, Category category, int durationMinutes) {
        requireLibrarian(actor);
        DVD dvd = new DVD(validateId(id), validateText(title, "Titulli"), requireCategory(category),
                validateDuration(durationMinutes));
        saveWithDuplicateCheck(() -> dvdDAO.save(dvd));
        return dvd;
    }

    public Book updateBook(Person actor, String id, String title, Category category, String author, String isbn) {
        requireLibrarian(actor);
        Book existing = bookDAO.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Libri me ID " + id + " nuk u gjet."));
        Book updated = new Book(id, validateText(title, "Titulli"), requireCategory(category),
                validateText(author, "Autori"), normalizeIsbn(isbn));
        updated.setAvailable(existing.isAvailable()); // vetem per objektin ne memorie — DAO s'e shkruan
        saveWithDuplicateCheck(() -> bookDAO.update(updated));
        return updated;
    }

    public DVD updateDVD(Person actor, String id, String title, Category category, int durationMinutes) {
        requireLibrarian(actor);
        DVD existing = dvdDAO.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("DVD-ja me ID " + id + " nuk u gjet."));
        DVD updated = new DVD(id, validateText(title, "Titulli"), requireCategory(category),
                validateDuration(durationMinutes));
        updated.setAvailable(existing.isAvailable());
        dvdDAO.update(updated);
        return updated;
    }

    public Optional<LibraryItem> findItem(String id) {
        return bookDAO.findById(id).map(book -> (LibraryItem) book)
                .or(() -> dvdDAO.findById(id).map(dvd -> (LibraryItem) dvd));
    }

    // Fshin vetem artikuj pa asnje histori. PSE: loans/reservations kane foreign key
    // drejt library_items — historia e huazimeve dhe gjobave duhet te mbetet e
    // lexueshme per raportet, keshtu qe nje artikull i perdorur s'fshihet kurre.
    public void deleteItem(Person actor, String id) {
        requireLibrarian(actor);
        LibraryItem item = findItem(id)
                .orElseThrow(() -> new IllegalArgumentException("Artikulli me ID " + id + " nuk u gjet."));

        if (hasHistory(id)) {
            throw new IllegalStateException("Artikulli \"" + item.getTitle() + "\" ka histori huazimesh ose "
                    + "rezervimesh — s'mund te fshihet, qe raportet dhe gjobat te mbeten te sakta.");
        }
        try {
            if (item instanceof Book) bookDAO.delete(id);
            else dvdDAO.delete(id);
        } catch (RuntimeException e) {
            // 23503 = foreign key: dikush e huazoi/rezervoi mes kontrollit dhe fshirjes
            if (e.getCause() instanceof SQLException sql && "23503".equals(sql.getSQLState())) {
                throw new IllegalStateException("Artikulli sapo u huazua ose u rezervua — s'mund te fshihet.");
            }
            throw e;
        }
    }

    // ISBN pranohet me ose pa viza/hapesira, por ruhet vetem me shifra (dhe X ne
    // fund per ISBN-10) — perndryshe "978-0135957059" dhe "9780135957059" do te
    // kalonin te dyja kontrollin UNIQUE si libra te ndryshem.
    static String normalizeIsbn(String isbn) {
        String normalized = isbn == null ? "" : isbn.replaceAll("[\\s-]", "").toUpperCase();
        if (!normalized.matches("\\d{13}|\\d{9}[\\dX]")) {
            throw new IllegalArgumentException("ISBN duhet te kete 10 ose 13 shifra (lejohen viza).");
        }
        return normalized;
    }

    private boolean hasHistory(String itemId) {
        try (Connection connection = DBConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT EXISTS (SELECT 1 FROM loans WHERE item_id = ?) " +
                             "OR EXISTS (SELECT 1 FROM reservations WHERE item_id = ?)")) {
            statement.setString(1, itemId);
            statement.setString(2, itemId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate kontrollit te historise se artikullit", e);
        }
    }

    // SQLState 23505 = UNIQUE/PRIMARY KEY — ID ose ISBN ekziston tashme
    private void saveWithDuplicateCheck(Runnable save) {
        try {
            save.run();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                throw new IllegalArgumentException("ID ose ISBN ekziston tashme ne sistem.");
            }
            throw e;
        }
    }

    private static String validateId(String id) {
        // Vetem shkronja/shifra/_/- — ID-te shfaqen ne menu dhe shkruhen nga perdoruesi
        if (id == null || !id.trim().matches("[A-Za-z0-9_-]{1,50}")) {
            throw new IllegalArgumentException("ID duhet te kete 1-50 shkronja, shifra, _ ose -.");
        }
        return id.trim();
    }

    private static String validateText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " s'mund te jete bosh.");
        if (value.trim().length() > MAX_TEXT) throw new IllegalArgumentException(field + " s'mund te kaloje " + MAX_TEXT + " karaktere.");
        return value.trim();
    }

    private static Category requireCategory(Category category) {
        if (category == null) throw new IllegalArgumentException("Zgjidh nje kategori.");
        return category;
    }

    private static int validateDuration(int minutes) {
        if (minutes < 1 || minutes > MAX_DURATION_MINUTES) {
            throw new IllegalArgumentException("Kohezgjatja duhet te jete 1-" + MAX_DURATION_MINUTES + " minuta.");
        }
        return minutes;
    }

    private static void requireLibrarian(Person actor) {
        if (!actor.canManageInventory()) {
            throw new SecurityException("Vetem bibliotekaret mund te menaxhojne inventarin.");
        }
    }
}
