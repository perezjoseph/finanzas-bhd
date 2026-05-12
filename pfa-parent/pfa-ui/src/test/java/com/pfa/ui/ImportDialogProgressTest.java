package com.pfa.ui;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javafx.application.Platform;

/**
 * Unit tests for ImportDialog per-file progress display.
 * Validates status transitions (pending → processing → completed/failed)
 * and error message display for rejected files.
 */
class ImportDialogProgressTest {

    @BeforeAll
    static void initToolkit() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
            latch.await(5, TimeUnit.SECONDS);
        } catch (IllegalStateException e) {
            // Toolkit already initialized
        }
    }

    /** Runs a block on the FX thread and rethrows any assertion error on the test thread. */
    private void runOnFxAndWait(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "FX thread timed out");
        if (error.get() != null) {
            if (error.get() instanceof AssertionError ae) {
                throw ae;
            }
            fail("Unexpected exception on FX thread", error.get());
        }
    }

    @Test
    void fileStatusEnum_hasAllExpectedValues() {
        ImportDialog.FileStatus[] values = ImportDialog.FileStatus.values();
        assertEquals(4, values.length);
        assertEquals(ImportDialog.FileStatus.PENDING, values[0]);
        assertEquals(ImportDialog.FileStatus.PROCESSING, values[1]);
        assertEquals(ImportDialog.FileStatus.COMPLETED, values[2]);
        assertEquals(ImportDialog.FileStatus.FAILED, values[3]);
    }

    @Test
    void newDialog_hasNoFilesSelected() throws Exception {
        runOnFxAndWait(() -> {
            ImportDialog dialog = new ImportDialog(null);
            assertEquals(0, dialog.getFileCount());
            assertTrue(dialog.getSelectedFiles().isEmpty());
        });
    }

    @Test
    void startImport_disablesControls() throws Exception {
        runOnFxAndWait(() -> {
            ImportDialog dialog = new ImportDialog(null);
            dialog.startImport();
            javafx.scene.control.Button okButton =
                    (javafx.scene.control.Button) dialog.getDialogPane()
                            .lookupButton(javafx.scene.control.ButtonType.OK);
            assertTrue(okButton.isDisabled());
            javafx.scene.control.Button cancelButton =
                    (javafx.scene.control.Button) dialog.getDialogPane()
                            .lookupButton(javafx.scene.control.ButtonType.CANCEL);
            assertTrue(cancelButton.isDisabled());
        });
    }

    @Test
    void importComplete_reEnablesCloseButton() throws Exception {
        runOnFxAndWait(() -> {
            ImportDialog dialog = new ImportDialog(null);
            dialog.startImport();
        });
        // importComplete uses Platform.runLater internally
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            ImportDialog dialog = new ImportDialog(null);
            dialog.startImport();
            dialog.importComplete(3, 1);
            // Schedule check after the internal Platform.runLater in importComplete
            Platform.runLater(() -> {
                try {
                    javafx.scene.control.Button cancelButton =
                            (javafx.scene.control.Button) dialog.getDialogPane()
                                    .lookupButton(javafx.scene.control.ButtonType.CANCEL);
                    assertFalse(cancelButton.isDisabled());
                    assertEquals("Close", cancelButton.getText());
                } catch (Throwable t) {
                    error.set(t);
                } finally {
                    latch.countDown();
                }
            });
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "FX thread timed out");
        if (error.get() != null) {
            if (error.get() instanceof AssertionError ae) throw ae;
            fail("Unexpected exception", error.get());
        }
    }

    @Test
    void setFileStatus_outOfBounds_doesNotThrow() throws Exception {
        runOnFxAndWait(() -> {
            ImportDialog dialog = new ImportDialog(null);
            // Should not throw for out-of-bounds index
            dialog.setFileStatus(-1, ImportDialog.FileStatus.COMPLETED);
            dialog.setFileStatus(100, ImportDialog.FileStatus.FAILED, "error");
        });
    }

    @Test
    void fileProgressRow_statusTransitions() throws Exception {
        runOnFxAndWait(() -> {
            ImportDialog.FileProgressRow row = new ImportDialog.FileProgressRow("test.pdf");

            // Transition to PROCESSING
            row.setStatus(ImportDialog.FileStatus.PROCESSING);
            assertTrue(row.getStyleClass().contains("file-row-processing"),
                    "Expected processing style class, got: " + row.getStyleClass());

            // Transition to COMPLETED
            row.setStatus(ImportDialog.FileStatus.COMPLETED);
            assertTrue(row.getStyleClass().contains("file-row-completed"),
                    "Expected completed style class, got: " + row.getStyleClass());
            assertFalse(row.getStyleClass().contains("file-row-processing"),
                    "Processing class should be removed");

            // Transition to FAILED with message
            row.setStatus(ImportDialog.FileStatus.FAILED, "File exceeds 10 MB limit");
            assertTrue(row.getStyleClass().contains("file-row-failed"),
                    "Expected failed style class, got: " + row.getStyleClass());
            assertFalse(row.getStyleClass().contains("file-row-completed"),
                    "Completed class should be removed");
        });
    }

    @Test
    void fileProgressRow_failedWithMessage_showsErrorLabel() throws Exception {
        runOnFxAndWait(() -> {
            ImportDialog.FileProgressRow row = new ImportDialog.FileProgressRow("large.pdf");
            row.setStatus(ImportDialog.FileStatus.FAILED, "File exceeds 10 MB limit");

            // The error label (second child) should be visible
            javafx.scene.control.Label errorLabel =
                    (javafx.scene.control.Label) row.getChildren().get(1);
            assertTrue(errorLabel.isVisible(), "Error label should be visible");
            assertTrue(errorLabel.isManaged(), "Error label should be managed");
            assertEquals("File exceeds 10 MB limit", errorLabel.getText());
        });
    }

    @Test
    void fileProgressRow_completedStatus_hidesErrorLabel() throws Exception {
        runOnFxAndWait(() -> {
            ImportDialog.FileProgressRow row = new ImportDialog.FileProgressRow("good.pdf");
            // First set to failed with message
            row.setStatus(ImportDialog.FileStatus.FAILED, "Some error");
            // Then set to completed (no message)
            row.setStatus(ImportDialog.FileStatus.COMPLETED);

            javafx.scene.control.Label errorLabel =
                    (javafx.scene.control.Label) row.getChildren().get(1);
            assertFalse(errorLabel.isVisible(), "Error label should be hidden after completed");
            assertFalse(errorLabel.isManaged(), "Error label should not be managed after completed");
        });
    }
}
