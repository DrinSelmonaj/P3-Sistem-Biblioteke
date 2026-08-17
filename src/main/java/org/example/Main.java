package org.example;

import org.example.dao.*;
import org.example.model.*;

import java.time.LocalDate;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Dependency Injection manual - wiring i DAO-ve
        MemberDAO memberDAO = new MemberDAOImpl();
        MemberDAO memberDAO2 = new MemberDAOImpl();

        MemberDAOImpl memberDAOImpl1 = new MemberDAOImpl();
        BookDAO bookDAO = new BookDAOImpl();
        DVDDAO dvdDAO = new DVDDAOImpl();
        LoanDAO loanDAO = new LoanDAOImpl(memberDAO, bookDAO, dvdDAO);

        // Testo save() - krijo nje huazim te ri
        var member = memberDAO.findById("M001").orElseThrow();
        var book = bookDAO.findById("B001").orElseThrow();

        Loan newLoan = new Loan(member, book, LocalDate.now());
        loanDAO.save(newLoan);
        System.out.println("Huazimi u ruajt me sukses.");

        // Testo findAll() - lexo te gjitha huazimet, verifiko resolveItem() dhe mapRowToLoan()
        List<Loan> allLoans = loanDAO.findAll();
        System.out.println("Numri i huazimeve: " + allLoans.size());

        for (Loan loan : allLoans) {
            System.out.println("Anetari: " + loan.getMember().getName() +
                    " | Artikulli: " + loan.getItem().getTitle() +
                    " | Afati: " + loan.getDueDate() +
                    " | I vonuar: " + loan.isOverdue());
        }
        // --- Test ReservationDAO ---
        ReservationDAO reservationDAO = new ReservationDAOImpl(memberDAO, bookDAO, dvdDAO);

// Marrim entitetet reale nga DB, jo t'i krijojmë manualisht —
// keshtu shmangim çdo mospërputhje me konstruktorin e Member/Book
        Member member2 = memberDAO.findById("M002")
                .orElseThrow(() -> new RuntimeException("M002 nuk u gjet"));
        LibraryItem book1 = bookDAO.findById("B001")
                .orElseThrow(() -> new RuntimeException("B001 nuk u gjet"));

        Reservation res = new Reservation(member2, book1, LocalDate.now());
        System.out.println("Para save(), id: " + res.getId()); // pritet null

        reservationDAO.save(res);
        System.out.println("Pas save(), id: " + res.getId()); // pritet nje numer, jo null

        List<Reservation> queue = reservationDAO.findQueueForItem("B001");
        System.out.println("Radha per B001, madhesia: " + queue.size());
        for (Reservation r : queue) {
            System.out.println("  id=" + r.getId() + " member=" + r.getMember().getId()
                    + " date=" + r.getReservationDate() + " fulfilled=" + r.isFulfilled());
        }

        // --- Test FineDAO ---
        FineDAO fineDAO = new FineDAOImpl(loanDAO);

        Loan overdueLoan = loanDAO.findById(1)
                .orElseThrow(() -> new RuntimeException("Loan #1 nuk u gjet"));
        System.out.println("Dite vonese: " + overdueLoan.getDaysOverdue()); // pritet 10

        Fine fine = new Fine(overdueLoan, LocalDate.now());
        System.out.println("Shuma e llogaritur: " + fine.getAmount()); // pritet 5.0

        fineDAO.save(fine);
        System.out.println("Pas save(), id: " + fine.getId()); // pritet numer, jo null

        Fine fetched = fineDAO.findByLoanId(1)
                .orElseThrow(() -> new RuntimeException("Fine per loan 1 nuk u gjet"));
        System.out.println("Fine rimarrur nga DB, amount=" + fetched.getAmount()
                + " paid=" + fetched.isPaid());

        fineDAO.markPaid(fetched.getId());
        System.out.println("markPaid() u thirr per id=" + fetched.getId());
    }

}