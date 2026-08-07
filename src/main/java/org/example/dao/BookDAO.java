package org.example.dao;

import org.example.model.Book;
import java.util.List;
import java.util.Optional;

public interface BookDAO {
    Optional<Book> findById(String id);
    List<Book> findAll();
    void save(Book book);
    void update(Book book);
    void delete(String id);
}
