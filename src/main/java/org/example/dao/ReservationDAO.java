package org.example.dao;

import org.example.model.Reservation;

import java.util.List;
import java.util.Optional;

public interface ReservationDAO {

    Optional<Reservation> findById(int id);

    List<Reservation> findAll();

    void save(Reservation reservation);

    // Aktivizon nje rezervim (kur libri kthehet dhe i takon radhes).
    // Marrim id si int, jo objekt Reservation, sepse modeli s'e ruan id-ne
    // (i njejti vendim si markReturned(int loanId, ...) te LoanDAO).
    void markFulfilled(int reservationId);

    // "Ready for pickup": artikulli mbahet per kete anetar specifik (radha FIFO)
    // pas kthimit, por s'eshte huazuar ende. markFulfilled() ndodh vetem kur
    // anetari e huazon realisht (shih LoanService.borrowItem()).
    void markReadyForPickup(int reservationId);

    // Kjo ESHTE radha FIFO "on the fly" — asnje kolone e ruajtur per pozicionin,
    // vetem ORDER BY reservation_date ASC mbi rezervimet e papermbushura per nje artikull.
    // ReservationService do ta thërrasë kete per te gjetur cili anëtar eshte i radhes.
    List<Reservation> findQueueForItem(String itemId);
}