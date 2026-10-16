package org.example.cli;

import org.example.dao.BookDAO;
import org.example.dao.DVDDAO;
import org.example.dao.LoanDAO;
import org.example.dao.ReservationDAO;
import org.example.model.Fine;
import org.example.model.Loan;
import org.example.model.Person;
import org.example.model.Reservation;
import org.example.service.FineService;
import org.example.service.LoanService;
import org.example.service.ReservationService;

import java.util.List;

/** Menuja e anetarit: huazimet, gjobat dhe rezervimet e veta. */
public class MemberMenu {

    private final ConsoleIO io;
    private final LoanService loanService;
    private final FineService fineService;
    private final ReservationService reservationService;
    private final LoanDAO loanDAO;
    private final ReservationDAO reservationDAO;
    private final BookDAO bookDAO;
    private final DVDDAO dvdDAO;

    /**
     * @param io                 input-i nga terminali
     * @param loanService        huazimi dhe kthimi
     * @param fineService        gjobat
     * @param reservationService rezervimet
     * @param loanDAO            leximi i huazimeve aktive
     * @param reservationDAO     leximi i rezervimeve aktive
     * @param bookDAO            katalogu i librave
     * @param dvdDAO             katalogu i DVD-ve
     */
    public MemberMenu(ConsoleIO io, LoanService loanService, FineService fineService,
                      ReservationService reservationService, LoanDAO loanDAO, ReservationDAO reservationDAO,
                      BookDAO bookDAO, DVDDAO dvdDAO) {
        this.io = io;
        this.loanService = loanService;
        this.fineService = fineService;
        this.reservationService = reservationService;
        this.loanDAO = loanDAO;
        this.reservationDAO = reservationDAO;
        this.bookDAO = bookDAO;
        this.dvdDAO = dvdDAO;
    }

