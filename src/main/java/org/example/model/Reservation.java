package org.example.model;

import java.time.LocalDate;

public class Reservation {
    // Njesoj si Loan.id — Integer, null derisa te ruhet ne DB, vendoset vetem nga DAO.
    private Integer id;
    private Member member;
    private LibraryItem item;
    private LocalDate reservationDate;
    private boolean fulfilled;
    private boolean readyForPickup;

    public Reservation(Member member, LibraryItem item, LocalDate reservationDate) {
        this.member = member;
        this.item = item;
        this.reservationDate = reservationDate;
        this.fulfilled = false;
        this.readyForPickup = false;
    }

    public void markFulfilled(){
        this.fulfilled = true;
    }

    public boolean isFulfilled(){
        return fulfilled;
    }

    // "Ready for pickup" = artikulli eshte kthyer dhe eshte mbajtur per kete anetar
    // specifik (radha FIFO), por anetari ende s'e ka huazuar realisht.
    // Dallon nga fulfilled: fulfilled do te thote anetari e ka huazuar tashme
    // (rezervimi eshte "konsumuar" plotesisht).
    public void markReadyForPickup() { this.readyForPickup = true; }
    public boolean isReadyForPickup() { return readyForPickup; }

    public Integer getId() { return id; }

    // Vetem DAO-t duhet ta thirrasin — pas INSERT (RETURNING id) ose gjate mapRowToReservation().
    public void setId(Integer id) { this.id = id; }

    public Member getMember() { return member; }
    public LibraryItem getItem() { return item; }
    public LocalDate getReservationDate() { return reservationDate; }

}