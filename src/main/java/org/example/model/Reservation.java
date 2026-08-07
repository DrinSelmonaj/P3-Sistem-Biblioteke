package org.example.model;

import java.time.LocalDate;

public class Reservation {
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
    public Member getMember() { return member; }
    public LibraryItem getItem() { return item; }
    public LocalDate getReservationDate() { return reservationDate; }

}

