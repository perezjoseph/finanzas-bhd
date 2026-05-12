package com.pfa.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CategoryTest {

    @Test
    void validCategory() {
        var cat = new Category("Food & Dining", false);
        assertEquals("Food & Dining", cat.name());
        assertFalse(cat.isCustom());
    }

    @Test
    void customCategory() {
        var cat = new Category("My Custom", true);
        assertEquals("My Custom", cat.name());
        assertTrue(cat.isCustom());
    }

    @Test
    void singleCharNameIsValid() {
        var cat = new Category("X", false);
        assertEquals("X", cat.name());
    }

    @Test
    void fiftyCharNameIsValid() {
        String name = "A".repeat(50);
        var cat = new Category(name, false);
        assertEquals(50, cat.name().length());
    }

    @Test
    void nullNameThrows() {
        assertThrows(NullPointerException.class, () -> new Category(null, false));
    }

    @Test
    void emptyNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Category("", false));
    }

    @Test
    void blankNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Category("   ", false));
    }

    @Test
    void nameExceeding50CharsThrows() {
        String name = "A".repeat(51);
        assertThrows(IllegalArgumentException.class, () -> new Category(name, false));
    }

    @Test
    void predefinedCategoriesContains18Entries() {
        assertEquals(18, Categories.ALL.size());
    }

    @Test
    void predefinedCategoriesAreNotCustom() {
        for (Category cat : Categories.ALL) {
            assertFalse(cat.isCustom(), cat.name() + " should not be custom");
        }
    }

    @Test
    void predefinedCategoriesHaveExpectedNames() {
        var names = Categories.ALL.stream().map(Category::name).toList();
        assertTrue(names.contains("Food & Dining"));
        assertTrue(names.contains("Groceries"));
        assertTrue(names.contains("Utilities"));
        assertTrue(names.contains("Rent/Housing"));
        assertTrue(names.contains("Transportation"));
        assertTrue(names.contains("Fuel"));
        assertTrue(names.contains("Entertainment"));
        assertTrue(names.contains("Travel"));
        assertTrue(names.contains("Healthcare"));
        assertTrue(names.contains("Shopping"));
        assertTrue(names.contains("Income"));
        assertTrue(names.contains("Investments"));
        assertTrue(names.contains("Transfers"));
        assertTrue(names.contains("Fees"));
        assertTrue(names.contains("Subscriptions"));
        assertTrue(names.contains("Cash Withdrawals"));
        assertTrue(names.contains("Taxes"));
        assertTrue(names.contains("Miscellaneous"));
    }
}
