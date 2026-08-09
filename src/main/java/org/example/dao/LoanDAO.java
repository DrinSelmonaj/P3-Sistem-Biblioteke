package org.example.dao;

import org.example.model.Loan;

import java.util.List;
import java.util.Optional;

public interface LoanDAO {
    Optional<Loan> findById(int id);
    List<Loan> findAll();
    void save(Loan loan);
    void markReturned(int loanId, java.time.LocalDate returnDate);
}
