package org.example.service;

import org.example.dao.LibrarianDAO;
import org.example.dao.MemberDAO;

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
