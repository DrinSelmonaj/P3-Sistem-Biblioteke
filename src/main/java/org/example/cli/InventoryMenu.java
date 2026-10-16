package org.example.cli;

import org.example.dao.BookDAO;
import org.example.dao.DVDDAO;
import org.example.model.Book;
import org.example.model.Category;
import org.example.model.DVD;
import org.example.model.LibraryItem;
import org.example.model.Person;
import org.example.service.InventoryService;

/**
 * Nenmenuja e inventarit (vetem Librarian). Cdo veprim kalon nga InventoryService,
 * qe ben autorizimin dhe validimin; ketu vetem lexohet input-i dhe shfaqet rezultati.
 */
public class InventoryMenu {

    private final ConsoleIO io;
    private final InventoryService inventory;
    private final BookDAO bookDAO;
    private final DVDDAO dvdDAO;

    /**
     * @param io        input-i nga terminali
     * @param inventory shto / ndrysho / fshi
     * @param bookDAO   katalogu i librave
     * @param dvdDAO    katalogu i DVD-ve
     */
    public InventoryMenu(ConsoleIO io, InventoryService inventory, BookDAO bookDAO, DVDDAO dvdDAO) {
        this.io = io;
        this.inventory = inventory;
        this.bookDAO = bookDAO;
        this.dvdDAO = dvdDAO;
    }

    /** @param actor bibliotekari i kycur; kthehet kur zgjidhet "Kthehu" */
    public void run(Person actor) {
        while (true) {
            System.out.println("\n--- Inventari ---");
            System.out.println("1. Shiko katalogun e plote");
            System.out.println("2. Shto liber");
            System.out.println("3. Shto DVD");
            System.out.println("4. Ndrysho nje artikull");
            System.out.println("5. Fshi nje artikull");
            System.out.println("6. Kthehu");
            System.out.print("Zgjedh: ");
            String choice = io.nextLine().trim();

            try {
                if (choice.equals("1")) {
                    ConsoleViews.printFullCatalog(bookDAO, dvdDAO);

                } else if (choice.equals("2")) {
                    System.out.print("ID (p.sh. B020): ");
                    String id = io.nextLine();
                    System.out.print("Titulli: ");
                    String title = io.nextLine();
                    System.out.print("Autori: ");
                    String author = io.nextLine();
                    System.out.print("ISBN (10 ose 13 shifra): ");
                    String isbn = io.nextLine();
                    Category category = io.readCategory(null);
                    if (category == null) continue;
                    Book book = inventory.addBook(actor, id, title, category, author, isbn);
                    System.out.println("Libri u shtua: " + book.getId() + " — " + book.getTitle());

                } else if (choice.equals("3")) {
                    System.out.print("ID (p.sh. D010): ");
                    String id = io.nextLine();
                    System.out.print("Titulli: ");
                    String title = io.nextLine();
                    System.out.print("Kohezgjatja (minuta): ");
                    int duration = Integer.parseInt(io.nextLine().trim());
                    Category category = io.readCategory(null);
                    if (category == null) continue;
                    DVD dvd = inventory.addDVD(actor, id, title, category, duration);
                    System.out.println("DVD-ja u shtua: " + dvd.getId() + " — " + dvd.getTitle());

                } else if (choice.equals("4")) {
                    ConsoleViews.printFullCatalog(bookDAO, dvdDAO);
                    System.out.print("ID e artikullit qe do ndryshosh: ");
                    String id = io.nextLine().trim();
                    LibraryItem item = inventory.findItem(id)
                            .orElseThrow(() -> new IllegalArgumentException("Artikulli me ID " + id + " nuk u gjet."));
                    System.out.println("(Enter = mban vleren aktuale)");
                    String title = io.readOrKeep("Titulli", item.getTitle());

                    if (item instanceof Book book) {
                        String author = io.readOrKeep("Autori", book.getAuthor());
                        String isbn = io.readOrKeep("ISBN", book.getIsbn());
                        Category category = io.readCategory(book.getCategory());
                        if (category == null) continue;
                        inventory.updateBook(actor, id, title, category, author, isbn);
                    } else if (item instanceof DVD dvd) {
                        String duration = io.readOrKeep("Kohezgjatja (minuta)", String.valueOf(dvd.getDurationMinutes()));
                        Category category = io.readCategory(dvd.getCategory());
                        if (category == null) continue;
                        inventory.updateDVD(actor, id, title, category, Integer.parseInt(duration));
                    }
                    System.out.println("Artikulli u perditesua.");

                } else if (choice.equals("5")) {
                    ConsoleViews.printFullCatalog(bookDAO, dvdDAO);
                    System.out.print("ID e artikullit qe do fshish: ");
                    String id = io.nextLine().trim();
                    LibraryItem item = inventory.findItem(id)
                            .orElseThrow(() -> new IllegalArgumentException("Artikulli me ID " + id + " nuk u gjet."));
                    // Konfirmim eksplicit — fshirja s'mund te zhbehet
                    System.out.print("Fshi \"" + item.getTitle() + "\"? Shkruaj PO per te konfirmuar: ");
                    if (!io.nextLine().trim().equalsIgnoreCase("PO")) {
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
}
