package org.example.cli;

import org.example.dao.BookDAO;
import org.example.dao.DVDDAO;
import org.example.model.Book;
import org.example.model.DVD;

import java.util.List;

/** Shfaqje te perbashketa mes menuve te anetarit dhe te bibliotekarit. */
public final class ConsoleViews {

    private ConsoleViews() {} // vetem metoda statike

    /**
     * Listohen artikujt (ID + titull) — pa kete, anetari do te duhej te dinte
     * perpara ID-te e librave qe s'i ka pare kurre, gje qe s'ka logjike per nje
     * perdorues real. available=true per huazim, false per rezervim (vetem
     * artikujt e zene mund te rezervohen).
     *
     * @param bookDAO   burimi i librave
     * @param dvdDAO    burimi i DVD-ve
     * @param available true per artikujt e lire, false per te zenet
     */
    public static void printCatalog(BookDAO bookDAO, DVDDAO dvdDAO, boolean available) {
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

    /**
     * Katalogu i plote per bibliotekarin — me tip, status dhe detajet specifike
     *
     * @param bookDAO burimi i librave
     * @param dvdDAO  burimi i DVD-ve
     */
    public static void printFullCatalog(BookDAO bookDAO, DVDDAO dvdDAO) {
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

    /**
     * I perbashket per kthimin nga anetari dhe nga bibliotekari — gjoba krijohet
     * automatikisht nga LoanService.returnItem(), ketu vetem njoftohet.
     *
     * @param fineAmount shuma e gjobes; asgje s'shfaqet per 0
     */
    public static void printOverdueFine(double fineAmount) {
        if (fineAmount > 0) {
            System.out.printf("Kthim me vonese — u krijua gjobe prej %.2f €. Huazimet e reja bllokohen derisa te paguhet.%n", fineAmount);
        }
    }
}
