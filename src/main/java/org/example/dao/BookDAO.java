package org.example.dao;

import org.example.model.Book;
import java.util.List;
import java.util.Optional;

/**
 * Qasja ne databaze per librat ({@code library_items} + {@code books}, Class Table Inheritance).
 * Autorizimi dhe validimi s'behen ketu — ato i takojne {@link org.example.service.InventoryService}.
 */
public interface BookDAO {

    /**
     * @param id ID-ja e artikullit (p.sh. "B001")
     * @return libri, ose bosh nese s'ekziston ose ID-ja i perket nje DVD-je
     */
    Optional<Book> findById(String id);

    /** @return te gjithe librat ne katalog, te lire dhe te zene */
    List<Book> findAll();

    /**
     * Ruan librin e ri ne te dyja tabelat, ne nje transaksion.
     *
     * @param book libri qe ruhet; ID dhe ISBN duhet te jene unike
     * @throws RuntimeException me shkak {@link java.sql.SQLException} (SQLState 23505) per ID/ISBN te dyfishte
     */
    void save(Book book);

    /**
     * Perditeson titullin, kategorine, autorin dhe ISBN-ne. Nuk e prek {@code available} —
     * disponueshmeria ndryshon vetem gjate huazimit, kthimit dhe rezervimit.
     *
     * @param book libri me vlerat e reja (identifikohet me ID)
     * @throws IllegalArgumentException nese libri s'ekziston
     */
    void update(Book book);

    /**
     * Fshin librin. Deshton (foreign key) nese ka huazime ose rezervime ne histori.
     *
     * @param id ID-ja e librit
     */
    void delete(String id);
}
