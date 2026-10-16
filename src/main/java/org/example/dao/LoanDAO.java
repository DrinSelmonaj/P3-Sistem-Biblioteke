package org.example.dao;

import org.example.model.Loan;

import java.util.List;
import java.util.Optional;

/**
 * Qasja ne databaze per huazimet. Huazimi dhe kthimi me rregullat e tyre
 * (disponueshmeria, gjobat, radha e rezervimeve) behen nga
 * {@link org.example.service.LoanService} brenda transaksioneve te veta.
 */
public interface LoanDAO {

    /**
     * @param id ID-ja e huazimit
     * @return huazimi me anetarin dhe artikullin e plote, ose bosh
     */
    Optional<Loan> findById(int id);

    /** @return te gjitha huazimet, aktive dhe te kthyera */
    List<Loan> findAll();

    /**
     * Ruan huazimin dhe i vendos ID-ne e gjeneruar ({@code RETURNING id}).
     *
     * @param loan huazimi i ri
     */
    void save(Loan loan);

    /**
     * Shenon huazimin si te kthyer (pa gjoba dhe pa ndryshim disponueshmerie).
     *
     * @param loanId     ID-ja e huazimit
     * @param returnDate data e kthimit
     */
    void markReturned(int loanId, java.time.LocalDate returnDate);

    /**
     * Huazimet aktive ({@code return_date IS NULL}) te nje anetari — pa u mbeshtetur te
     * {@code Member.currentLoans}, qe s'populohet nga DB (do te kerkonte varesi te
     * kryqezuar Member&lt;-&gt;Loan).
     *
     * @param memberId username-i i anetarit
     * @return huazimet e pakthyera
     */
    List<Loan> findActiveByMember(String memberId);
}
