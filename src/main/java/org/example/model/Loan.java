package org.example.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class Loan {
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

    public Member getMember() { return member; }
    public LibraryItem getItem() { return item; }
    public LocalDate getLoanDate() { return loanDate; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDate getReturnDate() { return returnDate; }
}