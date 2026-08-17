package org.example.model;

import java.time.LocalDate;

public class Fine {
    private static final double DAILY_RATE = 0.50;

    // Njesoj si Loan.id / Reservation.id — Integer, null derisa te ruhet ne DB,
    // vendoset vetem nga DAO (RETURNING id ose mapRowToFine()).
    private Integer id;
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

    public Integer getId() { return id; }

    // Vetem DAO-t duhet ta thirrasin.
    public void setId(Integer id) { this.id = id; }

    // Vetem DAO-t duhet ta thirrasin — perdoret ne mapRowToFine() per te vendosur
    // shumen ashtu sic eshte ruajtur historikisht ne DB, jo ta rillogarise nga e para
    // (rillogaritja do te jepte rezultat tjeter nese Loan eshte kthyer/ndryshuar meanderkohe).
    public void setAmount(double amount) { this.amount = amount; }
}