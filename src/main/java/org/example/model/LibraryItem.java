package org.example.model;

/**
 * Artikull i huazueshem — baza e {@link Book} dhe {@link DVD}. Ne DB:
 * {@code library_items} + {@code books}/{@code dvds}.
 */
public abstract class LibraryItem {
    private String id;
    private String title;
    private Category category;   // ndryshuar nga String
    private boolean available;

    public LibraryItem(String id, String title, Category category) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.available = true;
    }

    /**
     * Polimorfizem: cdo tip artikulli ka afatin e vet te huazimit.
     *
     * @return sa dite zgjat huazimi (21 per liber, 7 per DVD)
     */
    public abstract int getLoanPeriodDays();

    public String getId() { return id; }
    public String getTitle() { return title; }
    public Category getCategory() { return category; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}