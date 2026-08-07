package org.example.model;

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

    public abstract int getLoanPeriodDays();

    public String getId() { return id; }
    public String getTitle() { return title; }
    public Category getCategory() { return category; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}