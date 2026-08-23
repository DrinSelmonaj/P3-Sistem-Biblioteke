package org.example.service;

import org.example.dao.*;
import org.example.model.Book;
import org.example.model.Loan;
import org.example.model.Member;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

// Test integrimi (jo test i paster njesie si DomainModelTest) — LoanService
// perdor DBConnection reale, keshtu qe ky test kerkon Postgres real, ekzekutuar
// (biblioteka DB e konfiguruar sipas config.properties).
//
// PSE ky test ekziston: dëshmon rregullin qendror te Kapitullit 6 — Member s'mund
// te kthej huazimin e nje Member tjeter, edhe nese e thërret LoanService drejtperdrejt
// (jo permes CLI). CLI tani e bllokon kete rrugë ne UI (shfaq vetem huazimet e veta),
// por SecurityException mbetet mburoja e dyte per cdo thirres tjeter te ardhshem.
class LoanServiceAuthorizationTest {

    @Test
    void memberCannotReturnAnotherMembersLoan() {
        MemberDAO memberDAO = new MemberDAOImpl();
        BookDAO bookDAO = new BookDAOImpl();
        DVDDAO dvdDAO = new DVDDAOImpl();
        LoanDAO loanDAO = new LoanDAOImpl(memberDAO, bookDAO, dvdDAO);
        LoanService loanService = new LoanService(memberDAO, bookDAO, dvdDAO);

        Member m001 = memberDAO.findById("M001").orElseThrow(() -> new IllegalStateException("M001 duhet te ekzistoje per kete test."));
        Member m002 = memberDAO.findById("M002").orElseThrow(() -> new IllegalStateException("M002 duhet te ekzistoje per kete test."));

        // Gjej nje liber aktualisht te disponueshem — s'perdorim ID te fiksuar,
        // sepse disponueshmeria varet nga gjendja aktuale e DB-se.
        Book availableBook = bookDAO.findAll().stream()
                .filter(Book::isAvailable)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Duhet te kete te pakten 1 liber te disponueshem per kete test."));

        // M001 huazon librin — kete e kthen vetem M001, jo M002.
        Loan loan = loanService.borrowItem(m001, "M001", availableBook.getId());

        try {
            // Testi qendror: M002 provon te kthej huazimin e M001.
            SecurityException exception = assertThrows(SecurityException.class,
                    () -> loanService.returnItem(m002, loan.getId()));
            assertEquals("Mund te kthesh vetem huazimet e tua.", exception.getMessage());

            // Verifikohet edhe qe huazimi mbetet ende aktiv ne DB (provimi i deshtuar
            // s'duhet te kete ndryshuar asgje — rollback duhet te kete funksionuar).
            Optional<Loan> stillActive = loanDAO.findById(loan.getId());
            assertTrue(stillActive.isPresent());
            assertNull(stillActive.get().getReturnDate());

        } finally {
            // Pastrim — M001 (pronari real) e kthen librin, qe DB te mbetet ne
            // gjendje te qete pas testit, pavaresisht nga rezultati i assertimeve.
            loanService.returnItem(m001, loan.getId());
        }
    }
}