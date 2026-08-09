package org.example.model;

import java.time.LocalDate;

public class Reservation {
    // Njesoj si Loan.id — Integer, null derisa te ruhet ne DB, vendoset vetem nga DAO.
    private Integer id;
    private Member member;
    private LibraryItem item;
    private LocalDate reservationDate;
    private boolean fulfilled;

    public Reservation(Member member, LibraryItem item, LocalDate reservationDate) {
        this.member = member;
        this.item = item;
        this.reservationDate = reservationDate;
        this.fulfilled = false;
    }

    public void markFulfilled(){
        this.fulfilled = true;
    }

    public boolean isFulfilled(){
        return fulfilled;
    }

    public Integer getId() { return id; }

    // Vetem DAO-t duhet ta thirrasin — pas INSERT (RETURNING id) ose gjate mapRowToReservation().
    public void setId(Integer id) { this.id = id; }

    public Member getMember() { return member; }
    public LibraryItem getItem() { return item; }
    public LocalDate getReservationDate() { return reservationDate; }

}