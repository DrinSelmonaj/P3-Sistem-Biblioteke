package org.example.service;

import org.example.TestData;
import org.example.dao.*;
import org.example.model.Loan;
import org.example.model.Member;
import org.example.model.Reservation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Test integrimi (kerkon Postgres real). Te dhenat krijohen nga TestData dhe
// fshihen pas cdo testi — DB-ja mbetet ashtu sic ishte.
class ReservationServiceTest {

    private final MemberDAO memberDAO = new MemberDAOImpl();
    private final BookDAO bookDAO = new BookDAOImpl();
    private final DVDDAO dvdDAO = new DVDDAOImpl();
    private final ReservationDAO reservationDAO = new ReservationDAOImpl(memberDAO, bookDAO, dvdDAO);
    private final LoanService loanService = new LoanService(memberDAO, bookDAO, dvdDAO);
    private final ReservationService reservationService = new ReservationService();

    private TestData data;

    @BeforeEach
    void setUp() {
        data = new TestData();
    }

    @AfterEach
    void tearDown() throws Exception {
        data.close();
    }

    private Member member(String id) {
        return memberDAO.findById(id).orElseThrow();
    }

    // Cikli i plote: huazim → dy rezervime ne radhe → kthim → artikulli mbahet
    // per te parin ne radhe (jo per te dytin) → i pari e huazon.
    @Test
    void reservationQueueIsFifoFromReserveToBorrow() throws Exception {
        String bookId = data.createBook(true);
        Member holder = member(data.createMember());
        Member first = member(data.createMember());
        Member second = member(data.createMember());

        Loan loan = loanService.borrowItem(holder, holder.getId(), bookId);

        ReservationService.ReservationResult r1 = reservationService.reserveItem(first, first.getId(), bookId);
        ReservationService.ReservationResult r2 = reservationService.reserveItem(second, second.getId(), bookId);
        assertEquals(1, r1.queuePosition());
        assertEquals(2, r2.queuePosition());

        loanService.returnItem(holder, loan.getId());

        Reservation firstReservation = reservationDAO.findById(r1.reservationId()).orElseThrow();
        Reservation secondReservation = reservationDAO.findById(r2.reservationId()).orElseThrow();
        assertTrue(firstReservation.isReadyForPickup(), "i pari ne radhe duhet ta kete artikullin gati");
        assertFalse(secondReservation.isReadyForPickup());

        // I dyti s'mund ta marre — artikulli mbahet per te parin
        assertThrows(IllegalStateException.class, () -> loanService.borrowItem(second, second.getId(), bookId));

        loanService.borrowItem(first, first.getId(), bookId);
        assertTrue(reservationDAO.findById(r1.reservationId()).orElseThrow().isFulfilled());
    }

    @Test
    void cannotReserveAvailableItem() throws Exception {
        String bookId = data.createBook(true);
        Member member = member(data.createMember());

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> reservationService.reserveItem(member, member.getId(), bookId));
        assertEquals("Artikulli eshte i disponueshem — huazoje direkt.", e.getMessage());
    }

    @Test
    void cannotReserveTwiceOrReserveOwnLoan() throws Exception {
        String bookId = data.createBook(true);
        Member holder = member(data.createMember());
        Member other = member(data.createMember());
        loanService.borrowItem(holder, holder.getId(), bookId);

        assertThrows(IllegalStateException.class,
                () -> reservationService.reserveItem(holder, holder.getId(), bookId),
                "s'ka kuptim te rezervosh dicka qe e ke vete");

        reservationService.reserveItem(other, other.getId(), bookId);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> reservationService.reserveItem(other, other.getId(), bookId));
        assertEquals("Ke tashme nje rezervim aktiv per kete artikull.", e.getMessage());

        // Mbetet vetem nje rezervim aktiv — tentativat e deshtuara s'lane gjurme
        List<Reservation> active = reservationDAO.findActiveByMember(other.getId());
        assertEquals(1, active.size());
    }

    @Test
    void memberWithUnpaidFinesCannotReserve() throws Exception {
        String bookId = data.createBook(false);
        Member member = member(data.createMember());
        data.setUnpaidFines(member.getId(), 2.50);

        assertThrows(IllegalStateException.class,
                () -> reservationService.reserveItem(member, member.getId(), bookId));
    }

    @Test
    void memberCannotReserveForAnotherMember() throws Exception {
        String bookId = data.createBook(false);
        Member actor = member(data.createMember());
        String otherId = data.createMember();

        SecurityException e = assertThrows(SecurityException.class,
                () -> reservationService.reserveItem(actor, otherId, bookId));
        assertEquals("Mund te rezervosh vetem per vete.", e.getMessage());
        assertTrue(reservationDAO.findActiveByMember(otherId).isEmpty());
    }
}
