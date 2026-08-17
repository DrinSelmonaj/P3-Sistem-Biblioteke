package org.example.dao;

import org.example.model.Fine;

import java.util.List;
import java.util.Optional;

public interface FineDAO {

    Optional<Fine> findById(int id);

    List<Fine> findAll();

    void save(Fine fine);

    // Njesoj si markReturned/markFulfilled — kalojme id si int, jo objekt Fine,
    // sepse Fine s'e mban gjendjen "aktuale" te DB pas save(), vetem momentin e krijimit.
    void markPaid(int fineId);

    // I domosdoshem per FineService: para se te krijohet nje gjobe e re per nje loan,
    // duhet kontrolluar nese ekziston tashme nje (shmang gjoba te dyfishta per te njejtin huazim).
    Optional<Fine> findByLoanId(int loanId);
}