    /** @param actor anetari i kycur; menuja perseritet derisa te zgjedhe "Dil" */
    public void run(Person actor) {
        boolean running = true;
        while (running) {
            System.out.println("\n--- Menuja e Anetarit (" + actor.getName() + ") ---");
            System.out.println("1. Shiko huazimet e mia aktive");
            System.out.println("2. Huazo artikull");
            System.out.println("3. Kthe artikull");
            System.out.println("4. Shiko gjobat e mia");
            System.out.println("5. Paguaj nje gjobe");
            System.out.println("6. Shiko rezervimet e mia");
            System.out.println("7. Rezervo nje artikull te zene");
            System.out.println("8. Anulo nje rezervim");
            System.out.println("9. Dil");
            System.out.print("Zgjedh: ");
            String choice = io.nextLine().trim();

            try {
                if (choice.equals("1")) {
                    List<Loan> loans = loanDAO.findActiveByMember(actor.getId());
                    if (loans.isEmpty()) {
                        System.out.println("S'ke huazime aktive.");
                    } else {
                        for (Loan loan : loans) {
                            System.out.println("  #" + loan.getId() + " " + loan.getItem().getTitle()
                                    + " | Afati: " + loan.getDueDate()
                                    + " | I vonuar: " + loan.isOverdue());
                        }
                    }

                } else if (choice.equals("2")) {
                    ConsoleViews.printCatalog(bookDAO, dvdDAO, true);
                    System.out.print("ID e artikullit: ");
                    String itemId = io.nextLine().trim();
                    Loan loan = loanService.borrowItem(actor, actor.getId(), itemId);
                    System.out.println("Huazuar me sukses. Afati: " + loan.getDueDate());

                } else if (choice.equals("3")) {
                    List<Loan> activeLoans = loanDAO.findActiveByMember(actor.getId());
                    if (activeLoans.isEmpty()) {
                        System.out.println("S'ke huazime aktive per te kthyer.");
                    } else {
                        System.out.println("--- Huazimet e tua aktive ---");
                        for (Loan loan : activeLoans) {
                            System.out.println("  #" + loan.getId() + " " + loan.getItem().getTitle()
                                    + " | Afati: " + loan.getDueDate());
                        }
                        System.out.print("ID e huazimit qe do kthesh: ");
                        int loanId = Integer.parseInt(io.nextLine().trim());
                        double fine = loanService.returnItem(actor, loanId);
                        System.out.println("Artikulli u kthye me sukses.");
                        ConsoleViews.printOverdueFine(fine);
                    }

                } else if (choice.equals("4")) {
                    List<Fine> fines = fineService.getFinesForMember(actor, actor.getId());
                    if (fines.isEmpty()) {
                        System.out.println("S'ke gjoba.");
                    } else {
                        for (Fine fine : fines) {
                            String statusi = fine.isPaid() ? "Paguar me " + fine.getPaidDate() : "E papaguar";
                            System.out.println("  #" + fine.getId() + " Shuma: " + fine.getAmount()
                                    + " | " + statusi);
                        }
                    }

                } else if (choice.equals("5")) {
                    List<Fine> unpaidFines = fineService.getFinesForMember(actor, actor.getId()).stream()
                            .filter(f -> !f.isPaid())
                            .toList();
                    if (unpaidFines.isEmpty()) {
                        System.out.println("S'ke gjoba te papaguara.");
                    } else {
                        System.out.println("--- Gjobat e tua te papaguara ---");
                        for (Fine fine : unpaidFines) {
                            System.out.println("  #" + fine.getId() + " Shuma: " + fine.getAmount());
                        }
                        System.out.print("ID e gjobes qe do paguash: ");
                        int fineId = Integer.parseInt(io.nextLine().trim());
                        fineService.payFine(actor, fineId);
                        System.out.println("Gjoba u pagua me sukses.");
                    }

                } else if (choice.equals("6")) {
                    printMyReservations(actor.getId());

                } else if (choice.equals("7")) {
                    ConsoleViews.printCatalog(bookDAO, dvdDAO, false);
                    System.out.print("ID e artikullit qe do rezervosh: ");
                    String itemId = io.nextLine().trim();
                    ReservationService.ReservationResult result =
                            reservationService.reserveItem(actor, actor.getId(), itemId);
                    System.out.println("Rezervimi #" + result.reservationId() + " u krijua. Je i "
                            + result.queuePosition() + "-ti ne radhe — do te njoftohesh kur artikulli te jete gati.");

                } else if (choice.equals("8")) {
                    List<Reservation> reservations = printMyReservations(actor.getId());
                    if (!reservations.isEmpty()) {
                        System.out.print("ID e rezervimit qe do anulosh: ");
                        int reservationId = Integer.parseInt(io.nextLine().trim());
                        reservationService.cancelReservation(actor, reservationId);
                        System.out.println("Rezervimi u anulua.");
                    }

                } else if (choice.equals("9")) {
                    running = false;

                } else {
                    System.out.println("Zgjedhje e panjohur.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Gabim: duhej nje numer.");
            } catch (Exception e) {
                System.out.println("Gabim: " + e.getMessage());
            }
        }
    }

    // Ndihmes i perbashket per opsionet "6" dhe "8" — shfaq rezervimet aktive te
    // anetarit me status te qarte (gati per marrje / ne prite), qe te dyja opsionet
    // (shiko / anulo) te kene te njejtin format shfaqjeje.
    private List<Reservation> printMyReservations(String memberId) {
        List<Reservation> reservations = reservationDAO.findActiveByMember(memberId);
        if (reservations.isEmpty()) {
            System.out.println("S'ke rezervime aktive.");
        } else {
            System.out.println("--- Rezervimet e tua aktive ---");
            for (Reservation reservation : reservations) {
                String statusi = reservation.isReadyForPickup()
                        ? "GATI PER MARRJE qe nga " + reservation.getReadySince()
                        : "Ne prite ne radhe";
                System.out.println("  #" + reservation.getId() + " " + reservation.getItem().getTitle()
                        + " | " + statusi);
            }
        }
        return reservations;
    }
}
