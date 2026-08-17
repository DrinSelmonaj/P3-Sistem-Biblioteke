package org.example.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class Loan {
    // Integer (jo int) — null domethene "ende s'eshte ruajtur ne DB".
    // Vendoset vetem nga DAO pas INSERT (RETURNING id), kurre ne konstruktor.
    private Integer id;
    private Member member;
    private LibraryItem item;
    private LocalDate loanDate;
    private LocalDate dueDate;
    private LocalDate returnDate;

    public Loan(Member member, LibraryItem item, LocalDate loanDate) {
        this.member = member;
        this.item = item;
        this.loanDate = loanDate;
        this.dueDate = loanDate.plusDays(item.getLoanPeriodDays());
        this.returnDate = null;
    }

    public boolean isReturned() {
        return returnDate != null;
    }

    public boolean isOverdue() {
        if (isReturned()) {
            return returnDate.isAfter(dueDate);
        }
        return LocalDate.now().isAfter(dueDate);
    }

    public long getDaysOverdue() {
        if (!isOverdue()) return 0;
        LocalDate compareDate = isReturned() ? returnDate : LocalDate.now();
        return ChronoUnit.DAYS.between(dueDate, compareDate);
    }

    public void markReturned(LocalDate returnDate) {
        this.returnDate = returnDate;
    }

    public Integer getId() { return id; }

    // Vetem DAO-t duhet ta thirrasin — pas INSERT (RETURNING id) ose gjate mapRowToLoan().
    public void setId(Integer id) { this.id = id; }

    // Vetem DAO-t duhet ta thirrasin — perdoret ne mapRowToLoan() per te mbishkruar
    // dueDate-in e rillogaritur automatikisht ne konstruktor (loanDate + loanPeriodDays)
    // me vleren reale te ruajtur ne DB. Pa kete, cdo ndryshim manual i due_date (p.sh.
    // shtyrje afati nga bibliotekari) do te injorohej sa here huazimi rilexohet nga DB.
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public Member getMember() { return member; }
    public LibraryItem getItem() { return item; }
    public LocalDate getLoanDate() { return loanDate; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDate getReturnDate() { return returnDate; }
}