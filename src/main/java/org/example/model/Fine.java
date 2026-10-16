package org.example.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Gjobe per kthim te vonuar — 0.50 € per cdo dite vonese. */
public class Fine {
    private static final double DAILY_RATE = 0.50;

    // Njesoj si Loan.id / Reservation.id — Integer, null derisa te ruhet ne DB,
    // vendoset vetem nga DAO (RETURNING id ose mapRowToFine()).
    private Integer id;
    private Loan loan;
    private double amount;
    private LocalDate issuedDate;
    private boolean paid;
    private LocalDateTime paidDate;//null deri te paguhet gjoba

    /**
     * @param loan       huazimi i vonuar; shuma llogaritet nga ditet e tij te vonese
     * @param issuedDate data e krijimit te gjobes
     */
    public Fine(Loan loan, LocalDate issuedDate) {
        this.loan = loan;
        this.issuedDate = issuedDate;
        this.amount = calculateAmount(loan);
        this.paid = false;

    }
    private double  calculateAmount(Loan loan) {
        return amountForDaysOverdue(loan.getDaysOverdue());
    }

    /**
     * Burimi i vetem i tarifes — LoanService e perdor per gjoben automatike ne kthim,
     * qe 0.50/dite te mos jete e shkruar ne dy vende te ndryshme.
     *
     * @param daysOverdue ditet e vonese
     * @return shuma e gjobes; 0.0 per 0 ose me pak dite
     */
    public static double amountForDaysOverdue(long daysOverdue) {
        return daysOverdue > 0 ? daysOverdue * DAILY_RATE : 0.0;
    }

    /** Shenon gjoben te paguar tani. */
    public void markPaid() {
        this.paid = true;
        this.paidDate = LocalDateTime.now();
    }

    public boolean isPaid() { return paid; }
    public double getAmount() { return amount; }
    public LocalDate getIssuedDate() { return issuedDate; }
    public LocalDateTime getPaidDate() { return paidDate; }

    // Vetem DAO-t duhet ta thirrasin — perdoret ne mapRowToFine() per te lexuar
// paid_date nga DB ashtu sic eshte ruajtur, jo ta rillogarise.
    public void setPaidDate(LocalDateTime paidDate) { this.paidDate = paidDate; }

    // Vetem DAO-t duhet ta thirrasin — perdoret ne mapRowToFine() per te lexuar
// flamurin 'paid' ashtu sic eshte ne DB, pa e shoqeruar me markPaid() qe do
// vendoste paidDate=now() gabimisht ne vend te vleres historike te ruajtur.

    public void setPaid(boolean paid) { this.paid = paid; }
    public Loan getLoan() { return loan; }

    public Integer getId() { return id; }

    // Vetem DAO-t duhet ta thirrasin.
    public void setId(Integer id) { this.id = id; }

    // Vetem DAO-t duhet ta thirrasin — perdoret ne mapRowToFine() per te vendosur
    // shumen ashtu sic eshte ruajtur historikisht ne DB, jo ta rillogarise nga e para
    // (rillogaritja do te jepte rezultat tjeter nese Loan eshte kthyer/ndryshuar meanderkohe).
    public void setAmount(double amount) { this.amount = amount; }
}