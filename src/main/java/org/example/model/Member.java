package org.example.model;

import java.util.List;
import java.util.ArrayList;
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
    public boolean isBlocked() {
        return unpaidFees > 0.0;
    }
    public boolean canBorrowMore() {
        return currentLoans.size() < MAX_LOANS && !isBlocked();
    }

    // Getter publik per MAX_LOANS — LoanService e perdor per te kontrolluar
    // huazimet aktive nga DB (loanDAO.findActiveByMember()), jo nga currentLoans
    // (qe s'eshte i sinkronizuar me DB). Kjo shmang dyfishimin e konstantes 5 diku tjeter.
    public static int getMaxLoans() {
        return MAX_LOANS;
    }

    public List<Loan> getCurrentLoans() { return currentLoans; }
    public double getUnpaidFees() { return unpaidFees; }

    public void addFine(double amount){
        unpaidFees += amount;
    }
    public void payFines(double amount){
        this.unpaidFees = Math.max(0, this.unpaidFees - amount);

    }








}