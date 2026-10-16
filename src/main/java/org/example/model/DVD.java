package org.example.model;

/** DVD — huazohet per 7 dite. */
public class DVD extends LibraryItem {
    private static final int LOAN_PERIOD_DAYS = 7;

    private int durationMinutes;

    /**
     * @param id              ID-ja e artikullit
     * @param title           titulli
     * @param category        kategoria
     * @param durationMinutes kohezgjatja ne minuta
     */
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