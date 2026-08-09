package org.example.dao;

import org.example.model.Librarian;

import java.util.List;
import java.util.Optional;

public interface LibrarianDAO {
    Optional<Librarian> findById(String id);
    List<Librarian> findAll();
    void save(Librarian librarian);
    void update(Librarian librarian);
    void delete(String id);
}
