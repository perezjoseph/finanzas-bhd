package com.pfa.ui;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Import dialog supporting multi-select file picker (1-20 PDFs/CSVs, ≤10 MB each).
 * Shows per-file progress during import with status transitions:
 * pending → processing → completed/failed.
 * Displays error messages for rejected files.
 */
public class ImportDialog extends Dialog<List<Path>> {

    /** Status of an individual file during import. */
    public enum FileStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }

    private final VBox fileProgressContainer;
    private final ScrollPane progressScrollPane;
    private final Button selectButton;
    private final Label statusLabel;
    private final List<Path> selectedFiles;
    private final List<FileProgressRow> fileRows;

    public ImportDialog(Window owner) {
        setTitle("Importar Estados de Cuenta");
        setHeaderText("Seleccione estados de cuenta PDF o CSV para importar");
        initOwner(owner);

        selectedFiles = new ArrayList<>();
        fileRows = new ArrayList<>();

        fileProgressContainer = new VBox(4);
        fileProgressContainer.setPadding(new Insets(5));

        progressScrollPane = new ScrollPane(fileProgressContainer);
        progressScrollPane.setFitToWidth(true);
        progressScrollPane.setPrefHeight(250);
        progressScrollPane.setPrefWidth(500);

        statusLabel = new Label("Ningún archivo seleccionado");

        selectButton = new Button("Seleccionar Archivos...");
        selectButton.setOnAction(e -> selectFiles(owner));

        VBox content = new VBox(10);
        content.setPadding(new Insets(15));
        content.getChildren().addAll(selectButton, progressScrollPane, statusLabel);

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Rename OK to "Import"
        Button okButton = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText("Importar");
        okButton.setDisable(true);

        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return selectedFiles;
            }
            return null;
        });
    }

    /**
     * Returns the list of selected file paths.
     */
    public List<Path> getSelectedFiles() {
        return List.copyOf(selectedFiles);
    }

    /**
     * Transitions the dialog into import mode: disables file selection and the Import button,
     * and sets all files to PENDING status.
     */
    public void startImport() {
        selectButton.setDisable(true);
        Button okButton = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okButton.setDisable(true);
        Button cancelButton = (Button) getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton.setDisable(true);
        statusLabel.setText("Importando...");
    }

    /**
     * Updates the status of a specific file by index.
     * Must be called on the JavaFX Application Thread.
     *
     * @param index  the file index (0-based)
     * @param status the new status
     */
    public void setFileStatus(int index, FileStatus status) {
        if (index >= 0 && index < fileRows.size()) {
            Platform.runLater(() -> fileRows.get(index).setStatus(status));
        }
    }

    /**
     * Updates the status and sets an error message for a specific file.
     * Must be called on the JavaFX Application Thread.
     *
     * @param index   the file index (0-based)
     * @param status  the new status (typically FAILED)
     * @param message the error message to display
     */
    public void setFileStatus(int index, FileStatus status, String message) {
        if (index >= 0 && index < fileRows.size()) {
            Platform.runLater(() -> fileRows.get(index).setStatus(status, message));
        }
    }

    /**
     * Marks the import as complete. Re-enables the Close button.
     *
     * @param successCount number of files that completed successfully
     * @param failCount    number of files that failed
     */
    public void importComplete(int successCount, int failCount) {
        Platform.runLater(() -> {
            Button cancelButton = (Button) getDialogPane().lookupButton(ButtonType.CANCEL);
            cancelButton.setDisable(false);
            cancelButton.setText("Cerrar");

            if (failCount == 0) {
                statusLabel.setText("Importación completa: " + successCount + " archivo(s) procesado(s) exitosamente.");
            } else {
                statusLabel.setText("Importación completa: " + successCount + " exitoso(s), " + failCount + " fallido(s).");
            }
        });
    }

    /**
     * Returns the number of selected files.
     */
    public int getFileCount() {
        return selectedFiles.size();
    }

    private void selectFiles(Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar Estados de Cuenta");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Estados de Cuenta", "*.pdf", "*.csv"),
                new FileChooser.ExtensionFilter("Archivos PDF", "*.pdf"),
                new FileChooser.ExtensionFilter("Archivos CSV", "*.csv")
        );

        List<File> files = chooser.showOpenMultipleDialog(owner);
        if (files != null && !files.isEmpty()) {
            selectedFiles.clear();
            fileRows.clear();
            fileProgressContainer.getChildren().clear();

            int count = Math.min(files.size(), 20);
            for (int i = 0; i < count; i++) {
                File file = files.get(i);
                Path path = file.toPath();
                selectedFiles.add(path);

                FileProgressRow row = new FileProgressRow(file.getName());
                fileRows.add(row);
                fileProgressContainer.getChildren().add(row);
            }

            statusLabel.setText(selectedFiles.size() + " archivo(s) seleccionado(s) — haga clic en Importar para comenzar.");
            Button okButton = (Button) getDialogPane().lookupButton(ButtonType.OK);
            okButton.setDisable(false);
        }
    }

    /**
     * A single row in the progress display showing filename, status indicator, and optional error message.
     */
    static final class FileProgressRow extends VBox {

        private static final String STYLE_PROCESSING = "file-row-processing";
        private static final String STYLE_COMPLETED = "file-row-completed";
        private static final String STYLE_FAILED = "file-row-failed";

        private final Label fileNameLabel;
        private final Label statusIndicator;
        private final Label errorLabel;

        FileProgressRow(String fileName) {
            setSpacing(2);
            setPadding(new Insets(4, 8, 4, 8));
            getStyleClass().add("file-row");

            HBox topRow = new HBox(10);
            topRow.setAlignment(Pos.CENTER_LEFT);

            fileNameLabel = new Label(fileName);
            fileNameLabel.setStyle("-fx-font-weight: bold;");
            HBox.setHgrow(fileNameLabel, Priority.ALWAYS);

            statusIndicator = new Label("\u23f3 Pending");
            statusIndicator.getStyleClass().add("section-subtitle");

            errorLabel = new Label();
            errorLabel.getStyleClass().add("validation-error");
            errorLabel.setWrapText(true);
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);

            topRow.getChildren().addAll(fileNameLabel, statusIndicator);
            getChildren().addAll(topRow, errorLabel);
        }

        void setStatus(FileStatus status) {
            setStatus(status, null);
        }

        void setStatus(FileStatus status, String message) {
            switch (status) {
                case PENDING -> {
                    statusIndicator.setText("⏳ Pendiente");
                    statusIndicator.setStyle("-fx-text-fill: -pfa-text-muted;");
                    getStyleClass().removeAll(STYLE_PROCESSING, STYLE_COMPLETED, STYLE_FAILED);
                }
                case PROCESSING -> {
                    statusIndicator.setText("⚙ Procesando...");
                    statusIndicator.setStyle("-fx-text-fill: -pfa-info; -fx-font-weight: bold;");
                    getStyleClass().removeAll(STYLE_PROCESSING, STYLE_COMPLETED, STYLE_FAILED);
                    getStyleClass().add(STYLE_PROCESSING);
                }
                case COMPLETED -> {
                    statusIndicator.setText("✓ Completado");
                    statusIndicator.setStyle("-fx-text-fill: -pfa-positive; -fx-font-weight: bold;");
                    getStyleClass().removeAll(STYLE_PROCESSING, STYLE_COMPLETED, STYLE_FAILED);
                    getStyleClass().add(STYLE_COMPLETED);
                }
                case FAILED -> {
                    statusIndicator.setText("✗ Fallido");
                    statusIndicator.setStyle("-fx-text-fill: -pfa-negative; -fx-font-weight: bold;");
                    getStyleClass().removeAll(STYLE_PROCESSING, STYLE_COMPLETED, STYLE_FAILED);
                    getStyleClass().add(STYLE_FAILED);
                }
            }

            if (message != null && !message.isBlank()) {
                errorLabel.setText(message);
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
            } else {
                errorLabel.setVisible(false);
                errorLabel.setManaged(false);
            }
        }
    }
}
