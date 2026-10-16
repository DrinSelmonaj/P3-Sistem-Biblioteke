package org.example.model;

/** Kategorite e katalogut; ne DB ruhet emri i konstantes (p.sh. {@code SCIENCE}). */
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

    /** @return emri ne shqip per menute e CLI-se */
    public String getDisplayName() {
        return displayName;
    }
}