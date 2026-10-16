package org.example.model;

import java.util.List;
import java.util.ArrayList;
/** Anetar i bibliotekes. Huazon, rezervon dhe paguan gjobat e veta; s'ka qasje ne inventar. */
public class Member extends Person {
    private static final int MAX_LOANS = 5;

    private List<Loan> currentLoans;
    private double unpaidFees;

    public Member(String id, String name, String email, String phone) {
        super(id, name, email, phone);
        this.currentLoans = new ArrayList<>();
        this.unpaidFees = 0.0;
    }
    @Override
    public boolean canManageInventory() {
        return false;
    }
    /**
     * @return true nese bilanci ne memorie ka gjoba te papaguara (rregulli zyrtar per
     *         huazimin kontrollohet nga {@link org.example.service.LoanService} ne DB)
     */
    public boolean isBlocked() {
        return unpaidFees > 0.0;
    }
    /**
     * @return true nese lista ne memorie ka me pak se {@link #getMaxLoans()} huazime dhe
     *         anetari s'eshte i bllokuar
     */
    public boolean canBorrowMore() {
        return currentLoans.size() < MAX_LOANS && !isBlocked();
    }

    /**
     * Kufiri i huazimeve aktive njekohesisht. LoanService e perdor per te kontrolluar
     * huazimet aktive nga DB ({@code loanDAO.findActiveByMember()}), jo nga currentLoans
     * (qe s'eshte i sinkronizuar me DB) — keshtu konstanta 5 s'dyfishohet diku tjeter.
     *
     * @return numri maksimal i huazimeve aktive
     */
    public static int getMaxLoans() {
        return MAX_LOANS;
    }

    public List<Loan> getCurrentLoans() { return currentLoans; }
    public double getUnpaidFees() { return unpaidFees; }

    /** @param amount shuma qe i shtohet bilancit ne memorie */
    public void addFine(double amount){
        unpaidFees += amount;
    }
    /**
     * Zbret bilancin ne memorie, kurre nen zero.
     *
     * @param amount shuma e paguar
     */
    public void payFines(double amount){
        this.unpaidFees = Math.max(0, this.unpaidFees - amount);

    }








}