package org.example.service;

import org.example.TestData;
import org.example.dao.*;
import org.example.model.Librarian;
import org.example.model.Member;
import org.example.model.Person;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

// Test integrimi (kerkon Postgres real) — krijimi i llogarive dhe login.
class AuthServiceTest {

    private static final String PASSWORD = "fjalekalim-test-123";

    private final MemberDAO memberDAO = new MemberDAOImpl();
    private final LibrarianDAO librarianDAO = new LibrarianDAOImpl();
    private final AuthService authService = new AuthService(memberDAO, librarianDAO);

    private TestData data;
    private Librarian librarian;

    @BeforeEach
    void setUp() throws Exception {
        data = new TestData();
        librarian = librarianDAO.findById(data.createLibrarian()).orElseThrow();
    }

    @AfterEach
    void tearDown() throws Exception {
        data.close();
    }

    @Test
    void librarianCreatesLibrarianWhoCanLogInWithInventoryRights() {
        String id = data.reservePersonId();
        authService.registerLibrarian(librarian, id, "Bibliotekare e Re", "re@test.local", "",
                data.uniqueEmployeeCode(), PASSWORD);

        Person loggedIn = authService.login(id, PASSWORD).orElseThrow();
        assertInstanceOf(Librarian.class, loggedIn);
        assertTrue(loggedIn.canManageInventory());
        assertTrue(authService.login(id, "fjalekalim-i-gabuar").isEmpty());
    }

    @Test
    void memberCannotCreateLibrarian() throws Exception {
        Member member = memberDAO.findById(data.createMember()).orElseThrow();
        String id = data.reservePersonId();

        assertThrows(SecurityException.class, () -> authService.registerLibrarian(member, id, "Tentativë",
                "x@test.local", "", data.uniqueEmployeeCode(), PASSWORD));
        assertTrue(librarianDAO.findById(id).isEmpty());
    }

    // Kodi i punonjesit i zene deshton NE INSERT-in e dyte — rreshti i persons
    // duhet te jete rikthyer (rollback), jo te mbetet llogari gjysme e krijuar.
    @Test
    void duplicateEmployeeCodeLeavesNoHalfCreatedAccount() {
        String code = data.uniqueEmployeeCode();
        authService.registerLibrarian(librarian, data.reservePersonId(), "E Para", "a@test.local", "", code, PASSWORD);

        String secondId = data.reservePersonId();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> authService.registerLibrarian(librarian, secondId, "E Dyta", "b@test.local", "", code, PASSWORD));
        assertEquals("Username ose kodi i punonjesit eshte i zene. Provo nje tjeter.", e.getMessage());
        assertTrue(authService.login(secondId, PASSWORD).isEmpty(), "s'duhet te mbetet asnje gjurme e llogarise");
    }

    @Test
    void memberSignUpIsValidatedAndCanLogIn() {
        assertThrows(IllegalArgumentException.class,
                () -> authService.registerMember(data.reservePersonId(), "Emri", "email@test.local", "", "shkurt"));
        assertThrows(IllegalArgumentException.class,
                () -> authService.registerMember(data.reservePersonId(), "Emri", "pa-email", "", PASSWORD));
        assertThrows(IllegalArgumentException.class,
                () -> authService.registerMember("x", "Emri", "email@test.local", "", PASSWORD));

        String id = data.reservePersonId();
        authService.registerMember(id, "  Anetar i Ri  ", "anetar@test.local", "", PASSWORD);
        Optional<Person> loggedIn = authService.login(id, PASSWORD);
        assertTrue(loggedIn.isPresent());
        assertInstanceOf(Member.class, loggedIn.get());
        assertEquals("Anetar i Ri", loggedIn.get().getName());
        assertNull(loggedIn.get().getPhone(), "telefoni bosh ruhet si NULL");
    }
}
