package org.example;

import org.example.dao.*;
import org.example.model.*;
import org.example.service.*;
import org.example.client.*;

import java.io.Console;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.sql.SQLException;

public class Main {

    // Kapitulli 7 — afati i biznesit (sa dite ka anetari per te marre nje rezervim
    // ready_for_pickup para se t'i kaloje radhes tjeter) eshte i ndare qellimisht
    // nga sa shpesh xhiron job-i qe e zbaton kete rregull. Per demonstrim sot,
    // intervali eshte i shkurter (30 sek); ne "prodhim real" PICKUP_DAYS_ALLOWED
    // do te mbetej i njejte, por intervali do te ishte psh 1 here ne dite.
    private static final int PICKUP_DAYS_ALLOWED = 3;
    private static final int SCHEDULER_INTERVAL_SECONDS = 30;

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

        System.out.println("=== Miresevini ne Sistemin e Bibliotekes ===\n");

        // --- Meny hyrese — Login ose Sign Up, perseritet derisa perdoruesi te kyçet ---
        Person loggedInUser = null;
        while (loggedInUser == null) {
            System.out.println("Jeni anetar? Shtypni 1 per Login.");
            System.out.println("Deshironi te regjistroheni? Shtypni 2 per Sign Up.");
            System.out.print("Zgjedh: ");
            String entryChoice = scanner.nextLine().trim();

            if (entryChoice.equals("1")) {
                System.out.print("Username: ");
                String id = scanner.nextLine().trim();
                String password = readPassword(scanner, console);

                var result = authService.login(id, password);
                if (result.isPresent()) {
                    loggedInUser = result.get();
                    System.out.println("Mireserdhe, " + loggedInUser.getName() + "!");
                } else {
                    // Mesazh gjenerik qellimisht — s'zbulojme nese ID ekziston fare.
                    System.out.println("Username ose fjalekalim i gabuar. Provo perseri.\n");
                }

            } else if (entryChoice.equals("2")) {
                loggedInUser = signUp(scanner, console, memberDAO, authService);

            } else {
                System.out.println("Zgjedhje e panjohur. Shtyp 1 ose 2.\n");
            }
        }

        // --- Dallimi i menuse sipas rolit — perdor te njejten metode polimorfike
        // qe perdoret per autorizim ne LoanService/FineService (canManageInventory()).
        if (loggedInUser.canManageInventory()) {
            librarianMenu(scanner, loggedInUser, loanService, fineService, reportService, openLibraryClient,
                    inventoryService, loanDAO, bookDAO, dvdDAO);
        } else {
            memberMenu(scanner, loggedInUser, loanService, fineService, reservationService,
                    loanDAO, reservationDAO, bookDAO, dvdDAO);
        }

