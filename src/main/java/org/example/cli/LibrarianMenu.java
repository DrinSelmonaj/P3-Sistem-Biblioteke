package org.example.cli;

import org.example.client.BookSearchResult;
import org.example.client.OpenLibraryClient;
import org.example.dao.BookDAO;
import org.example.dao.DVDDAO;
import org.example.dao.LoanDAO;
import org.example.model.Book;
import org.example.model.Category;
import org.example.model.Fine;
import org.example.model.Librarian;
import org.example.model.Loan;
import org.example.model.Person;
import org.example.service.AuthService;
import org.example.service.FineService;
import org.example.service.InventoryService;
import org.example.service.LoanService;
import org.example.service.ReportService;

import java.time.LocalDate;
import java.util.List;

/** Menuja e bibliotekarit: huazime, gjoba, raporte, importi, inventari dhe llogarite. */
public class LibrarianMenu {

    private final ConsoleIO io;
    private final LoanService loanService;
    private final FineService fineService;
    private final ReportService reportService;
    private final OpenLibraryClient openLibraryClient;
    private final InventoryService inventoryService;
    private final AuthService authService;
    private final InventoryMenu inventoryMenu;
    private final LoanDAO loanDAO;
    private final BookDAO bookDAO;
    private final DVDDAO dvdDAO;

    /**
     * @param io                input-i nga terminali
     * @param loanService       huazimi dhe kthimi
     * @param fineService       gjobat
     * @param reportService     raportet
     * @param openLibraryClient kerkimi ne Open Library
     * @param inventoryService  shtimi i librave te importuar
     * @param authService       krijimi i bibliotekareve
     * @param inventoryMenu     nenmenuja e inventarit
     * @param loanDAO           leximi i huazimeve per gjobat manuale
     * @param bookDAO           katalogu i librave
     * @param dvdDAO            katalogu i DVD-ve
     */
    public LibrarianMenu(ConsoleIO io, LoanService loanService, FineService fineService,
                         ReportService reportService, OpenLibraryClient openLibraryClient,
                         InventoryService inventoryService, AuthService authService, InventoryMenu inventoryMenu,
                         LoanDAO loanDAO, BookDAO bookDAO, DVDDAO dvdDAO) {
        this.io = io;
        this.loanService = loanService;
        this.fineService = fineService;
        this.reportService = reportService;
        this.openLibraryClient = openLibraryClient;
        this.inventoryService = inventoryService;
        this.authService = authService;
        this.inventoryMenu = inventoryMenu;
        this.loanDAO = loanDAO;
        this.bookDAO = bookDAO;
        this.dvdDAO = dvdDAO;
    }

    /** @param actor bibliotekari i kycur; menuja perseritet derisa te zgjedhe "Dil" */
    public void run(Person actor) {
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
            System.out.println("8. Shto bibliotekar te ri");
            System.out.println("9. Dil");
            System.out.print("Zgjedh: ");
            String choice = io.nextLine().trim();

            try {
                if (choice.equals("1")) {
                    System.out.print("ID e anetarit: ");
                    String memberId = io.nextLine().trim();
                    ConsoleViews.printCatalog(bookDAO, dvdDAO, true);
                    System.out.print("ID e artikullit: ");
                    String itemId = io.nextLine().trim();
                    Loan loan = loanService.borrowItem(actor, memberId, itemId);
                    System.out.println("Huazim i regjistruar, id=" + loan.getId() + " afati=" + loan.getDueDate());

                } else if (choice.equals("2")) {
                    System.out.print("ID e huazimit: ");
                    int loanId = Integer.parseInt(io.nextLine().trim());
                    double fine = loanService.returnItem(actor, loanId);
                    System.out.println("Kthimi u regjistrua me sukses.");
                    ConsoleViews.printOverdueFine(fine);

                } else if (choice.equals("3")) {
                    System.out.print("ID e huazimit te vonuar: ");
                    int loanId = Integer.parseInt(io.nextLine().trim());
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
                    printReports(actor);

                } else if (choice.equals("6")) {
                    importFromOpenLibrary(actor);

                } else if (choice.equals("7")) {
                    inventoryMenu.run(actor);

                } else if (choice.equals("8")) {
                    createLibrarian(actor);

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

    // Printon te tre raportet njeheresh — permbledhje + me te huazuarit + gjobat.
    private void printReports(Person actor) {
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
    private void importFromOpenLibrary(Person actor) {
        System.out.print("Kerko liber (titull): ");
        String query = io.nextLine().trim();
        if (query.isEmpty()) {
            System.out.println("Shkruaj te pakten nje fjale per kerkim.");
            return;
        }

        List<BookSearchResult> results;
        try {
            results = openLibraryClient.search(query);
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
            choice = Integer.parseInt(io.nextLine().trim());
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
            isbn = io.nextLine().trim();
        }
        System.out.print("ID lokale per librin (p.sh. B020): ");
        String id = io.nextLine().trim();

        Category category = io.readCategory(null);
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

    // Krijon llogari bibliotekari — fjalekalimi kerkohet dy here, sepse ndryshe nga
    // Sign Up, personi qe e shkruan s'eshte ai qe do ta perdore (s'e sheh gabimin).
    private void createLibrarian(Person actor) {
        System.out.println("\n--- Bibliotekar i Ri ---");
        System.out.print("Username: ");
        String id = io.nextLine();
        System.out.print("Emri: ");
        String name = io.nextLine();
        System.out.print("Email: ");
        String email = io.nextLine();
        System.out.print("Telefoni (opsional): ");
        String phone = io.nextLine();
        System.out.print("Kodi i punonjesit: ");
        String employeeCode = io.nextLine();
        String password = io.readPassword();
        System.out.print("Perserit fjalekalimin. ");
        String confirm = io.readPassword();

        if (!password.equals(confirm)) {
            System.out.println("Fjalekalimet s'perputhen. Llogaria s'u krijua.");
            return;
        }
        try {
            Librarian librarian = authService.registerLibrarian(actor, id, name, email, phone, employeeCode, password);
            System.out.println("Bibliotekari " + librarian.getName() + " (" + librarian.getId() + ") u krijua dhe mund te kycet.");
        } catch (IllegalArgumentException | SecurityException e) {
            System.out.println("Gabim: " + e.getMessage());
        }
    }
}
