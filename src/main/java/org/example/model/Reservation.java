package org.example.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Reservation {
    // Njesoj si Loan.id — Integer, null derisa te ruhet ne DB, vendoset vetem nga DAO.
    private Integer id;
    private Member member;
    private LibraryItem item;
    private LocalDate reservationDate;
    private boolean fulfilled;
    private boolean readyForPickup;
    private LocalDateTime readySince; // null derisa te behet ready_for_pickup
    private boolean cancelled;

    public Reservation(Member member, LibraryItem item, LocalDate reservationDate) {
        this.member = member;
        this.item = item;
        this.reservationDate = reservationDate;
        this.fulfilled = false;
        this.readyForPickup = false;
        this.cancelled = false;
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
    public void markReadyForPickup() {
        this.readyForPickup = true;
        this.readySince = LocalDateTime.now();
    }
    public boolean isReadyForPickup() {
        return readyForPickup;
    }

    public void markCancelled() {
        this.cancelled = true;
        this.readyForPickup = false;
    }

    public Integer getId() { return id; }

    // Vetem DAO-t duhet ta thirrasin — pas INSERT (RETURNING id) ose gjate mapRowToReservation().
    public void setId(Integer id) { this.id = id; }

    public Member getMember() { return member; }
    public LibraryItem getItem() { return item; }
    public LocalDate getReservationDate() { return reservationDate; }
    public LocalDateTime getReadySince() { return readySince; }
    public boolean isCancelled() { return cancelled; }

    // Vetem DAO-t duhet t'i thirrasin — perdoren ne mapRowToReservation() per te
// lexuar gjendjen ashtu sic eshte ne DB, pa i shoqeruar me markReadyForPickup()/
// markCancelled() qe do te rillogaritnin readySince=now() gabimisht.
    public void setFulfilled(boolean fulfilled) { this.fulfilled = fulfilled; }
    public void setReadyForPickup(boolean readyForPickup) { this.readyForPickup = readyForPickup; }
    public void setReadySince(LocalDateTime readySince) { this.readySince = readySince; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

}