package org.example.dao;

import org.example.model.Reservation;

import java.util.List;
import java.util.Optional;

/**
 * Qasja ne databaze per rezervimet. Krijimi, anulimi dhe skadimi (qe ndryshojne
 * edhe disponueshmerine e artikullit) behen nga
 * {@link org.example.service.ReservationService} brenda transaksioneve te veta.
 */
public interface ReservationDAO {

    /**
     * @param id ID-ja e rezervimit
     * @return rezervimi me anetarin dhe artikullin, ose bosh
     */
    Optional<Reservation> findById(int id);

    /** @return te gjitha rezervimet, perfshire te permbushurat dhe te anuluarat */
    List<Reservation> findAll();

    /**
     * Ruan rezervimin pa kontrolle biznesi. Per rezervim nga menuja perdor
     * {@link org.example.service.ReservationService#reserveItem}.
     *
     * @param reservation rezervimi i ri; pas thirrjes ka ID-ne e gjeneruar
     */
    void save(Reservation reservation);

    /**
     * Shenon rezervimin si te permbushur — anetari e huazoi artikullin. Merr id si int,
     * jo objekt Reservation (i njejti vendim si {@link LoanDAO#markReturned}).
     *
     * @param reservationId ID-ja e rezervimit
     */
    void markFulfilled(int reservationId);

    /**
     * "Gati per marrje": artikulli u kthye dhe mbahet per kete anetar (radha FIFO),
     * por s'eshte huazuar ende. markFulfilled() ndodh vetem kur anetari e huazon realisht.
     *
     * @param reservationId ID-ja e rezervimit
     */
    void markReadyForPickup(int reservationId);

    /**
     * Anulim vullnetar ose skadim automatik — rezervimi "vdes" pa u konsumuar.
     *
     * @param reservationId ID-ja e rezervimit
     */
    void markCancelled(int reservationId);

    /**
     * Radha FIFO "on the fly" — asnje kolone e ruajtur per pozicionin, vetem
     * rezervimet aktive te artikullit sipas {@code reservation_date, id}.
     *
     * @param itemId ID-ja e artikullit
     * @return rezervimet aktive, i pari ne radhe ne fillim
     */
    List<Reservation> findQueueForItem(String itemId);

    /**
     * Rezervimet aktive (as te permbushura, as te anuluara) te nje anetari —
     * per "Shiko rezervimet e mia" dhe "Anulo".
     *
     * @param memberId username-i i anetarit
     * @return rezervimet aktive, me te vjetrat ne fillim
     */
    List<Reservation> findActiveByMember(String memberId);
}
