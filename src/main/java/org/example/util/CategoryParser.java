package org.example.util;

import org.example.model.Category;
import org.example.model.InvalidCategoryException;

public class CategoryParser {

    // Klasë vetëm me metoda statike — s'ka kuptim ta instancosh (new CategoryParser())
    private CategoryParser() {}

    public static Category fromInput(String input) throws InvalidCategoryException {
        String trimmed = input.trim();

        // Rasti 1: useri fut numër (nga menu UI)
        try {
            int index = Integer.parseInt(trimmed);
            Category[] values = Category.values();
            if (index >= 1 && index <= values.length) {
                return values[index - 1];
            }
            throw new InvalidCategoryException(trimmed);
        } catch (NumberFormatException notANumber) {
            // s'është numër, provo si tekst (rasti 2 më poshtë)
        }

        // Rasti 2: tekst (nga import CSV, migrim databaze, etj.)
        try {
            return Category.valueOf(trimmed.toUpperCase().replace(" ", "_").replace("-", "_"));
        } catch (IllegalArgumentException e) {
            throw new InvalidCategoryException(trimmed);
        }
    }
}