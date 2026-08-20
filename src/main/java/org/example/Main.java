package org.example;

import org.example.dao.*;
import org.example.model.*;
import org.example.service.*;

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

        // --- Test LoanService (transaksional, me FOR UPDATE) ---
        LoanService loanService = new LoanService(memberDAO, bookDAO, dvdDAO);

        // 1. Kontrollo gjendjen fillestare te B002
        Book b002Before = bookDAO.findById("B002").orElseThrow();
        System.out.println("B002 disponueshem para huazimit: " + b002Before.isAvailable()); // pritet true

        // 2. Huazo B002 per M002
        Loan svcLoan = loanService.borrowItem("M002", "B002");
        System.out.println("Huazim i ri, id=" + svcLoan.getId() + " dueDate=" + svcLoan.getDueDate());

        Book b002After = bookDAO.findById("B002").orElseThrow();
        System.out.println("B002 disponueshem pas huazimit: " + b002After.isAvailable()); // pritet false

        // 3. Provo te huazosh B002 sërish (duhet deshtuar — s'eshte disponueshem)
        try {
            loanService.borrowItem("M001", "B002");
            System.out.println("GABIM: duhej te hidhte exception!");
        } catch (IllegalStateException e) {
            System.out.println("Pritet: " + e.getMessage());
        }

        // 4. Ktheje B002
        loanService.returnItem(svcLoan.getId());
        Book b002AfterReturn = bookDAO.findById("B002").orElseThrow();
        System.out.println("B002 disponueshem pas kthimit: " + b002AfterReturn.isAvailable()); // pritet true

        // --- Test FIFO "ready for pickup" (LoanService rishkruar) ---
        ReservationDAO reservationDAO3 = new ReservationDAOImpl(memberDAO, bookDAO, dvdDAO);

        // 5. M001 huazon B002 (eshte e lire, sapo u kthye me lart)
        Loan fifoLoan = loanService.borrowItem("M001", "B002");
        System.out.println("FIFO test - M001 huazoi B002, loanId=" + fifoLoan.getId());

        // 6. M002 rezervon B002 (eshte e zene nga M001)
        Member m002 = memberDAO.findById("M002").orElseThrow();
        Book b002ForReservation = bookDAO.findById("B002").orElseThrow();
        Reservation fifoReservation = new Reservation(m002, b002ForReservation, LocalDate.now());
        reservationDAO3.save(fifoReservation);
        System.out.println("M002 rezervoi B002, reservationId=" + fifoReservation.getId());

        // 7. M001 kthen B002 — pritet qe rezervimi i M002 te behet ready_for_pickup,
        // dhe B002 te MBETET jo-disponueshem (mbahet per M002, jo per te tjeret)
        loanService.returnItem(fifoLoan.getId());
        Book b002AfterFifoReturn = bookDAO.findById("B002").orElseThrow();
        System.out.println("B002 disponueshem pas kthimit (me radhe): " + b002AfterFifoReturn.isAvailable()); // pritet false

        Reservation fifoReservationAfter = reservationDAO3.findById(fifoReservation.getId()).orElseThrow();
        System.out.println("Rezervimi i M002, readyForPickup=" + fifoReservationAfter.isReadyForPickup()
                + " fulfilled=" + fifoReservationAfter.isFulfilled()); // pritet true / false

        // 8. M001 provon te rihuazoje B002 — duhet DESHTUAR (eshte mbajtur per M002)
        try {
            loanService.borrowItem("M001", "B002");
            System.out.println("GABIM: duhej te hidhte exception!");
        } catch (IllegalStateException e) {
            System.out.println("Pritet: " + e.getMessage());
        }

        // 9. M002 huazon B002 — duhet te KALOJE, dhe rezervimi behet fulfilled
        Loan fifoLoanM002 = loanService.borrowItem("M002", "B002");
        System.out.println("M002 huazoi B002 me sukses, loanId=" + fifoLoanM002.getId());

        Reservation fifoReservationFinal = reservationDAO3.findById(fifoReservation.getId()).orElseThrow();
        System.out.println("Rezervimi i M002 pas huazimit, readyForPickup=" + fifoReservationFinal.isReadyForPickup()
                + " fulfilled=" + fifoReservationFinal.isFulfilled()); // pritet false / true

        // Pastrim — ktheje B002 qe DB te mbetet ne gjendje te qete
        loanService.returnItem(fifoLoanM002.getId());

        // --- Test FineService (sinkronizim me members.unpaid_fines) ---
        FineService fineService = new FineService();

        Loan overdueLoanForFineService = loanDAO.findById(1).orElseThrow();
        Member m001Before = memberDAO.findById("M001").orElseThrow();
        System.out.println("M001 unpaidFees para issue(): " + m001Before.getUnpaidFees());

        Fine newFine = new Fine(overdueLoanForFineService, LocalDate.now());
        fineService.issue(newFine);
        System.out.println("Fine e re, id=" + newFine.getId() + " amount=" + newFine.getAmount());

        Member m001After = memberDAO.findById("M001").orElseThrow();
        System.out.println("M001 unpaidFees pas issue(): " + m001After.getUnpaidFees()); // pritet + amount

        fineService.markPaid(newFine.getId());
        Member m001AfterPaid = memberDAO.findById("M001").orElseThrow();
        System.out.println("M001 unpaidFees pas markPaid(): " + m001AfterPaid.getUnpaidFees()); // pritet mbrapa te vlera fillestare
    }

}