package org.example.dao;

import org.example.model.Librarian;

import java.util.List;
import java.util.Optional;

/** Qasja ne databaze per bibliotekaret ({@code persons} + {@code librarians}). */
public interface LibrarianDAO {

    /**
     * @param id username-i i bibliotekarit
     * @return bibliotekari, ose bosh nese s'ekziston ose personi eshte anetar
     */
    Optional<Librarian> findById(String id);

    /** @return te gjithe bibliotekaret */
    List<Librarian> findAll();

    /**
     * Ruan bibliotekarin me nje hash placeholder. Per krijim te plote me fjalekalim
     * perdor {@link org.example.service.AuthService#registerLibrarian}.
     *
     * @param librarian bibliotekari i ri
     */
    void save(Librarian librarian);

    /**
     * Perditeson kodin e punonjesit.
     *
     * @param librarian bibliotekari me kodin e ri (identifikohet me ID)
     * @throws RuntimeException nese bibliotekari s'ekziston
     */
    void update(Librarian librarian);

    /**
     * Fshin llogarine e plote (persons, me cascade te librarians).
     *
     * @param id username-i i bibliotekarit
     */
    void delete(String id);
}