        scanner.close();
        scheduler.shutdownNow();
        System.out.println("Mirupafshim!");
    }

    // Fsheh fjalekalimin me **** nese xhirohet ne terminal te vertete (console != null).
    // Ne IntelliJ Run panel (console == null) bie mbrapa te Scanner normal, me
    // nje shenim qe fjalekalimi shfaqet plain — kufizim i JVM-se, jo diçka qe
    // mund ta anashkalojme me kod.
    private static String readPassword(Scanner scanner, Console console) {
        if (console != null) {
            char[] passwordChars = console.readPassword("Fjalekalimi: ");
            return new String(passwordChars);
        } else {
            System.out.print("Fjalekalimi (i dukshem - xhiro nga Terminal.app per fshehje): ");
            return scanner.nextLine().trim();
        }
    }

    // Regjistrim i ri per Member — vetem Member mund te regjistrohet vetvetiu;
    // Librarian mbetet i krijuar nga stafi (jashte ketij flow-i publik), sepse
    // eshte llogari pune, jo diçka qe cilido duhet te mund ta krijoje vetem.
    // Kthen null nese regjistrimi deshton (p.sh. ID e zene tashme), jo exception —
    // qe loop-i hyres thjesht ta ripyese perdoruesin, njesoj si login i gabuar.
    private static Person signUp(Scanner scanner, Console console, MemberDAO memberDAO, AuthService authService) {
        System.out.println("\n--- Regjistrim Anetari i Ri ---");
        System.out.print("Username (do te perdoret per login): ");
        String id = scanner.nextLine().trim();
        System.out.print("Emri: ");
        String name = scanner.nextLine().trim();
        System.out.print("Email: ");
        String email = scanner.nextLine().trim();
        System.out.print("Telefoni: ");
        String phone = scanner.nextLine().trim();
        String password = readPassword(scanner, console);

        try {
            Member newMember = new Member(id, name, email, phone);
            memberDAO.save(newMember);
            authService.setPassword(id, password);
            System.out.println("Regjistrimi u krye me sukses. Mireserdhe, " + name + "!\n");
            return newMember;
        } catch (Exception e) {
            System.out.println("Gabim gjate regjistrimit: " + e.getMessage() + "\n");
            return null;
        }
    }

    // Listohen artikujt (ID + titull) — pa kete, anetari do te duhej te dinte
    // perpara ID-te e librave qe s'i ka pare kurre, gje qe s'ka logjike per nje
    // perdorues real. available=true per huazim, false per rezervim (vetem
    // artikujt e zene mund te rezervohen).
    private static void printCatalog(BookDAO bookDAO, DVDDAO dvdDAO, boolean available) {
        System.out.println(available ? "--- Artikuj te disponueshem ---" : "--- Artikuj te zene (mund te rezervohen) ---");
        List<Book> books = bookDAO.findAll();
        List<DVD> dvds = dvdDAO.findAll();

        boolean anyListed = false;
        for (Book book : books) {
            if (book.isAvailable() == available) {
                System.out.println("  " + book.getId() + " — " + book.getTitle() + " (Liber)");
                anyListed = true;
            }
        }
        for (DVD dvd : dvds) {
            if (dvd.isAvailable() == available) {
                System.out.println("  " + dvd.getId() + " — " + dvd.getTitle() + " (DVD)");
                anyListed = true;
            }
        }
        if (!anyListed) {
            System.out.println(available ? "  (asnje artikull i disponueshem momentalisht)"
                                         : "  (asnje artikull i zene — te gjithe mund te huazohen direkt)");
        }
    }
    // Printon te tre raportet njeheresh — permbledhje + me te huazuarit + gjobat.
    private static void printReports(ReportService reportService, Person actor) {
        ReportService.SystemSummary summary = reportService.getSystemSummary(actor);
        System.out.println("--- Permbledhje e Sistemit ---");
        System.out.println("Huazime aktive: " + summary.activeLoans());
        System.out.println("Huazime te vonuara: " + summary.overdueLoans());
        System.out.println("Gjoba totale te papaguara: " + summary.totalUnpaidFines());
        System.out.println("Rezervime ne pritje: " + summary.pendingReservations());

        System.out.println("\n--- 5 Artikujt me te Huazuar ---");
        List<ReportService.ItemBorrowCount> mostBorrowed = reportService.getMostBorrowedItems(actor, 5);
        if (mostBorrowed.isEmpty()) {
            System.out.println("S'ka te dhena huazimesh ende.");
        } else {
            for (ReportService.ItemBorrowCount item : mostBorrowed) {
                System.out.println("  " + item.itemId() + " — " + item.title() + " | Huazuar " + item.timesBorrowed() + " here");
            }
        }

        System.out.println("\n--- Anetaret me Gjoba te Papaguara ---");
        List<ReportService.MemberDebt> debts = reportService.getMembersWithUnpaidFines(actor);
        if (debts.isEmpty()) {
            System.out.println("Asnje anetar s'ka gjoba te papaguara.");
        } else {
            for (ReportService.MemberDebt debt : debts) {
                System.out.println("  " + debt.memberId() + " — " + debt.name() + " | Borgji: " + debt.unpaidFines());
            }
        }
    }
    // Kerkon ne Open Library, lejon Librarian te zgjedhe nje rezultat, dhe krijon
    // nje Book te ri lokal me te dhenat e marra + detajet qe mungojne (ID lokale,
    // kategoria, dhe ISBN manual nese Open Library s'e ka dhene).
    private static void importFromOpenLibrary(Scanner scanner, Person actor, OpenLibraryClient client,
                                              InventoryService inventoryService) {
        System.out.print("Kerko liber (titull): ");
        String query = scanner.nextLine().trim();
        if (query.isEmpty()) {
            System.out.println("Shkruaj te pakten nje fjale per kerkim.");
            return;
        }

        List<BookSearchResult> results;
        try {
            results = client.search(query);
        } catch (RuntimeException e) {
            System.out.println("Gabim gjate lidhjes me Open Library: " + e.getMessage());
            return;
        }

        if (results.isEmpty()) {
            System.out.println("S'u gjet asnje rezultat per '" + query + "'.");
            return;
        }

        System.out.println("--- Rezultatet ---");
        for (int i = 0; i < results.size(); i++) {
            BookSearchResult r = results.get(i);
            System.out.println("  " + (i + 1) + ") " + r.title() + " — " + r.author()
                    + (r.firstPublishYear() != null ? " (" + r.firstPublishYear() + ")" : ""));
        }

        System.out.print("Zgjidh numrin (0 per te anuluar): ");
        int choice;
        try {
            choice = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Gabim: duhej nje numer.");
            return;
        }
        if (choice == 0) return;
        if (choice < 1 || choice > results.size()) {
            System.out.println("Numer i pavlefshem.");
            return;
        }

        BookSearchResult selected = results.get(choice - 1);

        String isbn = selected.isbn();
        if (isbn == null) {
            // books.isbn eshte NOT NULL UNIQUE ne skeme — Open Library s'e ka
            // dhene per kete rezultat, duhet plotesuar manualisht.
            System.out.print("Open Library s'ka ISBN per kete liber. Fute ISBN manualisht: ");
            isbn = scanner.nextLine().trim();
        }
        System.out.print("ID lokale per librin (p.sh. B020): ");
        String id = scanner.nextLine().trim();

        Category category = readCategory(scanner, null);
        if (category == null) return;

        // Validimi (ID/ISBN bosh, ISBN e pavlefshme, dublikatat) behet nga InventoryService —
        // i njejti rregull per importin dhe per shtimin manual.
        try {
            Book book = inventoryService.addBook(actor, id, selected.title(), category, selected.author(), isbn);
            System.out.println("Libri u importua me sukses: " + book.getId() + " — " + book.getTitle());
        } catch (IllegalArgumentException e) {
            System.out.println("Importi u anulua: " + e.getMessage());
        }
    }

    // Shfaq kategorite e numeruara dhe kthen zgjedhjen. `current` != null → Enter
    // e mban kategorine aktuale (perdoret gjate ndryshimit te artikullit).
    // Kthen null nese zgjedhja eshte e pavlefshme (thirresi e anulon veprimin).
    private static Category readCategory(Scanner scanner, Category current) {
        System.out.println("--- Kategoria ---");
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            System.out.println("  " + (i + 1) + ") " + categories[i].getDisplayName());
        }
        System.out.print(current == null ? "Zgjidh kategorine: "
                : "Zgjidh kategorine [Enter = " + current.getDisplayName() + "]: ");
        String input = scanner.nextLine().trim();
        if (input.isEmpty() && current != null) return current;
        try {
            int choice = Integer.parseInt(input);
            if (choice >= 1 && choice <= categories.length) return categories[choice - 1];
        } catch (NumberFormatException ignored) {
            // trajtohet njesoj si numer jashte kufijve, me poshte
        }
        System.out.println("Kategori e pavlefshme. Veprimi u anulua.");
        return null;
    }


    // I perbashket per kthimin nga anetari dhe nga bibliotekari — gjoba krijohet
    // automatikisht nga LoanService.returnItem(), ketu vetem njoftohet.
    private static void printOverdueFine(double fineAmount) {
        if (fineAmount > 0) {
            System.out.printf("Kthim me vonese — u krijua gjobe prej %.2f €. Huazimet e reja bllokohen derisa te paguhet.%n", fineAmount);
        }
    }

    // Ndihmes i perbashket per opsionet "6" dhe "8" — shfaq rezervimet aktive te
    // anetarit me status te qarte (gati per marrje / ne prite), qe te dyja opsionet
    // (shiko / anulo) te kene te njejtin format shfaqjeje.
    private static List<Reservation> printMyReservations(ReservationDAO reservationDAO, String memberId) {
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

    private static void memberMenu(Scanner scanner, Person actor, LoanService loanService,
                                   FineService fineService, ReservationService reservationService,
                                   LoanDAO loanDAO, ReservationDAO reservationDAO,
                                   BookDAO bookDAO, DVDDAO dvdDAO) {
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
            String choice = scanner.nextLine().trim();

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
                    printCatalog(bookDAO, dvdDAO, true);
                    System.out.print("ID e artikullit: ");
                    String itemId = scanner.nextLine().trim();
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
                        int loanId = Integer.parseInt(scanner.nextLine().trim());
                        double fine = loanService.returnItem(actor, loanId);
                        System.out.println("Artikulli u kthye me sukses.");
                        printOverdueFine(fine);
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
                        int fineId = Integer.parseInt(scanner.nextLine().trim());
                        fineService.payFine(actor, fineId);
                        System.out.println("Gjoba u pagua me sukses.");
                    }

                } else if (choice.equals("6")) {
                    printMyReservations(reservationDAO, actor.getId());

                } else if (choice.equals("7")) {
                    printCatalog(bookDAO, dvdDAO, false);
                    System.out.print("ID e artikullit qe do rezervosh: ");
                    String itemId = scanner.nextLine().trim();
                    ReservationService.ReservationResult result =
                            reservationService.reserveItem(actor, actor.getId(), itemId);
                    System.out.println("Rezervimi #" + result.reservationId() + " u krijua. Je i "
                            + result.queuePosition() + "-ti ne radhe — do te njoftohesh kur artikulli te jete gati.");

                } else if (choice.equals("8")) {
                    List<Reservation> reservations = printMyReservations(reservationDAO, actor.getId());
                    if (!reservations.isEmpty()) {
                        System.out.print("ID e rezervimit qe do anulosh: ");
                        int reservationId = Integer.parseInt(scanner.nextLine().trim());
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

    private static void librarianMenu(Scanner scanner, Person actor, LoanService loanService,
                                      FineService fineService, ReportService reportService,
                                      OpenLibraryClient openLibraryClient, InventoryService inventoryService,
                                      LoanDAO loanDAO, BookDAO bookDAO, DVDDAO dvdDAO) {
        boolean running = true;
        while (running) {
            System.out.println("\n--- Menuja e Librarianit (" + actor.getName() + ") ---");
            System.out.println("1. Regjistro huazim per nje anetar");
            System.out.println("2. Regjistro kthim");
            System.out.println("3. Krijo gjobe per nje huazim te vonuar");
            System.out.println("4. Shiko historine e plote te pagesave");
            System.out.println("5. Raporte");
            System.out.println("6. Importo liber nga Open Library");
            System.out.println("7. Menaxho inventarin (shto / ndrysho / fshi)");
            System.out.println("8. Dil");
            System.out.print("Zgjedh: ");
            String choice = scanner.nextLine().trim();

            try {
                if (choice.equals("1")) {
                    System.out.print("ID e anetarit: ");
                    String memberId = scanner.nextLine().trim();
                    printCatalog(bookDAO, dvdDAO, true);
                    System.out.print("ID e artikullit: ");
                    String itemId = scanner.nextLine().trim();
                    Loan loan = loanService.borrowItem(actor, memberId, itemId);
                    System.out.println("Huazim i regjistruar, id=" + loan.getId() + " afati=" + loan.getDueDate());

                } else if (choice.equals("2")) {
                    System.out.print("ID e huazimit: ");
                    int loanId = Integer.parseInt(scanner.nextLine().trim());
                    double fine = loanService.returnItem(actor, loanId);
                    System.out.println("Kthimi u regjistrua me sukses.");
                    printOverdueFine(fine);

                } else if (choice.equals("3")) {
                    System.out.print("ID e huazimit te vonuar: ");
                    int loanId = Integer.parseInt(scanner.nextLine().trim());
                    Loan loan = loanDAO.findById(loanId)
                            .orElseThrow(() -> new IllegalArgumentException("Huazimi nuk u gjet."));
                    Fine fine = new Fine(loan, LocalDate.now());
                    fineService.issue(actor, fine);
                    System.out.println("Gjoba u krijua, id=" + fine.getId() + " shuma=" + fine.getAmount());

                } else if (choice.equals("4")) {
                    List<Fine> allFines = fineService.getAllPayments(actor);
                    if (allFines.isEmpty()) {
                        System.out.println("S'ka gjoba ne sistem.");
                    } else {
                        for (Fine fine : allFines) {
                            String statusi = fine.isPaid() ? "Paguar me " + fine.getPaidDate() : "E papaguar";
                            System.out.println("  #" + fine.getId() + " Anetari: " + fine.getLoan().getMember().getId()
                                    + " | Shuma: " + fine.getAmount() + " | " + statusi);
                        }
                    }

                } else if (choice.equals("5")) {
                    printReports(reportService, actor);

                } else if (choice.equals("6")) {
                    importFromOpenLibrary(scanner, actor, openLibraryClient, inventoryService);

                } else if (choice.equals("7")) {
                    inventoryMenu(scanner, actor, inventoryService, bookDAO, dvdDAO);

                } else if (choice.equals("8")) {
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

    // --- Nenmenuja e inventarit (vetem Librarian) ---
    // Cdo veprim kalon nga InventoryService, qe ben autorizimin dhe validimin;
    // ketu vetem lexohet input-i dhe shfaqet rezultati.
    private static void inventoryMenu(Scanner scanner, Person actor, InventoryService inventory,
                                      BookDAO bookDAO, DVDDAO dvdDAO) {
        while (true) {
            System.out.println("\n--- Inventari ---");
            System.out.println("1. Shiko katalogun e plote");
            System.out.println("2. Shto liber");
            System.out.println("3. Shto DVD");
            System.out.println("4. Ndrysho nje artikull");
            System.out.println("5. Fshi nje artikull");
            System.out.println("6. Kthehu");
            System.out.print("Zgjedh: ");
            String choice = scanner.nextLine().trim();

            try {
                if (choice.equals("1")) {
                    printFullCatalog(bookDAO, dvdDAO);

                } else if (choice.equals("2")) {
                    System.out.print("ID (p.sh. B020): ");
                    String id = scanner.nextLine();
                    System.out.print("Titulli: ");
                    String title = scanner.nextLine();
                    System.out.print("Autori: ");
                    String author = scanner.nextLine();
                    System.out.print("ISBN (10 ose 13 shifra): ");
                    String isbn = scanner.nextLine();
                    Category category = readCategory(scanner, null);
                    if (category == null) continue;
                    Book book = inventory.addBook(actor, id, title, category, author, isbn);
                    System.out.println("Libri u shtua: " + book.getId() + " — " + book.getTitle());

                } else if (choice.equals("3")) {
                    System.out.print("ID (p.sh. D010): ");
                    String id = scanner.nextLine();
                    System.out.print("Titulli: ");
                    String title = scanner.nextLine();
                    System.out.print("Kohezgjatja (minuta): ");
                    int duration = Integer.parseInt(scanner.nextLine().trim());
                    Category category = readCategory(scanner, null);
                    if (category == null) continue;
                    DVD dvd = inventory.addDVD(actor, id, title, category, duration);
                    System.out.println("DVD-ja u shtua: " + dvd.getId() + " — " + dvd.getTitle());

                } else if (choice.equals("4")) {
                    printFullCatalog(bookDAO, dvdDAO);
                    System.out.print("ID e artikullit qe do ndryshosh: ");
                    String id = scanner.nextLine().trim();
                    LibraryItem item = inventory.findItem(id)
                            .orElseThrow(() -> new IllegalArgumentException("Artikulli me ID " + id + " nuk u gjet."));
                    System.out.println("(Enter = mban vleren aktuale)");
                    String title = readOrKeep(scanner, "Titulli", item.getTitle());

                    if (item instanceof Book book) {
                        String author = readOrKeep(scanner, "Autori", book.getAuthor());
                        String isbn = readOrKeep(scanner, "ISBN", book.getIsbn());
                        Category category = readCategory(scanner, book.getCategory());
                        if (category == null) continue;
                        inventory.updateBook(actor, id, title, category, author, isbn);
                    } else if (item instanceof DVD dvd) {
                        String duration = readOrKeep(scanner, "Kohezgjatja (minuta)", String.valueOf(dvd.getDurationMinutes()));
                        Category category = readCategory(scanner, dvd.getCategory());
                        if (category == null) continue;
                        inventory.updateDVD(actor, id, title, category, Integer.parseInt(duration));
                    }
                    System.out.println("Artikulli u perditesua.");

                } else if (choice.equals("5")) {
                    printFullCatalog(bookDAO, dvdDAO);
                    System.out.print("ID e artikullit qe do fshish: ");
                    String id = scanner.nextLine().trim();
                    LibraryItem item = inventory.findItem(id)
                            .orElseThrow(() -> new IllegalArgumentException("Artikulli me ID " + id + " nuk u gjet."));
                    // Konfirmim eksplicit — fshirja s'mund te zhbehet
                    System.out.print("Fshi \"" + item.getTitle() + "\"? Shkruaj PO per te konfirmuar: ");
                    if (!scanner.nextLine().trim().equalsIgnoreCase("PO")) {
                        System.out.println("Fshirja u anulua.");
                        continue;
                    }
                    inventory.deleteItem(actor, id);
                    System.out.println("Artikulli u fshi.");

                } else if (choice.equals("6")) {
                    return;

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

    // Enter bosh → mban vleren aktuale; perdoret te "Ndrysho artikull"
    private static String readOrKeep(Scanner scanner, String label, String current) {
        System.out.print(label + " [" + current + "]: ");
        String input = scanner.nextLine().trim();
        return input.isEmpty() ? current : input;
    }

    // Katalogu i plote per bibliotekarin — me tip, status dhe detajet specifike
    private static void printFullCatalog(BookDAO bookDAO, DVDDAO dvdDAO) {
        System.out.println("--- Katalogu ---");
        List<Book> books = bookDAO.findAll();
        List<DVD> dvds = dvdDAO.findAll();
        if (books.isEmpty() && dvds.isEmpty()) {
            System.out.println("  (katalogu eshte bosh)");
            return;
        }
        for (Book book : books) {
            System.out.println("  " + book.getId() + " — " + book.getTitle() + " | " + book.getAuthor()
                    + " | ISBN " + book.getIsbn() + " | " + book.getCategory().getDisplayName()
                    + " | " + (book.isAvailable() ? "i lire" : "i zene"));
        }
        for (DVD dvd : dvds) {
            System.out.println("  " + dvd.getId() + " — " + dvd.getTitle() + " (DVD, " + dvd.getDurationMinutes()
                    + " min) | " + dvd.getCategory().getDisplayName() + " | " + (dvd.isAvailable() ? "i lire" : "i zene"));
        }
    }
}
