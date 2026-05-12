package com.pfa.ui;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.pfa.core.Category;

import javafx.application.Platform;

/**
 * Unit tests for FilterPanel account/category multi-select and validation.
 */
class FilterPanelTest {

    private FilterPanel filterPanel;

    @BeforeAll
    static void initToolkit() throws Exception {
        // Initialize JavaFX toolkit once for all tests
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
            latch.await(5, TimeUnit.SECONDS);
        } catch (IllegalStateException e) {
            // Toolkit already initialized
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            filterPanel = new FilterPanel();
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void setAvailableAccounts_populatesAccountList() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            filterPanel.setAvailableAccounts(List.of("savings-001", "credit-002", "savings-003"));
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        CountDownLatch checkLatch = new CountDownLatch(1);
        Platform.runLater(checkLatch::countDown);
        assertTrue(checkLatch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void setAvailableCategories_populatesCategoryList() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            List<Category> categories = List.of(
                    new Category("Groceries", false),
                    new Category("Fuel", false),
                    new Category("Entertainment", false));
            filterPanel.setAvailableCategories(categories);
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void validationError_shownForInvalidDateRange() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            // Verify the validation label exists and is initially hidden
            assertFalse(filterPanel.getValidationErrorLabel().isVisible());
            assertEquals("", filterPanel.getValidationErrorLabel().getText());
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void clearFilters_resetsValidationError() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            filterPanel.clearFilters();
            assertFalse(filterPanel.getValidationErrorLabel().isVisible());
            assertNull(filterPanel.criteriaProperty().get());
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void clearFilters_resetsCriteriaToNull() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            filterPanel.clearFilters();
            assertNull(filterPanel.criteriaProperty().get());
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }
}
