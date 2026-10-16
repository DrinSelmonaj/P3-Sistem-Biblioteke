package org.example.dao;

import org.example.TestData;
import org.example.model.Librarian;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Test integrimi — update() perdorte kolonen `id` qe s'ekziston te librarians,
// dhe delete() linte rreshtin e persons pas vetes.
class LibrarianDAOImplTest {

    private final LibrarianDAO librarianDAO = new LibrarianDAOImpl();

    @Test
    void updateChangesEmployeeCodeAndDeleteRemovesWholeAccount() throws Exception {
        try (TestData data = new TestData()) {
            String id = data.createLibrarian();
            Librarian librarian = librarianDAO.findById(id).orElseThrow();
            String newCode = data.uniqueEmployeeCode();

            librarianDAO.update(new Librarian(id, librarian.getName(), librarian.getEmail(),
                    librarian.getPhone(), newCode));
            assertEquals(newCode, librarianDAO.findById(id).orElseThrow().getEmployeeCode());

            librarianDAO.delete(id);
            assertTrue(librarianDAO.findById(id).isEmpty());
            assertFalse(data.personExists(id), "rreshti i persons duhet te fshihet bashke me librarians");
        }
    }
}
