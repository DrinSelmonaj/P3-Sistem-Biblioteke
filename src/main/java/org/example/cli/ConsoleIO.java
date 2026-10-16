package org.example.cli;

import org.example.model.Category;

import java.io.Console;
import java.util.Scanner;

/**
 * Leximi i input-it nga terminali, i perbashket per te gjitha menute — nje Scanner i
 * vetem per gjithe programin (disa Scanner mbi System.in do te humbnin rreshta mes tyre).
 */
public class ConsoleIO {

    private final Scanner scanner;
    private final Console console;

    /**
     * @param scanner lexuesi i System.in
     * @param console terminali real, ose null kur xhirohet nga IntelliJ (s'ka TTY)
     */
    public ConsoleIO(Scanner scanner, Console console) {
        this.scanner = scanner;
        this.console = console;
    }

    /** @return rreshti i radhes, ashtu sic u shkrua (pa trim) */
    public String nextLine() {
        return scanner.nextLine();
    }

    /**
     * Fsheh fjalekalimin me **** nese xhirohet ne terminal te vertete (console != null).
     * Ne IntelliJ Run panel (console == null) bie mbrapa te Scanner normal, me
     * nje shenim qe fjalekalimi shfaqet plain — kufizim i JVM-se, jo diçka qe
     * mund ta anashkalojme me kod.
     *
     * @return fjalekalimi i shkruar nga perdoruesi
     */
    public String readPassword() {
        if (console != null) {
            char[] passwordChars = console.readPassword("Fjalekalimi: ");
            return new String(passwordChars);
        } else {
            System.out.print("Fjalekalimi (i dukshem - xhiro nga Terminal.app per fshehje): ");
            return scanner.nextLine().trim();
        }
    }

    /**
     * Shfaq kategorite e numeruara dhe kthen zgjedhjen. `current` != null → Enter
     * e mban kategorine aktuale (perdoret gjate ndryshimit te artikullit).
     * Kthen null nese zgjedhja eshte e pavlefshme (thirresi e anulon veprimin).
     *
     * @param current kategoria aktuale, ose null kur s'ka (shtim i ri)
     * @return kategoria e zgjedhur, ose null nese zgjedhja eshte e pavlefshme
     */
    public Category readCategory(Category current) {
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

    /**
     * Enter bosh → mban vleren aktuale; perdoret te "Ndrysho artikull"
     *
     * @param label   emri i fushes
     * @param current vlera aktuale
     * @return vlera e re, ose current nese u shtyp vetem Enter
     */
    public String readOrKeep(String label, String current) {
        System.out.print(label + " [" + current + "]: ");
        String input = scanner.nextLine().trim();
        return input.isEmpty() ? current : input;
    }
}
