package org.example.dao;

import org.example.model.Member;
import java.util.List;
import java.util.Optional;

/** Qasja ne databaze per anetaret ({@code persons} + {@code members}). */
public interface MemberDAO {

    /**
     * @param id username-i i anetarit
     * @return anetari, ose bosh nese s'ekziston ose personi eshte bibliotekar
     */
    Optional<Member> findById(String id);

    /** @return te gjithe anetaret */
    List<Member> findAll();

    /**
     * Ruan anetarin me nje hash placeholder — llogaria s'mund te kycet derisa te
     * vendoset fjalekalimi. Per regjistrim te plote perdor
     * {@link org.example.service.AuthService#registerMember}.
     *
     * @param member anetari i ri
     */
    void save(Member member);

    /**
     * Perditeson vetem bilancin {@code unpaid_fines} te anetarit.
     *
     * @param member anetari me bilancin e ri
     */
    void update(Member member);

    /**
     * Fshin llogarine e plote (persons, me cascade te members).
     *
     * @param id username-i i anetarit
     */
    void delete(String id);
}
