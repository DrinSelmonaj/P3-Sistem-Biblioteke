package org.example.dao;

import org.example.model.DVD;

import java.util.List;
import java.util.Optional;

public interface DVDDAO {
    Optional<DVD> findById(String id);
    List<DVD> findAll();
    void save(DVD dvd);
    void update(DVD dvd);
    void delete(String id);
}
