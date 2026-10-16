package org.example.cli;

import org.example.model.Member;
import org.example.model.Person;
import org.example.service.AuthService;

/** Menuja hyrese: login ose Sign Up, perseritet derisa perdoruesi te kycet. */
public class AuthMenu {

    private final ConsoleIO io;
    private final AuthService authService;

    /**
     * @param io          input-i nga terminali
     * @param authService login dhe regjistrimi
     */
    public AuthMenu(ConsoleIO io, AuthService authService) {
        this.io = io;
        this.authService = authService;
    }

    /** @return personi i kycur (Member ose Librarian) */
    public Person run() {
    Person loggedInUser = null;
    while (loggedInUser == null) {
        System.out.println("Jeni anetar? Shtypni 1 per Login.");
        System.out.println("Deshironi te regjistroheni? Shtypni 2 per Sign Up.");
        System.out.print("Zgjedh: ");
        String entryChoice = io.nextLine().trim();

        if (entryChoice.equals("1")) {
            System.out.print("Username: ");
            String id = io.nextLine().trim();
            String password = io.readPassword();

            var result = authService.login(id, password);
            if (result.isPresent()) {
                loggedInUser = result.get();
                System.out.println("Mireserdhe, " + loggedInUser.getName() + "!");
            } else {
                // Mesazh gjenerik qellimisht — s'zbulojme nese ID ekziston fare.
                System.out.println("Username ose fjalekalim i gabuar. Provo perseri.\n");
            }

        } else if (entryChoice.equals("2")) {
            loggedInUser = signUp();

        } else {
            System.out.println("Zgjedhje e panjohur. Shtyp 1 ose 2.\n");
        }
    }
        return loggedInUser;
    }

    // Regjistrim i ri per Member — vetem Member mund te regjistrohet vetvetiu;
    // Librarian mbetet i krijuar nga stafi (jashte ketij flow-i publik), sepse
    // eshte llogari pune, jo diçka qe cilido duhet te mund ta krijoje vetem.
    // Kthen null nese regjistrimi deshton (p.sh. ID e zene tashme), jo exception —
    // qe loop-i hyres thjesht ta ripyese perdoruesin, njesoj si login i gabuar.
    private Person signUp() {
        System.out.println("\n--- Regjistrim Anetari i Ri ---");
        System.out.print("Username (do te perdoret per login): ");
        String id = io.nextLine().trim();
        System.out.print("Emri: ");
        String name = io.nextLine().trim();
        System.out.print("Email: ");
        String email = io.nextLine().trim();
        System.out.print("Telefoni: ");
        String phone = io.nextLine().trim();
        String password = io.readPassword();

        try {
            // Nje transaksion i vetem (persons + members + hash) — shih AuthService.insertPerson()
            Member newMember = authService.registerMember(id, name, email, phone, password);
            System.out.println("Regjistrimi u krye me sukses. Mireserdhe, " + newMember.getName() + "!\n");
            return newMember;
        } catch (Exception e) {
            System.out.println("Gabim gjate regjistrimit: " + e.getMessage() + "\n");
            return null;
        }
    }
}
