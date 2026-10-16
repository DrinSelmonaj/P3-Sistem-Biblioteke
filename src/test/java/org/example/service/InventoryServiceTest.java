package org.example.service;

import org.example.TestData;
import org.example.dao.*;
import org.example.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// normalizeIsbn() — test njesie; pjesa tjeter teste integrimi (kerkon Postgres real).
class InventoryServiceTest {

    private final MemberDAO memberDAO = new MemberDAOImpl();
    private final BookDAO bookDAO = new BookDAOImpl();
    private final DVDDAO dvdDAO = new DVDDAOImpl();
    private final LibrarianDAO librarianDAO = new LibrarianDAOImpl();
    private final LoanService loanService = new LoanService(memberDAO, bookDAO, dvdDAO);
    private final InventoryService inventory = new InventoryService(bookDAO, dvdDAO);

    private TestData data;
    private Librarian librarian;

    @BeforeEach
    void setUp() throws Exception {
        data = new TestData();
        librarian = librarianDAO.findById(data.createLibrarian()).orElseThrow();
    }

    @AfterEach
    void tearDown() throws Exception {
        data.close();
    }

    @Test
    void isbnIsNormalizedAndValidated() {
        assertEquals("9780135957059", InventoryService.normalizeIsbn("978-0135957059"));
        assertEquals("013235088X", InventoryService.normalizeIsbn("0-13-235088-x"));
        assertThrows(IllegalArgumentException.class, () -> InventoryService.normalizeIsbn("12345"));
        assertThrows(IllegalArgumentException.class, () -> InventoryService.normalizeIsbn(""));
        assertThrows(IllegalArgumentException.class, () -> InventoryService.normalizeIsbn("978013595705A"));
    }

    @Test
    void sameIsbnWithDashesIsRejectedAsDuplicate() {
        String isbn = data.uniqueIsbn();
        inventory.addBook(librarian, data.reserveItemId(), "Liber", Category.SCIENCE, "Autor", isbn);

        String dashed = isbn.substring(0, 3) + "-" + isbn.substring(3);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> inventory.addBook(librarian, data.reserveItemId(), "Kopje", Category.SCIENCE, "Autor", dashed));
        assertEquals("ID ose ISBN ekziston tashme ne sistem.", e.getMessage());
    }

    @Test
    void memberCannotManageInventory() throws Exception {
        Member member = memberDAO.findById(data.createMember()).orElseThrow();
        assertThrows(SecurityException.class,
                () -> inventory.addDVD(member, data.reserveItemId(), "Film", Category.FICTION, 120));
    }

    @Test
    void addAndUpdateDvdPersistsAllFields() {
        String id = data.reserveItemId();
        inventory.addDVD(librarian, id, "Film", Category.FICTION, 120);
        inventory.updateDVD(librarian, id, "Film (Director's Cut)", Category.HISTORY, 150);

        DVD stored = dvdDAO.findById(id).orElseThrow();
        assertEquals("Film (Director's Cut)", stored.getTitle());
        assertEquals(Category.HISTORY, stored.getCategory());
        assertEquals(150, stored.getDurationMinutes());

        assertThrows(IllegalArgumentException.class,
                () -> inventory.updateDVD(librarian, id, "Film", Category.FICTION, 0));
    }

    // Rregullimi i BookDAOImpl.update(): ndryshimi i titullit s'duhet ta beje
    // "te lire" nje liber qe eshte i huazuar.
    @Test
    void updatingBorrowedBookKeepsItUnavailable() throws Exception {
        String bookId = data.createBook(true);
        Member member = memberDAO.findById(data.createMember()).orElseThrow();
        Book before = bookDAO.findById(bookId).orElseThrow(); // lexuar kur ishte i lire

        loanService.borrowItem(member, member.getId(), bookId);
        inventory.updateBook(librarian, bookId, "Titull i ri", before.getCategory(), "Autor i ri", data.uniqueIsbn());

        Book after = bookDAO.findById(bookId).orElseThrow();
        assertEquals("Titull i ri", after.getTitle());
        assertEquals("Autor i ri", after.getAuthor());
        assertFalse(after.isAvailable(), "libri eshte ende i huazuar");
    }

    @Test
    void itemWithHistoryCannotBeDeletedButUnusedItemCan() throws Exception {
        String usedBook = data.createBook(true);
        Member member = memberDAO.findById(data.createMember()).orElseThrow();
        Loan loan = loanService.borrowItem(member, member.getId(), usedBook);
        loanService.returnItem(member, loan.getId());

        assertThrows(IllegalStateException.class, () -> inventory.deleteItem(librarian, usedBook));
        assertTrue(bookDAO.findById(usedBook).isPresent());

        String unusedBook = data.createBook(true);
        inventory.deleteItem(librarian, unusedBook);
        assertTrue(bookDAO.findById(unusedBook).isEmpty());
    }
}
