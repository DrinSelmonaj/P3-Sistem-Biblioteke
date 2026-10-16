package org.example;

import org.example.cli.AuthMenu;
import org.example.cli.ConsoleIO;
import org.example.cli.InventoryMenu;
import org.example.cli.LibrarianMenu;
import org.example.cli.MemberMenu;
import org.example.client.OpenLibraryClient;
import org.example.dao.*;
import org.example.model.Person;
import org.example.service.*;

import java.io.Console;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Pika hyrese e aplikacionit CLI: lidh DAO-t, service-t dhe menute, nis scheduler-in e
 * rezervimeve dhe shfaq menune sipas rolit (anetar ose bibliotekar). Logjika e menuve
 * eshte te paketa {@code org.example.cli}.
 */
public class Main {


    // Kapitulli 7 — afati i biznesit (sa dite ka anetari per te marre nje rezervim
    // ready_for_pickup para se t'i kaloje radhes tjeter) eshte i ndare qellimisht
    // nga sa shpesh xhiron job-i qe e zbaton kete rregull. Per demonstrim sot,
    // intervali eshte i shkurter (30 sek); ne "prodhim real" PICKUP_DAYS_ALLOWED
    // do te mbetej i njejte, por intervali do te ishte psh 1 here ne dite.
    private static final int PICKUP_DAYS_ALLOWED = 3;
    private static final int SCHEDULER_INTERVAL_SECONDS = 30;

    /** @param args s'perdoren */
    public static void main(String[] args) {
        // --- Wiring i DAO-ve ---
        MemberDAO memberDAO = new MemberDAOImpl();
        BookDAO bookDAO = new BookDAOImpl();
        DVDDAO dvdDAO = new DVDDAOImpl();
        LoanDAO loanDAO = new LoanDAOImpl(memberDAO, bookDAO, dvdDAO);
        FineDAO fineDAO = new FineDAOImpl(loanDAO);
        LibrarianDAO librarianDAO = new LibrarianDAOImpl();
        ReservationDAO reservationDAO = new ReservationDAOImpl(memberDAO, bookDAO, dvdDAO);

        // --- Wiring i Service-ve ---
        AuthService authService = new AuthService(memberDAO, librarianDAO);
        LoanService loanService = new LoanService(memberDAO, bookDAO, dvdDAO);
        FineService fineService = new FineService(fineDAO);
        ReservationService reservationService = new ReservationService();
        ReportService reportService = new ReportService();
        OpenLibraryClient openLibraryClient = new OpenLibraryClient();
        InventoryService inventoryService = new InventoryService(bookDAO, dvdDAO);

        // --- Scheduler per skadimin e rezervimeve "ready_for_pickup" ---
        // Thread daemon (setDaemon(true)) — kjo eshte pika kyçe: nese do ishte
        // thread normal, JVM do priste per te (dhe scanner.close() s'do mjaftonte
        // per te mbyllur programin, do duhej System.exit()). Thread daemon lejon
        // JVM-ne te mbyllet normalisht kur main() perfundon, edhe nese scheduler-i
        // ende "xhiron" ne sfond.
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "reservation-expiry-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try {
                reservationService.expireStaleReadyPickups(PICKUP_DAYS_ALLOWED);
            } catch (Exception e) {
                System.out.println("[SCHEDULER] Gabim gjate kontrollit te rezervimeve te skaduara: " + e.getMessage());
            }
        }, SCHEDULER_INTERVAL_SECONDS, SCHEDULER_INTERVAL_SECONDS, TimeUnit.SECONDS);

        Scanner scanner = new Scanner(System.in);
        // System.console() eshte i disponueshem vetem ne terminal te vertete
        // (Terminal.app) — kur xhirohet nga IntelliJ Run panel kthen null,
        // sepse IntelliJ s'ofron nje TTY te vertete. Perdoret per te fshehur
        // fjalekalimin me **** vetem kur eshte e mundur teknikisht.
        Console console = System.console();
        ConsoleIO io = new ConsoleIO(scanner, console);

        System.out.println("=== Miresevini ne Sistemin e Bibliotekes ===\n");

        Person loggedInUser = new AuthMenu(io, authService).run();

        // --- Dallimi i menuse sipas rolit — perdor te njejten metode polimorfike
        // qe perdoret per autorizim ne LoanService/FineService (canManageInventory()).
        if (loggedInUser.canManageInventory()) {
            InventoryMenu inventoryMenu = new InventoryMenu(io, inventoryService, bookDAO, dvdDAO);
            new LibrarianMenu(io, loanService, fineService, reportService, openLibraryClient,
                    inventoryService, authService, inventoryMenu, loanDAO, bookDAO, dvdDAO).run(loggedInUser);
        } else {
            new MemberMenu(io, loanService, fineService, reservationService,
                    loanDAO, reservationDAO, bookDAO, dvdDAO).run(loggedInUser);
        }

        scanner.close();
        scheduler.shutdownNow();
        System.out.println("Mirupafshim!");
    }
}
