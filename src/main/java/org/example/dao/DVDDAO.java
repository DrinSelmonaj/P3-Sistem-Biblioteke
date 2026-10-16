package org.example.dao;

import org.example.model.DVD;

import java.util.List;
import java.util.Optional;

/**
 * Qasja ne databaze per DVD-te ({@code library_items} + {@code dvds}).
 * Rregullat jane te njejta si te {@link BookDAO}.
 */
public interface DVDDAO {

    /**
     * @param id ID-ja e artikullit (p.sh. "D001")
     * @return DVD-ja, ose bosh nese s'ekziston ose ID-ja i perket nje libri
     */
    Optional<DVD> findById(String id);

    /** @return te gjitha DVD-te ne katalog */
    List<DVD> findAll();

    /**
     * Ruan DVD-ne e re ne te dyja tabelat, ne nje transaksion.
     *
     * @param dvd DVD-ja qe ruhet; ID-ja duhet te jete unike
     */
    void save(DVD dvd);

    /**
     * Perditeson titullin, kategorine dhe kohezgjatjen; s'e prek {@code available}.
     *
     * @param dvd DVD-ja me vlerat e reja (identifikohet me ID)
     * @throws IllegalArgumentException nese DVD-ja s'ekziston
     */
    void update(DVD dvd);

    /**
     * Fshin DVD-ne. Deshton (foreign key) nese ka huazime ose rezervime ne histori.
     *
     * @param id ID-ja e DVD-se
     */
    void delete(String id);
}
