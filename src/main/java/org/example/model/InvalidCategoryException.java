package org.example.model;

/** Hidhet kur nje kategori nga input-i ose nga DB s'njihet. */
public class InvalidCategoryException extends Exception {
    private final String invalidValue;

    /** @param invalidValue vlera qe s'u njoh */
    public InvalidCategoryException(String invalidValue) {
        super("Kategori e panjohur: '" + invalidValue + "'");
        this.invalidValue = invalidValue;
    }

    /** @return vlera qe s'u njoh */
    public String getInvalidValue() {
        return invalidValue;
    }

    /** @return lista e numeruar e kategorive te vlefshme, per t'ia shfaqur perdoruesit */
    public String buildSuggestionMessage() {
        StringBuilder sb = new StringBuilder("Kategoritë e vlefshme janë:\n");
        int i = 1;
        for (Category c : Category.values()) {
            sb.append(i++).append(". ").append(c.getDisplayName()).append("\n");
        }
        return sb.toString();
    }
}