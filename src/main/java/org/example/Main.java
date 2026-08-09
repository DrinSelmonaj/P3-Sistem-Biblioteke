package org.example;

import org.example.dao.*;
import org.example.model.Loan;
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
    }
}