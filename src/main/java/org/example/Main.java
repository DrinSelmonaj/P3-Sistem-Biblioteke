package org.example;

import org.example.dao.*;
import org.example.model.*;
import org.example.service.*;

import java.io.Console;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;
import java.sql.SQLException;

public class Main {
    public static void main(String[] args) {
        // --- Wiring i DAO-ve ---
        MemberDAO memberDAO = new MemberDAOImpl();
        BookDAO bookDAO = new BookDAOImpl();
        DVDDAO dvdDAO = new DVDDAOImpl();
        LoanDAO loanDAO = new LoanDAOImpl(memberDAO, bookDAO, dvdDAO);
        FineDAO fineDAO = new FineDAOImpl(loanDAO);
        LibrarianDAO librarianDAO = new LibrarianDAOImpl();

        // --- Wiring i Service-ve ---
        AuthService authService = new AuthService(memberDAO, librarianDAO);
        LoanService loanService = new LoanService(memberDAO, bookDAO, dvdDAO);
        FineService fineService = new FineService(fineDAO);

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
            librarianMenu(scanner, loggedInUser, loanService, fineService, loanDAO, bookDAO, dvdDAO);
        } else {
            memberMenu(scanner, loggedInUser, loanService, fineService, loanDAO, bookDAO, dvdDAO);
        }

        scanner.close();
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
            System.out.print("Fjalekalimi (dukshem - xhiro nga Terminal.app per fshehje): ");
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
        } catch (RuntimeException e) {
            if (isDuplicateUsername(e)) {
                System.out.println("Ky username eshte i zene tashme. Zgjidh nje tjeter.\n");
            } else {
                System.out.println("Gabim gjate regjistrimit: " + e.getMessage() + "\n");
            }
            return null;
        }
    }

    // PostgreSQL kthen SQLState "23505" specifikisht per shkelje te PRIMARY
    // KEY/UNIQUE — e dallojme nga gabime te tjera (p.sh. DB e paarritshme)
    // qe te japim mesazh te qarte vetem per rastin real: username i zene.
    private static boolean isDuplicateUsername(Throwable e) {
        Throwable cause = e.getCause();
        return cause instanceof SQLException sqlEx && "23505".equals(sqlEx.getSQLState());
    }


    // Listohen artikujt e disponueshem (ID + titull) — pa kete, anetari do
    // te duhej te dinte perpara ID-te e librave qe s'i ka pare kurre, gje
    // qe s'ka logjike per nje perdorues real.
    private static void printAvailableCatalog(BookDAO bookDAO, DVDDAO dvdDAO) {
        System.out.println("--- Artikuj te disponueshem ---");
        List<Book> books = bookDAO.findAll();
        List<DVD> dvds = dvdDAO.findAll();

        boolean anyAvailable = false;
        for (Book book : books) {
            if (book.isAvailable()) {
                System.out.println("  " + book.getId() + " — " + book.getTitle() + " (Liber)");
                anyAvailable = true;
            }
        }
        for (DVD dvd : dvds) {
            if (dvd.isAvailable()) {
                System.out.println("  " + dvd.getId() + " — " + dvd.getTitle() + " (DVD)");
                anyAvailable = true;
            }
        }
        if (!anyAvailable) {
            System.out.println("  (asnje artikull i disponueshem momentalisht)");
        }
    }

    private static void memberMenu(Scanner scanner, Person actor, LoanService loanService,
                                   FineService fineService, LoanDAO loanDAO,
                                   BookDAO bookDAO, DVDDAO dvdDAO) {
        boolean running = true;
        while (running) {
            System.out.println("\n--- Menuja e Anetarit (" + actor.getName() + ") ---");
            System.out.println("1. Shiko huazimet e mia aktive");
            System.out.println("2. Huazo artikull");
            System.out.println("3. Kthe artikull");
            System.out.println("4. Shiko gjobat e mia");
            System.out.println("5. Paguaj nje gjobe");
            System.out.println("6. Dil");
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
                    printAvailableCatalog(bookDAO, dvdDAO);
                    System.out.print("ID e artikullit: ");
                    String itemId = scanner.nextLine().trim();
                    Loan loan = loanService.borrowItem(actor, actor.getId(), itemId);
                    System.out.println("Huazuar me sukses. Afati: " + loan.getDueDate());

                } else if (choice.equals("3")) {
                    System.out.print("ID e huazimit: ");
                    int loanId = Integer.parseInt(scanner.nextLine().trim());
                    loanService.returnItem(actor, loanId);
                    System.out.println("Artikulli u kthye me sukses.");

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
                    System.out.print("ID e gjobes qe do paguash: ");
                    int fineId = Integer.parseInt(scanner.nextLine().trim());
                    fineService.payFine(actor, fineId);
                    System.out.println("Gjoba u pagua me sukses.");

                } else if (choice.equals("6")) {
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
                                      FineService fineService, LoanDAO loanDAO,
                                      BookDAO bookDAO, DVDDAO dvdDAO) {
        boolean running = true;
        while (running) {
            System.out.println("\n--- Menuja e Librarianit (" + actor.getName() + ") ---");
            System.out.println("1. Regjistro huazim per nje anetar");
            System.out.println("2. Regjistro kthim");
            System.out.println("3. Krijo gjobe per nje huazim te vonuar");
            System.out.println("4. Shiko historine e plote te pagesave");
            System.out.println("5. Dil");
            System.out.print("Zgjedh: ");
            String choice = scanner.nextLine().trim();

            try {
                if (choice.equals("1")) {
                    System.out.print("ID e anetarit: ");
                    String memberId = scanner.nextLine().trim();
                    printAvailableCatalog(bookDAO, dvdDAO);
                    System.out.print("ID e artikullit: ");
                    String itemId = scanner.nextLine().trim();
                    Loan loan = loanService.borrowItem(actor, memberId, itemId);
                    System.out.println("Huazim i regjistruar, id=" + loan.getId() + " afati=" + loan.getDueDate());

                } else if (choice.equals("2")) {
                    System.out.print("ID e huazimit: ");
                    int loanId = Integer.parseInt(scanner.nextLine().trim());
                    loanService.returnItem(actor, loanId);
                    System.out.println("Kthimi u regjistrua me sukses.");

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
}