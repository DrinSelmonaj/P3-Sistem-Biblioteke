package org.example.service;

import org.example.TestData;
import org.example.dao.*;
import org.example.model.Fine;
import org.example.model.Librarian;
import org.example.model.Loan;
import org.example.model.Member;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

// Test integrimi (kerkon Postgres real) — gjoba automatike ne kthim te vonuar.
// Tarifa: 0.50 €/dite (Fine.amountForDaysOverdue).
class OverdueFineTest {

    private final MemberDAO memberDAO = new MemberDAOImpl();
    private final BookDAO bookDAO = new BookDAOImpl();
    private final DVDDAO dvdDAO = new DVDDAOImpl();
    private final LoanDAO loanDAO = new LoanDAOImpl(memberDAO, bookDAO, dvdDAO);
    private final LibrarianDAO librarianDAO = new LibrarianDAOImpl();
    private final LoanService loanService = new LoanService(memberDAO, bookDAO, dvdDAO);
    private final FineService fineService = new FineService(new FineDAOImpl(loanDAO));

    private TestData data;

    @BeforeEach
    void setUp() {
        data = new TestData();
    }

    @AfterEach
    void tearDown() throws Exception {
        data.close();
    }

    @Test
    void onTimeReturnCreatesNoFine() throws Exception {
        Member member = memberDAO.findById(data.createMember()).orElseThrow();
        Loan loan = loanService.borrowItem(member, member.getId(), data.createBook(true));

        assertEquals(0.0, loanService.returnItem(member, loan.getId()));
        assertEquals(0.0, data.unpaidFines(member.getId()));
    }

    @Test
    void lateReturnCreatesFineAndBlocksNewLoans() throws Exception {
        Member member = memberDAO.findById(data.createMember()).orElseThrow();
        Loan loan = loanService.borrowItem(member, member.getId(), data.createBook(true));
        data.makeOverdue(loan.getId(), 4);

        assertEquals(2.00, loanService.returnItem(member, loan.getId()), 0.001);
        assertEquals(2.00, data.unpaidFines(member.getId()), 0.001);

        // Gjoba e papaguar → huazimi i radhes refuzohet (rregulli ekzistues i LoanService)
        String otherBook = data.createBook(true);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> loanService.borrowItem(member, member.getId(), otherBook));
        assertEquals("Anetari ka gjoba te papaguara.", e.getMessage());
    }

    @Test
    void manualFineBeforeReturnIsNotChargedTwice() throws Exception {
        Member member = memberDAO.findById(data.createMember()).orElseThrow();
        Librarian librarian = librarianDAO.findById(data.createLibrarian()).orElseThrow();
        Loan loan = loanService.borrowItem(member, member.getId(), data.createBook(true));

        // Bibliotekari gjobit huazimin aktiv pas 2 ditesh vonese (1.00 €)
        data.makeOverdue(loan.getId(), 2);
        fineService.issue(librarian, new Fine(loanDAO.findById(loan.getId()).orElseThrow(), LocalDate.now()));
        assertEquals(1.00, data.unpaidFines(member.getId()), 0.001);

        // Kthimi pas 4 ditesh vonese gjithsej → ngarkohet vetem diferenca (1.00 €)
        data.makeOverdue(loan.getId(), 4);
        assertEquals(1.00, loanService.returnItem(member, loan.getId()), 0.001);
        assertEquals(2.00, data.unpaidFines(member.getId()), 0.001);
    }

    @Test
    void manualFineAfterAutomaticFineIsRejected() throws Exception {
        Member member = memberDAO.findById(data.createMember()).orElseThrow();
        Librarian librarian = librarianDAO.findById(data.createLibrarian()).orElseThrow();
        Loan loan = loanService.borrowItem(member, member.getId(), data.createBook(true));
        data.makeOverdue(loan.getId(), 3);
        loanService.returnItem(member, loan.getId());

        Fine duplicate = new Fine(loanDAO.findById(loan.getId()).orElseThrow(), LocalDate.now());
        assertThrows(IllegalStateException.class, () -> fineService.issue(librarian, duplicate));
        assertEquals(1.50, data.unpaidFines(member.getId()), 0.001);
    }
}
