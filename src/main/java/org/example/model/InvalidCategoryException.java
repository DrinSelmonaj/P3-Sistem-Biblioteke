package org.example.model;

public class InvalidCategoryException extends Exception {
    private final String invalidValue;

    public InvalidCategoryException(String invalidValue) {
        super("Kategori e panjohur: '" + invalidValue + "'");
        this.invalidValue = invalidValue;
    }

    public String getInvalidValue() {
        return invalidValue;
    }

    public String buildSuggestionMessage() {
        StringBuilder sb = new StringBuilder("Kategoritë e vlefshme janë:\n");
        int i = 1;
        for (Category c : Category.values()) {
            sb.append(i++).append(". ").append(c.getDisplayName()).append("\n");
        }
        return sb.toString();
    }
}