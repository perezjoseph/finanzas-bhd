package com.pfa.app;

import java.util.Optional;

import com.pfa.persistence.VaultManager;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Main entry point for the Personal Finance Analyzer application.
 * Launches the JavaFX application.
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        ServiceFacade facade = new ServiceFacade();
        facade.initialize();

        // If vault mode is enabled, require password before showing the app
        if (facade.getVaultManager().isVaultEnabled()) {
            boolean authenticated = showVaultLoginDialog(facade.getVaultManager());
            if (!authenticated) {
                facade.shutdown();
                Platform.exit();
                return;
            }
        }

        AppController controller = new AppController(facade, primaryStage);
        controller.show();
    }

    /**
     * Shows a vault password dialog on startup. Implements 5-attempt lockout (60 seconds).
     * Returns true if authentication succeeds, false if the user cancels.
     */
    private boolean showVaultLoginDialog(VaultManager vaultManager) {
        while (true) {
            // Check if currently locked out — block until lockout expires
            if (vaultManager.isLockedOut()) {
                showLockoutDialog(vaultManager);
            }

            String password = promptForVaultPassword(vaultManager);
            if (password == null) {
                // User cancelled
                return false;
            }

            if (vaultManager.authenticate(password)) {
                return true;
            }

            // Authentication failed — show appropriate message
            if (!vaultManager.isLockedOut()) {
                int attemptsLeft = 5 - vaultManager.getFailedAttempts();
                showError("Autenticación de Bóveda",
                        "Contraseña incorrecta. " + attemptsLeft + " intento(s) restante(s).");
            }
        }
    }

    /**
     * Prompts the user for the vault password.
     * Returns the entered password, or null if the user cancelled.
     */
    private String promptForVaultPassword(VaultManager vaultManager) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Bóveda Bloqueada");
        dialog.setHeaderText("Ingrese su contraseña de bóveda para acceder a sus datos.");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox content = new VBox(10);
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Contraseña de bóveda");

        int remaining = 5 - vaultManager.getFailedAttempts();
        Label attemptsLabel = new Label("Intentos restantes: " + remaining);
        attemptsLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 11;");

        content.getChildren().addAll(passwordField, attemptsLabel);
        dialog.getDialogPane().setContent(content);

        // Focus the password field when dialog opens
        Platform.runLater(passwordField::requestFocus);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                return passwordField.getText();
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();

        if (result.isEmpty() || result.get() == null || result.get().isBlank()) {
            return null;
        }
        return result.get();
    }

    /**
     * Shows a lockout dialog with a countdown timer. Blocks until the lockout expires
     * or the user closes the dialog.
     */
    private void showLockoutDialog(VaultManager vaultManager) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Bóveda Bloqueada");
        alert.setHeaderText("Demasiados intentos fallidos.");

        long remainingSeconds = vaultManager.getLockoutRemainingSeconds();
        Label countdownLabel = new Label("Por favor espere " + remainingSeconds + " segundos antes de intentar de nuevo.");
        countdownLabel.setStyle("-fx-font-size: 13;");
        alert.getDialogPane().setContent(countdownLabel);

        // Update countdown every second
        Timeline countdown = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            long secs = vaultManager.getLockoutRemainingSeconds();
            if (secs <= 0) {
                countdownLabel.setText("Bloqueo expirado. Puede intentar de nuevo.");
                alert.close();
            } else {
                countdownLabel.setText("Por favor espere " + secs + " segundos antes de intentar de nuevo.");
            }
        }));
        countdown.setCycleCount((int) remainingSeconds + 1);
        countdown.play();

        alert.showAndWait();
        countdown.stop();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
