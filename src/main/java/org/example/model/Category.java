package org.example.model;

public enum Category {
    FICTION("Letërsi (Fiction)"),
    NON_FICTION("Jo-letërsi (Non-Fiction)"),
    SCIENCE("Shkencë"),
    HISTORY("Histori"),
    BIOGRAPHY("Biografi"),
    CHILDREN("Për fëmijë"),
    REFERENCE("Referencë (vetëm në bibliotekë)");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}