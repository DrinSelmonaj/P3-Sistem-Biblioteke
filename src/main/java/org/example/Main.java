package org.example;

import org.example.dao.*;
import org.example.model.LibraryItem;
import org.example.model.Loan;
import org.example.model.Member;
import org.example.model.Reservation;

import java.time.LocalDate;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Dependency Injection manual - wiring i DAO-ve
        MemberDAO memberDAO = new MemberDAOImpl();
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
    }

}