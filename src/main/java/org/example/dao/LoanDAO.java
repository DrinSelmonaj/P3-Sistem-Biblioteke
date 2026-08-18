package org.example.dao;

import org.example.model.Loan;

import java.util.List;
import java.util.Optional;

public interface LoanDAO {
    Optional<Loan> findById(int id);
    List<Loan> findAll();
    void save(Loan loan);
    void markReturned(int loanId, java.time.LocalDate returnDate);

    // I domosdoshem per LoanService.canBorrowMore() logic: numeron huazimet aktive
    // (return_date IS NULL) te nje anetari, pa u mbeshtetur te Member.currentLoans
    // (qe s'populohet nga DB — do te kerkonte varesi te kryqezuar Member<->Loan).
    List<Loan> findActiveByMember(String memberId);
}