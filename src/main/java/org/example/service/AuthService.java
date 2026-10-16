package org.example.service;

import org.example.dao.LibrarianDAO;
import org.example.dao.MemberDAO;

import org.example.model.Librarian;
import org.example.model.Member;
import org.example.model.Person;

import org.example.util.DBConnection;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class AuthService {
    private final MemberDAO memberDAO;
    private final LibrarianDAO librarianDAO;

    public AuthService(MemberDAO memberDAO, LibrarianDAO librarianDAO) {
        this.memberDAO = memberDAO;
        this.librarianDAO = librarianDAO;
    }
    // Kthen Optional bosh si per ID te panjohur, ashtu edhe per fjalekalim te gabuar —
    // qellimisht s'i dallon keto dy raste ketu, qe CLI-ja te japi mesazh gjenerik dhe
    // te mos zbuloje nese nje ID ekziston fare ("user enumeration").
    public Optional<Person> login(String id,String rawPassword){
        try(Connection connection = DBConnection.getInstance().getConnection()){

            String personType;
            String storedHash;

            try(PreparedStatement stmt = connection.prepareStatement(
                    "SELECT person_type, password_hash FROM persons WHERE id = ?"))
            {
                stmt.setString(1, id);
                try(ResultSet rs = stmt.executeQuery()){
                    if(!rs.next()){
                       return Optional.empty();//id nuk ekziston
                    }
                    personType = rs.getString("person_type");
                    storedHash = rs.getString("password_hash");
                }
            }

            // BCrypt.checkpw() rillogarit hash-in me te njejtin salt te ruajtur brenda
            // storedHash dhe krahason — kurre s'krahasohen fjalekalime plain text
            try {
                if (!BCrypt.checkpw(rawPassword, storedHash)) {
                    return Optional.empty(); // fjalekalim i gabuar
                }
            } catch (IllegalArgumentException e) {
                // storedHash s'eshte format i vlefshem BCrypt — ndodh kur nje Person
                // eshte krijuar por password_hash i tij eshte ende placeholder
                // (fjalekalimi real s'eshte vendosur ende). Trajtohet si kredenciale
                // te gabuara, jo si crash — nga jashte duket njesoj (login deshtoi).
                return Optional.empty();
            }
            // Resolve ne entitetin konkret sipas person_type — i njejti pattern qe
            // LoanDAOImpl perdor per te dalluar Book/DVD.
            if("MEMBER".equals(personType)){
                return memberDAO.findById(id).map(m -> (Person) m);

            }else {
                return librarianDAO.findById(id).map(l -> (Person) l);
            }
        }catch (SQLException e){
            throw new RuntimeException("Gabim DB gjate login()", e);
        }

    }
    static final int MIN_PASSWORD_LENGTH = 8;

    // Sign Up i nje anetari te ri — nuk kerkon actor, cdokush mund te regjistrohet si Member.
    public Member registerMember(String id, String name, String email, String phone, String rawPassword) {
        Member member = new Member(validateUsername(id), validateName(name), validateEmail(email), blankToNull(phone));
        insertPerson(member, "MEMBER", rawPassword,
                "INSERT INTO members (person_id) VALUES (?)", null);
        return member;
    }

    // Vetem nje bibliotekar ekzistues mund te krijoje bibliotekar te ri — perndryshe
    // cdo anetar do te mund t'i jepte vetes te drejta mbi inventarin dhe gjobat.
    public Librarian registerLibrarian(Person actor, String id, String name, String email, String phone,
                                       String employeeCode, String rawPassword) {
        if (!actor.canManageInventory()) {
            throw new SecurityException("Vetem bibliotekaret mund te krijojne bibliotekare te rinj.");
        }
        if (employeeCode == null || !employeeCode.trim().matches("[A-Za-z0-9_-]{1,20}")) {
            throw new IllegalArgumentException("Kodi i punonjesit duhet te kete 1-20 shkronja, shifra, _ ose -.");
        }
        Librarian librarian = new Librarian(validateUsername(id), validateName(name), validateEmail(email),
                blankToNull(phone), employeeCode.trim());
        insertPerson(librarian, "LIBRARIAN", rawPassword,
                "INSERT INTO librarians (person_id, employee_code) VALUES (?, ?)", librarian.getEmployeeCode());
        return librarian;
    }

    // persons + members/librarians + hash-i real ne NJE transaksion.
    // PSE jo DAO.save() + setPassword(): ato jane dy transaksione — nese i dyti
    // deshtonte, mbetej nje llogari me hash placeholder ku s'mund te hyhej kurre.
    private void insertPerson(Person person, String personType, String rawPassword,
                              String subtypeSql, String employeeCode) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Fjalekalimi duhet te kete te pakten " + MIN_PASSWORD_LENGTH + " karaktere.");
        }
        String hash = hashPassword(rawPassword);

        try (Connection connection = DBConnection.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO persons (id, name, email, phone, person_type, password_hash) VALUES (?, ?, ?, ?, ?, ?)")) {
                    statement.setString(1, person.getId());
                    statement.setString(2, person.getName());
                    statement.setString(3, person.getEmail());
                    statement.setString(4, person.getPhone());
                    statement.setString(5, personType);
                    statement.setString(6, hash);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(subtypeSql)) {
                    statement.setString(1, person.getId());
                    if (employeeCode != null) statement.setString(2, employeeCode);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                // 23505 = username (persons.id) ose employee_code ekziston tashme
                if ("23505".equals(e.getSQLState())) {
                    throw new IllegalArgumentException("Username ose kodi i punonjesit eshte i zene. Provo nje tjeter.");
                }
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate krijimit te llogarise", e);
        }
    }

    private static String validateUsername(String id) {
        if (id == null || !id.trim().matches("[A-Za-z0-9_.-]{3,50}")) {
            throw new IllegalArgumentException("Username duhet te kete 3-50 shkronja, shifra, _ . ose -.");
        }
        return id.trim();
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 255) {
            throw new IllegalArgumentException("Emri s'mund te jete bosh.");
        }
        return name.trim();
    }

    // Kontroll bazik formati — s'garanton qe adresa ekziston
    private static String validateEmail(String email) {
        if (email == null || !email.trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || email.trim().length() > 255) {
            throw new IllegalArgumentException("Email i pavlefshem.");
        }
        return email.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // Perdoret vetem kur krijohet/rivendoset password per nje Person (p.sh. migrim
    // fillestar i te dhenave test, ose nje krijim i ri anetari nga Librarian).
    public static String hashPassword(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }

    // Vendos fjalekalimin real per nje Person qe tashme ekziston ne DB (i krijuar
    // me placeholder nga MemberDAO/LibrarianDAO.save()). I domosdoshem per flow-in
    // e sign-up: save() krijon rreshtin, kjo metode e ben te kycshem realisht.
    public void setPassword(String personId, String rawPassword) {
        String hash = hashPassword(rawPassword);
        try (Connection connection = DBConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE persons SET password_hash = ? WHERE id = ?")) {
            statement.setString(1, hash);
            statement.setString(2, personId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("Personi me ID " + personId + " nuk u gjet.");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate vendosjes se fjalekalimit", e);
        }
    }
}
