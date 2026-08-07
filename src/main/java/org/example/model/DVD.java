package org.example.model;

public class DVD extends LibraryItem {
    private static final int LOAN_PERIOD_DAYS = 7;

    private int durationMinutes;

    public DVD(String id, String title, Category category, int durationMinutes) {
        super(id, title, category);
        this.durationMinutes = durationMinutes;
    }

    @Override
    public int getLoanPeriodDays() {
        return LOAN_PERIOD_DAYS;
    }

    public int getDurationMinutes() { return durationMinutes; }
}