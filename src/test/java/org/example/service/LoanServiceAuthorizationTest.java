package org.example.service;

import org.example.TestData;
import org.example.dao.*;
import org.example.model.Loan;
import org.example.model.Member;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

// Test integrimi (jo test i paster njesie si DomainModelTest) — LoanService
// perdor DBConnection reale, keshtu qe ky test kerkon Postgres real.
//
// PSE ky test ekziston: dëshmon rregullin qendror te Kapitullit 6 — Member s'mund
// te kthej huazimin e nje Member tjeter, edhe nese e thërret LoanService drejtperdrejt
// (jo permes CLI). CLI tani e bllokon kete rrugë ne UI (shfaq vetem huazimet e veta),
// por SecurityException mbetet mburoja e dyte per cdo thirres tjeter te ardhshem.
//
// Anetaret dhe libri krijohen nga TestData (jo M001/M002 te DB-se reale) — me pare
// cdo ekzekutim linte nje huazim te ri ne historikun e M001.
class LoanServiceAuthorizationTest {

    @Test
    void memberCannotReturnAnotherMembersLoan() throws Exception {
        MemberDAO memberDAO = new MemberDAOImpl();
        BookDAO bookDAO = new BookDAOImpl();
        DVDDAO dvdDAO = new DVDDAOImpl();
        LoanDAO loanDAO = new LoanDAOImpl(memberDAO, bookDAO, dvdDAO);
        LoanService loanService = new LoanService(memberDAO, bookDAO, dvdDAO);

        try (TestData data = new TestData()) {
            Member owner = memberDAO.findById(data.createMember()).orElseThrow();
            Member other = memberDAO.findById(data.createMember()).orElseThrow();

            // owner huazon librin — kete e kthen vetem owner, jo other.
            Loan loan = loanService.borrowItem(owner, owner.getId(), data.createBook(true));

            // Testi qendror: other provon te kthej huazimin e owner.
            SecurityException exception = assertThrows(SecurityException.class,
                    () -> loanService.returnItem(other, loan.getId()));
            assertEquals("Mund te kthesh vetem huazimet e tua.", exception.getMessage());

            // Verifikohet edhe qe huazimi mbetet ende aktiv ne DB (provimi i deshtuar
            // s'duhet te kete ndryshuar asgje — rollback duhet te kete funksionuar).
            Optional<Loan> stillActive = loanDAO.findById(loan.getId());
            assertTrue(stillActive.isPresent());
            assertNull(stillActive.get().getReturnDate());
        } // TestData.close() fshin huazimin, librin dhe anetaret
    }
}
