package org.example.dao;

import org.example.model.Fine;

import java.util.List;
import java.util.Optional;

/**
 * Qasja ne databaze per gjobat. Krijimi i gjobave dhe pagesa (qe ndryshojne edhe
 * {@code members.unpaid_fines}) behen nga {@link org.example.service.FineService}
 * dhe {@link org.example.service.LoanService}.
 */
public interface FineDAO {

    /**
     * @param id ID-ja e gjobes
     * @return gjoba me huazimin perkates, ose bosh
     */
    Optional<Fine> findById(int id);

    /** @return te gjitha gjobat, te paguara dhe te papaguara */
    List<Fine> findAll();

    /**
     * Ruan gjoben dhe i vendos ID-ne e gjeneruar.
     *
     * @param fine gjoba e re
     */
    void save(Fine fine);

    /**
     * Shenon gjoben si te paguar. Kalohet id si int, jo objekt Fine, sepse Fine s'e mban
     * gjendjen aktuale te DB pas save(), vetem momentin e krijimit.
     *
     * @param fineId ID-ja e gjobes
     */
    void markPaid(int fineId);

    /**
     * @param loanId ID-ja e huazimit
     * @return nje gjobe e lidhur me huazimin, ose bosh nese s'ka asnje
     */
    Optional<Fine> findByLoanId(int loanId);

    /**
     * Gjobat e nje anetari, me te rejat ne fillim — per "gjobat e mia" (Member)
     * dhe per pasqyren e bibliotekarit.
     *
     * @param memberId username-i i anetarit
     * @return gjobat e renditura sipas {@code issued_date DESC}
     */
    List<Fine> findByMemberId(String memberId);
}
