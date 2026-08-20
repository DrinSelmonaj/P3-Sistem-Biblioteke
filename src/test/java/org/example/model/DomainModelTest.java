package org.example.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainModelTest {
    @Test
    void bookLoanHasTwentyOneDayDueDate() {
        Book book = new Book("B1", "Test", Category.SCIENCE, "Author", "ISBN");
        Loan loan = new Loan(new Member("M1", "Name", "mail", "phone"), book, LocalDate.of(2026, 1, 1));

        assertEquals(LocalDate.of(2026, 1, 22), loan.getDueDate());
    }

    @Test
    void returnedLateLoanCalculatesDaysOverdue() {
        Book book = new Book("B1", "Test", Category.SCIENCE, "Author", "ISBN");
        Loan loan = new Loan(new Member("M1", "Name", "mail", "phone"), book, LocalDate.of(2026, 1, 1));
        loan.markReturned(LocalDate.of(2026, 1, 25));

        assertTrue(loan.isOverdue());
        assertEquals(3, loan.getDaysOverdue());
    }

    @Test
    void finePaymentCannotMakeBalanceNegative() {
        Member member = new Member("M1", "Name", "mail", "phone");
        member.addFine(2.0);
        member.payFines(5.0);

        assertFalse(member.isBlocked());
        assertEquals(0.0, member.getUnpaidFees());
    }
}