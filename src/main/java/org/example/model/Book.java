package org.example.model;

/** Liber — huazohet per 21 dite. */
public class Book extends LibraryItem {
    private static final int LOAN_PERIOD_DAYS = 21;

    private String author;
    private String isbn;

    /**
     * @param id       ID-ja e artikullit
     * @param title    titulli
     * @param category kategoria
     * @param author   autori
     * @param isbn     ISBN-ja
     */
    public Book(String id, String title, Category category, String author, String isbn) {
        super(id, title, category);
        this.author = author;
        this.isbn = isbn;
    }

    @Override
    public int getLoanPeriodDays() {
        return LOAN_PERIOD_DAYS;
    }

    public String getAuthor() { return author; }
    public String getIsbn() { return isbn; }
}