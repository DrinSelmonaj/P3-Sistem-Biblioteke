package org.example.model;

import java.time.LocalDate;

public class Fine {
    private static final double DAILY_RATE = 0.50;

    private Loan loan;
    private double amount;
    private LocalDate issuedDate;
    private boolean paid;


    public Fine(Loan loan, LocalDate issuedDate) {
        this.loan = loan;
        this.issuedDate = issuedDate;
        this.amount = calculateAmount(loan);
        this.paid = false;

    }
    private double  calculateAmount(Loan loan) {
        return loan.getDaysOverdue() * DAILY_RATE;

    }

    public void markPaid() {
        this.paid = true;
    }
    public boolean isPaid() { return paid; }
    public double getAmount() { return amount; }
    public LocalDate getIssuedDate() { return issuedDate; }
    public Loan getLoan() { return loan; }
}
