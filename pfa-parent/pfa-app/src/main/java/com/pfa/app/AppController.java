package com.pfa.app;

import java.io.File;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.pfa.core.AccountAssignment;
import com.pfa.core.Budget;
import com.pfa.core.BudgetPeriod;
import com.pfa.core.BudgetStatus;
import com.pfa.core.Category;
import com.pfa.core.CategoryBreakdown;
import com.pfa.core.Currency;
import com.pfa.core.ExportException;
import com.pfa.core.FetchResult;
import com.pfa.core.FilterCriteria;
import com.pfa.core.GmailAccount;
import com.pfa.core.ImportBatchResult;
import com.pfa.core.ImportSource;
import com.pfa.core.Money;
import com.pfa.core.MonthlyTrends;
import com.pfa.core.SessionHandle;
import com.pfa.core.SpendingAlert;
import com.pfa.core.TransactionSet;
import com.pfa.ui.AppShell;
import com.pfa.ui.BudgetsView;
import com.pfa.ui.DashboardView;
import com.pfa.ui.FilterPanel;
import com.pfa.ui.ImportDialog;
import com.pfa.ui.SettingsView;
import com.pfa.ui.TransactionsView;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Main application controller connecting the UI shell to the service facade.
 * Wires toolbar buttons, navigation, filter panel, and data refresh.
 */
public class AppController {

    private static final BigDecimal MAX_RATE = new BigDecimal("999999");
    private static final String INVALID_BUDGET = "Presupuesto Inválido";
    private static final String ADD_RULE = "Agregar Regla";
    private static final String VAULT_MODE = "Modo Bóveda";
    private static final java.time.format.DateTimeFormatter MONTH_YEAR_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("MMM yyyy");

    private final ServiceFacade facade;
    private final Stage primaryStage;
    private final AppShell shell;

    // Views
    private final DashboardView dashboardView;
    private final TransactionsView transactionsView;
    private final BudgetsView budgetsView;
    private final SettingsView settingsView;
    private final FilterPanel filterPanel;

    public AppController(ServiceFacade facade, Stage primaryStage) {
        this.facade = facade;
        this.primaryStage = primaryStage;
        this.shell = new AppShell();

        // Create views
        this.dashboardView = new DashboardView();
        this.transactionsView = new TransactionsView();
        this.transactionsView.setCategorizationEngine(facade.getCategorizationEngine());
        this.budgetsView = new BudgetsView();
        this.settingsView = new SettingsView();
        this.filterPanel = new FilterPanel();

        wireToolbar();
        wireNavigation();
        wireFilterPanel();
        wireCurrencySelector();
        wireBudgetsView();
        wireDashboardControls();
        wireTransactionsView();
        updateStatusBar();
    }

    /**
     * Shows the application window with the dashboard as the initial view.
     */
    public void show() {
        // Place filter panel on the right side
        shell.getRoot().setRight(filterPanel);

        // Show dashboard initially
        showView(dashboardView);
        shell.show(primaryStage);

        primaryStage.setOnCloseRequest(e -> {
            facade.shutdown();
            Platform.exit();
        });

        // Wire settings view buttons
        wireSettingsView();

        // Initial data refresh
        refreshDashboard();
    }

    // --- Wiring ---

    private void wireToolbar() {
        shell.getImportButton().setOnAction(e -> handleImport());
        shell.getGmailButton().setOnAction(e -> handleGmailFetch());
        shell.getExportCsvButton().setOnAction(e -> handleExportCsv());
        shell.getExportExcelButton().setOnAction(e -> handleExportExcel());
    }

    private void wireNavigation() {
        shell.getDashboardBtn().setOnAction(e -> {
            showView(dashboardView);
            refreshDashboard();
        });
        shell.getTransactionsBtn().setOnAction(e -> {
            showView(transactionsView);
            refreshTransactions();
        });
        shell.getBudgetsBtn().setOnAction(e -> {
            showView(budgetsView);
            refreshBudgets();
        });
        shell.getSessionsBtn().setOnAction(e -> showView(settingsView));
        shell.getSettingsBtn().setOnAction(e -> showView(settingsView));
    }

    private void wireFilterPanel() {
        filterPanel.criteriaProperty().addListener((obs, oldVal, newVal) -> {
            System.out.println("[FILTER] Criteria changed: " + newVal);
            refreshDashboard();
            refreshTransactions();
            updateStatusBar();
        });

        // Populate filter panel lists
        refreshFilterPanelOptions();
    }

    /**
     * Refreshes the account and category options in the filter panel.
     * Call after importing transactions or loading a session.
     */
    private void refreshFilterPanelOptions() {
        // Populate accounts from imported transactions
        Set<String> accountIds = facade.getAllTransactions().stream()
                .map(com.pfa.core.Transaction::accountId)
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        filterPanel.setAvailableAccounts(accountIds);

        // Populate categories from categorization engine
        filterPanel.setAvailableCategories(facade.getCategorizationEngine().allCategories());
    }

    private void wireCurrencySelector() {
        shell.getCurrencySelector().setOnAction(e -> {
            String selected = shell.getCurrencySelector().getValue();
            if (selected != null) {
                facade.setBaseCurrency(Currency.valueOf(selected));
                refreshDashboard();
            }
        });
    }

    private void wireTransactionsView() {
        transactionsView.setOnInternalTransferToggle(facade::setTransactionInternalTransfer);
    }

    private void wireDashboardControls() {
        dashboardView.setOnGranularityChanged(this::refreshDashboard);
    }

    private void wireBudgetsView() {
        // Populate category combo box
        List<Category> categories = facade.getCategorizationEngine().allCategories();
        budgetsView.getCategoryComboBox().getItems().clear();
        for (Category cat : categories) {
            budgetsView.getCategoryComboBox().getItems().add(cat.name());
        }

        // Wire Add Budget button
        budgetsView.getAddButton().setOnAction(e -> handleAddBudget());

        // Wire Delete Budget button
        budgetsView.getDeleteButton().setOnAction(e -> {
            java.util.UUID selectedId = budgetsView.getSelectedBudgetId();
            if (selectedId == null) {
                showInfo("Eliminar Presupuesto", "Seleccione un presupuesto de la lista primero.");
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Delete Budget");
            confirm.setHeaderText("Are you sure you want to delete this budget?");
            confirm.setContentText("This action cannot be undone.");
            java.util.Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                facade.removeBudget(selectedId);
                refreshBudgets();
            }
        });
    }

    private void handleAddBudget() {
        // Read category
        String category = budgetsView.getCategoryComboBox().getValue();
        if (category == null || category.isBlank()) {
            showError(INVALID_BUDGET, "Por favor seleccione o ingrese una categoría.");
            return;
        }
        category = category.trim();

        // Read and validate amount
        BigDecimal amount = parseAndValidateBudgetAmount();
        if (amount == null) {
            return;
        }

        // Read and validate period
        BudgetPeriod period = parseAndValidateBudgetPeriod();
        if (period == null) {
            return;
        }

        // Create and add budget
        Budget budget = new Budget(
                java.util.UUID.randomUUID(),
                category,
                new Money(amount, facade.getBaseCurrency()),
                period
        );
        facade.addBudget(budget);
        refreshBudgets();

        // Clear form fields
        budgetsView.getCategoryComboBox().setValue(null);
        budgetsView.getAmountField().clear();
        budgetsView.getPeriodSelector().setValue("Mensual");
        budgetsView.getStartDatePicker().setValue(null);
        budgetsView.getEndDatePicker().setValue(null);
    }

    /** Parses and validates the budget amount field. Returns null if invalid (error shown to user). */
    private BigDecimal parseAndValidateBudgetAmount() {
        String amountText = budgetsView.getAmountField().getText();
        if (amountText == null || amountText.isBlank()) {
            showError(INVALID_BUDGET, "Por favor ingrese un monto de presupuesto.");
            return null;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountText.trim());
        } catch (NumberFormatException ex) {
            showError(INVALID_BUDGET, "El monto debe ser un número válido.");
            return null;
        }

        BigDecimal minAmount = new BigDecimal("0.01");
        BigDecimal maxAmount = new BigDecimal("999999999.99");
        if (amount.compareTo(minAmount) < 0 || amount.compareTo(maxAmount) > 0) {
            showError(INVALID_BUDGET, "El monto debe estar entre 0.01 y 999,999,999.99.");
            return null;
        }
        return amount;
    }

    /** Parses and validates the budget period selection. Returns null if invalid (error shown to user). */
    private BudgetPeriod parseAndValidateBudgetPeriod() {
        String periodValue = budgetsView.getPeriodSelector().getValue();
        if ("Personalizado".equals(periodValue)) {
            java.time.LocalDate start = budgetsView.getStartDatePicker().getValue();
            java.time.LocalDate end = budgetsView.getEndDatePicker().getValue();
            if (start == null || end == null) {
                showError(INVALID_BUDGET, "Por favor seleccione ambas fechas para un período personalizado.");
                return null;
            }
            if (!end.isAfter(start) && !end.isEqual(start)) {
                showError(INVALID_BUDGET, "La fecha de fin debe ser igual o posterior a la fecha de inicio.");
                return null;
            }
            long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
            if (days < 1 || days > 365) {
                showError(INVALID_BUDGET, "El período personalizado debe ser entre 1 y 365 días.");
                return null;
            }
            return new BudgetPeriod.Custom(start, end);
        } else {
            return new BudgetPeriod.Monthly();
        }
    }

    private void wireSettingsView() {
        // Vault mode toggle
        wireVaultModeToggle();

        // Add Gmail account
        settingsView.getAddGmailButton().setOnAction(e -> {
            Dialog<GmailAccount> dialog = new Dialog<>();
            dialog.setTitle("Agregar Cuenta Gmail");
            dialog.setHeaderText("Ingrese su dirección de Gmail y Contraseña de Aplicación");
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            TextField emailField = new TextField();
            emailField.setPromptText("email@gmail.com");
            PasswordField passwordField = new PasswordField();
            passwordField.setPromptText("App Password");
            grid.add(new Label("Email:"), 0, 0);
            grid.add(emailField, 1, 0);
            grid.add(new Label("Contraseña de App:"), 0, 1);
            grid.add(passwordField, 1, 1);
            dialog.getDialogPane().setContent(grid);

            dialog.setResultConverter(btn -> {
                if (btn == ButtonType.OK && !emailField.getText().isBlank()
                        && !passwordField.getText().isBlank()) {
                    return new GmailAccount(emailField.getText().trim(),
                            passwordField.getText().toCharArray());
                }
                return null;
            });

            Optional<GmailAccount> result = dialog.showAndWait();
            result.ifPresent(account -> {
                facade.saveGmailAccount(account);
                settingsView.getGmailAccountsList().getItems().add(account.email());
            });
        });

        // Remove Gmail account
        settingsView.getRemoveGmailButton().setOnAction(e -> {
            String selected = settingsView.getGmailAccountsList().getSelectionModel().getSelectedItem();
            if (selected != null) {
                facade.removeGmailAccount(selected);
                settingsView.getGmailAccountsList().getItems().remove(selected);
                settingsView.removeFetchRulesForAccount(selected);
            }
        });

        // Add fetch rule for selected account
        settingsView.getAddRuleButton().setOnAction(e -> {
            String selectedAccount = settingsView.getGmailAccountsList().getSelectionModel().getSelectedItem();
            if (selectedAccount == null) {
                showInfo(ADD_RULE, "Seleccione una cuenta Gmail primero.");
                return;
            }
            String sender = settingsView.getSenderPatternField().getText();
            String subject = settingsView.getSubjectPatternField().getText();
            if (sender == null || sender.isBlank()) {
                showError(ADD_RULE, "El patrón de remitente es requerido.");
                return;
            }
            if (subject == null || subject.isBlank()) {
                showError(ADD_RULE, "El patrón de asunto es requerido.");
                return;
            }

            java.time.LocalDate fromDate = settingsView.getDateFromPicker().getValue();
            java.time.LocalDate toDate = settingsView.getDateToPicker().getValue();
            Optional<com.pfa.core.DateRange> dateRange = Optional.empty();
            if (fromDate != null && toDate != null) {
                if (toDate.isBefore(fromDate)) {
                    showError(ADD_RULE, "La fecha Hasta debe ser igual o posterior a la fecha Desde.");
                    return;
                }
                dateRange = Optional.of(new com.pfa.core.DateRange(fromDate, toDate));
            } else if (fromDate != null || toDate != null) {
                showError(ADD_RULE, "Ambas fechas Desde y Hasta deben proporcionarse, o dejar ambas vacías.");
                return;
            }

            com.pfa.core.FetchRule rule = new com.pfa.core.FetchRule(sender.trim(), subject.trim(), dateRange);
            settingsView.addFetchRule(selectedAccount, rule);

            // Clear input fields
            settingsView.getSenderPatternField().clear();
            settingsView.getSubjectPatternField().clear();
            settingsView.getDateFromPicker().setValue(null);
            settingsView.getDateToPicker().setValue(null);
        });

        // Remove selected fetch rule
        settingsView.getRemoveRuleButton().setOnAction(e -> settingsView.removeSelectedFetchRule());

        // Edit selected fetch rule — populate fields with current values for modification
        settingsView.getEditRuleButton().setOnAction(e -> {
            com.pfa.core.FetchRule selectedRule = settingsView.getSelectedFetchRule();
            if (selectedRule == null) {
                showInfo("Editar Regla", "Seleccione una regla de la lista primero.");
                return;
            }
            // Populate input fields with the selected rule's values
            settingsView.getSenderPatternField().setText(selectedRule.senderPattern());
            settingsView.getSubjectPatternField().setText(selectedRule.subjectPattern());
            if (selectedRule.dateRange().isPresent()) {
                settingsView.getDateFromPicker().setValue(selectedRule.dateRange().get().start());
                settingsView.getDateToPicker().setValue(selectedRule.dateRange().get().end());
            } else {
                settingsView.getDateFromPicker().setValue(null);
                settingsView.getDateToPicker().setValue(null);
            }
            // Remove the old rule so the user can re-add with modifications
            settingsView.removeSelectedFetchRule();
        });

        // Fetch from Gmail
        settingsView.getFetchButton().setOnAction(e -> {
            String selected = settingsView.getGmailAccountsList().getSelectionModel().getSelectedItem();
            if (selected == null) {
                showInfo("Gmail Fetch", "Seleccione una cuenta Gmail primero.");
                return;
            }
            GmailAccount account = facade.getGmailAccount(selected);
            if (account == null) {
                showError("Gmail Fetch", "No hay credenciales guardadas para " + selected + ". Vuelva a agregar la cuenta en Configuración.");
                return;
            }
            List<com.pfa.core.FetchRule> rules = settingsView.getFetchRulesForAccount(selected);
            FetchResult fetchResult = facade.fetchGmail(account, rules);
            showFetchResultsDialog(fetchResult);
        });

        // Save Session
        settingsView.getSaveSessionButton().setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Guardar Sesión");
            dialog.setHeaderText("Ingrese un nombre para esta sesión (máximo 100 caracteres):");
            dialog.setContentText("Nombre:");

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(name -> {
                if (name.isBlank() || name.length() > 100) {
                    showError("Nombre Inválido", "El nombre de la sesión debe tener entre 1 y 100 caracteres.");
                    return;
                }
                String trimmedName = name.trim();

                // Check if session name already exists and confirm overwrite
                boolean exists = facade.listSessions().stream()
                        .anyMatch(h -> h.name().equals(trimmedName));
                if (exists) {
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                    confirm.setTitle("Sobrescribir Sesión");
                    confirm.setHeaderText("Ya existe una sesión llamada '" + trimmedName + "'.");
                    confirm.setContentText("¿Desea sobrescribirla?");
                    Optional<ButtonType> overwrite = confirm.showAndWait();
                    if (overwrite.isEmpty() || overwrite.get() != ButtonType.OK) {
                        return;
                    }
                }

                facade.saveSession(trimmedName);
                refreshSessionsList();
                showInfo("Sesión Guardada", "Sesión '" + trimmedName + "' guardada exitosamente.");
            });
        });

        // Load Session
        settingsView.getLoadSessionButton().setOnAction(e -> {
            String selected = settingsView.getSessionsList().getSelectionModel().getSelectedItem();
            if (selected == null) {
                showInfo("Cargar Sesión", "Seleccione una sesión de la lista primero.");
                return;
            }
            List<SessionHandle> sessions = facade.listSessions();
            sessions.stream()
                    .filter(h -> h.name().equals(selected))
                    .findFirst()
                    .ifPresent(handle -> {
                        facade.loadSession(handle);
                        refreshFilterPanelOptions();
                        refreshDashboard();
                        refreshTransactions();
                        refreshBudgets();
                        updateStatusBar();
                        showInfo("Sesión Cargada", "Sesión '" + selected + "' cargada exitosamente.");
                    });
        });

        // Save Rates
        settingsView.getSaveRatesButton().setOnAction(e -> {
            try {
                BigDecimal usdToDop = new BigDecimal(settingsView.getUsdToDopField().getText().trim());
                BigDecimal usdToBbd = new BigDecimal(settingsView.getUsdToBbdField().getText().trim());
                BigDecimal dopToBbd = new BigDecimal(settingsView.getDopToBbdField().getText().trim());

                // Validate rates
                if (usdToDop.compareTo(BigDecimal.ZERO) <= 0 || usdToDop.compareTo(MAX_RATE) > 0
                        || usdToBbd.compareTo(BigDecimal.ZERO) <= 0 || usdToBbd.compareTo(MAX_RATE) > 0
                        || dopToBbd.compareTo(BigDecimal.ZERO) <= 0 || dopToBbd.compareTo(MAX_RATE) > 0) {
                    showError("Tasa Inválida", "Las tasas de cambio deben ser mayores que 0 y como máximo 999,999.");
                    return;
                }

                facade.setExchangeRate(Currency.USD, Currency.DOP, usdToDop);
                facade.setExchangeRate(Currency.USD, Currency.BBD, usdToBbd);
                facade.setExchangeRate(Currency.DOP, Currency.BBD, dopToBbd);
                showInfo("Tasas Guardadas", "Tasas de cambio actualizadas exitosamente.");
                refreshDashboard();
            } catch (NumberFormatException ex) {
                showError("Entrada Inválida", "Por favor ingrese tasas de cambio numéricas válidas.");
            }
        });

        // Populate sessions list on view load
        refreshSessionsList();

        // PDF Password
        settingsView.getSavePdfPasswordButton().setOnAction(e -> {
            String password = settingsView.getPdfPasswordField().getText();
            facade.setPdfPassword(password);
            showInfo("Contraseña PDF Guardada", "La contraseña del PDF se aplicará automáticamente al importar estados de cuenta encriptados.");
        });

        // Set OCR status
        settingsView.setOcrStatus(facade.getOcrMode().name());
    }

    private void refreshSessionsList() {
        settingsView.getSessionsList().getItems().clear();
        List<SessionHandle> sessions = facade.listSessions();
        for (SessionHandle handle : sessions) {
            settingsView.getSessionsList().getItems().add(handle.name());
        }
    }

    /**
     * Wires the vault mode toggle checkbox.
     * When toggled ON: prompts for a password and enables vault mode.
     * When toggled OFF: prompts for the current password and disables vault mode.
     */
    private void wireVaultModeToggle() {
        // Set initial state from VaultManager
        settingsView.getVaultModeToggle().setSelected(facade.getVaultManager().isVaultEnabled());

        settingsView.getVaultModeToggle().selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (Boolean.TRUE.equals(isSelected)) {
                handleEnableVault();
            } else {
                handleDisableVault();
            }
        });
    }

    private void handleEnableVault() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Activar Modo Bóveda");
        dialog.setHeaderText("Establezca una contraseña para proteger sus datos.");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Contraseña");
        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Confirmar Contraseña");
        grid.add(new Label("Contraseña:"), 0, 0);
        grid.add(passwordField, 1, 0);
        grid.add(new Label("Confirmar:"), 0, 1);
        grid.add(confirmField, 1, 1);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                return passwordField.getText();
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().isBlank()) {
            String password = result.get();
            if (!password.equals(confirmField.getText())) {
                showError(VAULT_MODE, "Las contraseñas no coinciden.");
                settingsView.getVaultModeToggle().setSelected(false);
                return;
            }
            facade.getVaultManager().enableVault(password);
            showInfo(VAULT_MODE, "Modo bóveda activado. Necesitará esta contraseña para acceder a sus datos.");
        } else {
            // User cancelled — revert toggle
            settingsView.getVaultModeToggle().setSelected(false);
        }
    }

    private void handleDisableVault() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Desactivar Modo Bóveda");
        dialog.setHeaderText("Ingrese su contraseña de bóveda para desactivar el modo bóveda.");
        dialog.setContentText("Contraseña:");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && facade.getVaultManager().disableVault(result.get())) {
            showInfo(VAULT_MODE, "Modo bóveda desactivado.");
        } else {
            if (result.isPresent()) {
                showError(VAULT_MODE, "Contraseña incorrecta. El modo bóveda permanece activado.");
            }
            // Revert toggle
            settingsView.getVaultModeToggle().setSelected(true);
        }
    }

    // --- View switching ---

    private void showView(Node view) {
        shell.getContentArea().getChildren().clear();
        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane(view);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        shell.getContentArea().getChildren().add(scrollPane);
    }

    // --- Import ---

    private void handleImport() {
        ImportDialog dialog = new ImportDialog(primaryStage);

        // Intercept the Import (OK) button to prevent dialog closure and run import with progress
        javafx.scene.control.Button importButton =
                (javafx.scene.control.Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        importButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume(); // Prevent dialog from closing

            List<java.nio.file.Path> paths = dialog.getSelectedFiles();
            if (paths.isEmpty()) return;

            dialog.startImport();
            shell.setStatus("Importando " + paths.size() + " archivo(s)...");

            Task<ImportBatchResult> importTask = new Task<>() {
                @Override
                protected ImportBatchResult call() {
                    List<com.pfa.core.Transaction> allSuccesses = new java.util.ArrayList<>();
                    List<com.pfa.core.ImportError> allFailures = new java.util.ArrayList<>();
                    List<com.pfa.core.ImportWarning> allWarnings = new java.util.ArrayList<>();
                    List<String> allDuplicates = new java.util.ArrayList<>();
                    List<String> allEmptyFiles = new java.util.ArrayList<>();

                    for (int i = 0; i < paths.size(); i++) {
                        final int index = i;
                        Platform.runLater(() -> dialog.setFileStatus(index, ImportDialog.FileStatus.PROCESSING));

                        try {
                            // Auto-detect account type based on PDF format
                            byte[] fileBytes = java.nio.file.Files.readAllBytes(paths.get(i));
                            com.pfa.core.FormatDescriptor format = new com.pfa.import_.DefaultFormatDetector().detect(fileBytes, paths.get(i).getFileName().toString());
                            com.pfa.core.AccountKind kind = (format.format() == com.pfa.core.SourceFormat.BHD_CREDIT_CARD)
                                    ? com.pfa.core.AccountKind.CREDIT_CARD
                                    : com.pfa.core.AccountKind.SAVINGS;
                            String accountId = (kind == com.pfa.core.AccountKind.CREDIT_CARD)
                                    ? "bhd-credit-card"
                                    : "bhd-savings";

                            ImportSource source = new ImportSource.LocalFile(
                                    paths.get(i),
                                    new AccountAssignment(
                                            accountId,
                                            com.pfa.core.Bank.BHD,
                                            kind));

                            ImportBatchResult fileResult = facade.importFiles(List.of(source));

                            allSuccesses.addAll(fileResult.successes());
                            allWarnings.addAll(fileResult.warnings());

                            if (!fileResult.failures().isEmpty()) {
                                allFailures.addAll(fileResult.failures());
                                String errorMsg = fileResult.failures().stream()
                                        .map(com.pfa.core.ImportError::message)
                                        .reduce((a, b) -> a + "; " + b)
                                        .orElse("Unknown error");
                                final String msg = errorMsg;
                                Platform.runLater(() -> dialog.setFileStatus(index,
                                        ImportDialog.FileStatus.FAILED, msg));
                            } else if (!fileResult.duplicates().isEmpty()) {
                                allDuplicates.addAll(fileResult.duplicates());
                                Platform.runLater(() -> dialog.setFileStatus(index,
                                        ImportDialog.FileStatus.FAILED, "Archivo previamente importado (duplicado)"));
                            } else if (!fileResult.emptyFiles().isEmpty()) {
                                allEmptyFiles.addAll(fileResult.emptyFiles());
                                Platform.runLater(() -> dialog.setFileStatus(index,
                                        ImportDialog.FileStatus.FAILED, "No se encontraron transacciones en el archivo"));
                            } else {
                                Platform.runLater(() -> dialog.setFileStatus(index,
                                        ImportDialog.FileStatus.COMPLETED));
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            allFailures.add(new com.pfa.core.ImportError(
                                    paths.get(index).getFileName().toString(),
                                    com.pfa.core.ErrorCode.CORRUPTED_FILE,
                                    "Unexpected error: " + ex.getMessage()));
                            final String msg = ex.getMessage() != null ? ex.getMessage() : "Error inesperado";
                            Platform.runLater(() -> dialog.setFileStatus(index,
                                    ImportDialog.FileStatus.FAILED, msg));
                        }
                    }

                    return new ImportBatchResult(allSuccesses, allFailures, allWarnings,
                            allDuplicates, allEmptyFiles);
                }
            };

            importTask.setOnSucceeded(e -> {
                ImportBatchResult batchResult = importTask.getValue();
                int failFileCount = batchResult.failures().size()
                        + batchResult.duplicates().size()
                        + batchResult.emptyFiles().size();
                int successFileCount = paths.size() - failFileCount;

                dialog.importComplete(Math.max(successFileCount, 0), failFileCount);
                updateStatusBar();
                refreshFilterPanelOptions();
                refreshDashboard();
                refreshTransactions();
                shell.setStatus("Listo");
            });

            importTask.setOnFailed(e -> {
                Throwable ex = importTask.getException();
                if (ex != null) {
                    System.err.println("Import task failed with exception:");
                    ex.printStackTrace();
                }
                for (int i = 0; i < dialog.getFileCount(); i++) {
                    dialog.setFileStatus(i, ImportDialog.FileStatus.FAILED,
                            "Importación abortada por error inesperado");
                }
                dialog.importComplete(0, dialog.getFileCount());
                shell.setStatus("Importación fallida");
            });

            facade.getImportPool().submit(importTask);
        });

        dialog.showAndWait();
    }

    // --- Gmail ---

    private void handleGmailFetch() {
        // If accounts are configured, fetch from the first one; otherwise redirect to Settings
        if (!settingsView.getGmailAccountsList().getItems().isEmpty()) {
            String firstAccount = settingsView.getGmailAccountsList().getItems().get(0);
            GmailAccount account = facade.getGmailAccount(firstAccount);
            if (account == null) {
                showError("Gmail Fetch", "No hay credenciales guardadas para " + firstAccount + ". Vuelva a agregar la cuenta en Configuración.");
                return;
            }
            List<com.pfa.core.FetchRule> rules = settingsView.getFetchRulesForAccount(firstAccount);
            FetchResult fetchResult = facade.fetchGmail(account, rules);
            showFetchResultsDialog(fetchResult);
        } else {
            showInfo("Gmail Fetch",
                    "Configure su cuenta Gmail en Configuración primero, luego use Obtener para descargar estados de cuenta.");
        }
    }

    /**
     * Shows a dialog summarizing the Gmail fetch results and auto-imports any downloaded PDFs
     * through the standard import pipeline.
     */
    private void showFetchResultsDialog(FetchResult fetchResult) {
        StringBuilder msg = new StringBuilder();
        msg.append("Descargados: ").append(fetchResult.downloaded().size()).append(" PDF(s)\n");

        if (!fetchResult.errors().isEmpty()) {
            msg.append("\nErrores de búsqueda:\n");
            for (String error : fetchResult.errors()) {
                msg.append("  \u2022 ").append(error).append("\n");
            }
        }

        // Auto-import downloaded PDFs through the standard import pipeline
        if (!fetchResult.downloaded().isEmpty()) {
            ImportBatchResult importResult = facade.importFiles(fetchResult.downloaded());

            int filesImported = fetchResult.downloaded().size()
                    - importResult.failures().size()
                    - importResult.duplicates().size()
                    - importResult.emptyFiles().size();
            msg.append("\nResultados de importación:\n");
            msg.append("  Importados exitosamente: ").append(Math.max(filesImported, 0)).append(" PDF(s)\n");
            msg.append("  Transacciones extraídas: ").append(importResult.successes().size()).append("\n");

            if (!importResult.failures().isEmpty()) {
                msg.append("  Fallidos: ").append(importResult.failures().size()).append("\n");
                for (com.pfa.core.ImportError err : importResult.failures()) {
                    msg.append("    \u2022 ").append(err.message()).append("\n");
                }
            }
            if (!importResult.duplicates().isEmpty()) {
                msg.append("  Duplicados omitidos: ").append(importResult.duplicates().size()).append("\n");
            }
            if (!importResult.emptyFiles().isEmpty()) {
                msg.append("  Archivos vacíos: ").append(importResult.emptyFiles().size()).append("\n");
            }

            // Refresh views after successful import
            refreshFilterPanelOptions();
            refreshDashboard();
            refreshTransactions();
            updateStatusBar();
        }

        showInfo("Resultado Gmail Fetch", msg.toString());
    }

    // --- Export ---

    private void handleExportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar a CSV");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Archivos CSV", "*.csv"));
        chooser.setInitialFileName("transactions.csv");

        File file = chooser.showSaveDialog(primaryStage);
        if (file != null) {
            try {
                TransactionSet txs = getCurrentTransactionSet();
                facade.exportCsv(file.toPath(), txs, filterPanel.criteriaProperty().get());
                showInfo("Exportación Completa", "Exportado a: " + file.getAbsolutePath());
            } catch (ExportException e) {
                showError("Exportación Fallida", e.getMessage());
            }
        }
    }

    private void handleExportExcel() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar a Excel");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Archivos Excel", "*.xlsx"));
        chooser.setInitialFileName("transactions.xlsx");

        File file = chooser.showSaveDialog(primaryStage);
        if (file != null) {
            try {
                TransactionSet txs = getCurrentTransactionSet();
                facade.exportExcel(file.toPath(), txs, filterPanel.criteriaProperty().get());
                showInfo("Exportación Completa", "Exportado a: " + file.getAbsolutePath());
            } catch (ExportException e) {
                showError("Exportación Fallida", e.getMessage());
            }
        }
    }

    // --- Data refresh ---

    private void refreshDashboard() {
        try {
            refreshDashboardInternal();
        } catch (com.pfa.core.MissingRateException ex) {
            dashboardView.showEmptyState(
                    "Tasas de cambio no configuradas. Por favor establezca las tasas en Configuración antes de ver el panel.\n"
                    + "Faltante: " + ex.getMessage());
        }
    }

    private void refreshDashboardInternal() {
        TransactionSet txs = getCurrentTransactionSet();

        if (txs.transactions().isEmpty()) {
            dashboardView.showEmptyState("Sin transacciones para mostrar. Importe estados de cuenta para comenzar.");
            return;
        }

        // Rebuild the dashboard view structure if it was cleared by showEmptyState
        dashboardView.rebuild();

        // Update pie chart with "Other" grouping for categories < 2% of total
        // and separate segments per currency when transactions span multiple currencies
        CategoryBreakdown breakdown = facade.getCategoryBreakdown(txs);
        dashboardView.getCategoryPieChart().getData().clear();

        // Use PieChartGrouping to group categories < 2% into "Other"
        PieChartGrouping.GroupedData grouped = PieChartGrouping.group(breakdown);
        for (var entry : grouped.segments().entrySet()) {
            PieChart.Data data = new PieChart.Data(
                    entry.getKey() + " " + entry.getValue().toPlainString(),
                    entry.getValue().doubleValue());
            dashboardView.getCategoryPieChart().getData().add(data);
        }

        // Update trend chart — separate series per currency when multi-currency
        // Respects the selected granularity (Daily/Weekly/Monthly), defaulting to last 6 months
        com.pfa.core.TrendGranularity granularity = dashboardView.getSelectedGranularity();
        dashboardView.getTrendChart().getData().clear();
        // Update x-axis label to reflect granularity
        dashboardView.getTrendChart().getXAxis().setLabel(granularity.toString());

        // Determine the date window: last 6 months from today
        java.time.LocalDate now = java.time.LocalDate.now();
        java.time.LocalDate sixMonthsAgo = now.minusMonths(6).withDayOfMonth(1);

        // Filter transactions to the last 6 months and debits only
        List<com.pfa.core.Transaction> trendTxs = txs.transactions().stream()
                .filter(tx -> tx.direction() == com.pfa.core.Direction.DEBIT)
                .filter(tx -> !tx.date().isBefore(sixMonthsAgo))
                .toList();

        // Collect all currencies present in the trend window
        java.util.Set<com.pfa.core.Currency> trendCurrencies = trendTxs.stream()
                .map(tx -> tx.amount().currency())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        if (trendCurrencies.size() > 1) {
            // Multi-currency: build one series per currency
            java.util.Map<com.pfa.core.Currency, java.util.Map<String, BigDecimal>> byCurrencyBucket =
                    new java.util.LinkedHashMap<>();
            for (com.pfa.core.Transaction tx : trendTxs) {
                com.pfa.core.Currency cur = tx.amount().currency();
                String bucket = computeTrendBucket(tx.date(), granularity);
                byCurrencyBucket.computeIfAbsent(cur, k -> new java.util.LinkedHashMap<>())
                        .merge(bucket, tx.amount().amount(), BigDecimal::add);
            }
            for (var curEntry : byCurrencyBucket.entrySet()) {
                XYChart.Series<String, Number> currSeries = new XYChart.Series<>();
                currSeries.setName("Gastos (" + curEntry.getKey().symbol + ")");
                curEntry.getValue().forEach((bucket, amount) ->
                        currSeries.getData().add(new XYChart.Data<>(bucket, amount)));
                if (!currSeries.getData().isEmpty()) {
                    dashboardView.getTrendChart().getData().add(currSeries);
                }
            }
        } else {
            // Single currency: show one aggregated series
            java.util.Map<String, BigDecimal> bucketTotals = new java.util.LinkedHashMap<>();
            for (com.pfa.core.Transaction tx : trendTxs) {
                String bucket = computeTrendBucket(tx.date(), granularity);
                bucketTotals.merge(bucket, tx.amount().amount(), BigDecimal::add);
            }
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            String currLabel = trendCurrencies.isEmpty()
                    ? facade.getBaseCurrency().name()
                    : trendCurrencies.iterator().next().symbol;
            series.setName(granularity + " Gastos (" + currLabel + ")");
            bucketTotals.forEach((bucket, amount) ->
                    series.getData().add(new XYChart.Data<>(bucket, amount)));
            if (!series.getData().isEmpty()) {
                dashboardView.getTrendChart().getData().add(series);
            }
        }

        // Update monthly comparison bar chart
        try {
            MonthlyTrends trends = facade.getMonthlyTrends(txs);
            List<YearMonth> availableMonths = new ArrayList<>(trends.monthlyByCategory().keySet());
            dashboardView.setAvailableMonths(availableMonths);
            dashboardView.setOnMonthSelectionChanged(() -> refreshMonthlyComparison(txs));
            refreshMonthlyComparison(txs);
        } catch (com.pfa.core.MissingRateException ex) {
            dashboardView.setAvailableMonths(List.of());
        }

        // Update income vs expenses chart
        try {
            refreshIncomeVsExpenses(txs);
        } catch (com.pfa.core.MissingRateException ex) {
            // Skip income vs expenses when rates are missing
        }

        // Update alerts
        List<SpendingAlert> alerts = facade.getUnusualSpending(txs);
        dashboardView.getAlertsPanel().getChildren().clear();

        // Update recurring payments table
        List<com.pfa.core.RecurringPayment> recurring = facade.detectRecurring(txs);
        dashboardView.getRecurringPaymentsTable().getItems().setAll(recurring);
        if (alerts.isEmpty()) {
            dashboardView.getAlertsPanel().getChildren().add(new Label("No se detectaron gastos inusuales."));
        } else {
            for (SpendingAlert alert : alerts) {
                Label alertLabel = new Label("\u26a0 " + alert.categoryName() + ": "
                        + alert.currentAmount().currency().symbol
                        + alert.currentAmount().amount().toPlainString()
                        + " (avg: " + alert.historicalAverage().amount().toPlainString() + ")");
                alertLabel.setStyle("-fx-text-fill: -pfa-negative; -fx-font-weight: bold;");
                dashboardView.getAlertsPanel().getChildren().add(alertLabel);
            }
        }

        // Update budget cards
        // Update budget cards
        try {
            refreshBudgetCards(txs);
        } catch (com.pfa.core.MissingRateException ex) {
            // Skip budget cards when rates are missing
        }

        // Update savings rate display
        try {
            refreshSavingsRate(txs);
        } catch (com.pfa.core.MissingRateException ex) {
            // Skip savings rate when rates are missing
        }

        // Update forecast panel
        try {
            refreshForecast(txs);
        } catch (com.pfa.core.MissingRateException ex) {
            // Skip forecast when rates are missing
        }
    }

    private void refreshTransactions() {
        TransactionSet txs = getCurrentTransactionSet();
        System.out.println("[REFRESH] Transactions count: " + txs.transactions().size());
        transactionsView.setTransactions(txs.transactions());
    }

    private void refreshBudgets() {
        TransactionSet txs = getCurrentTransactionSet();
        List<BudgetStatus> statuses = facade.getBudgetStatuses(txs);

        budgetsView.clearBudgets();
        for (BudgetStatus status : statuses) {
            budgetsView.addBudgetCard(
                    status.budget().id(),
                    status.budget().categoryName(),
                    status.percentUsed(),
                    status.spent().amount().toPlainString(),
                    status.budget().limit().amount().toPlainString(),
                    status.isExpired()
            );
        }
    }

    private void refreshBudgetCards(TransactionSet txs) {
        List<BudgetStatus> statuses = facade.getBudgetStatuses(txs);
        dashboardView.getBudgetCards().getChildren().clear();

        if (statuses.isEmpty()) {
            dashboardView.getBudgetCards().getChildren().add(new Label("Sin presupuestos configurados."));
            return;
        }

        for (BudgetStatus status : statuses) {
            HBox card = new HBox(10);
            card.setPadding(new javafx.geometry.Insets(6));
            card.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            card.getStyleClass().add("budget-card");

            Label nameLabel = new Label(status.budget().categoryName());
            nameLabel.setPrefWidth(120);
            nameLabel.setStyle("-fx-font-weight: bold;");

            ProgressBar bar = new ProgressBar(Math.min(status.percentUsed() / 100.0, 1.0));
            bar.setPrefWidth(200);

            Label amountLabel = new Label(status.spent().amount().toPlainString()
                    + " / " + status.budget().limit().amount().toPlainString());

            Label statusLabel = new Label();
            statusLabel.getStyleClass().add("budget-status-label");

            if (status.isExpired()) {
                card.getStyleClass().add("budget-card-expired");
                statusLabel.setText("EXPIRADO");
                statusLabel.getStyleClass().add("budget-status-expired");
            } else if (status.isOverLimit()) {
                card.getStyleClass().add("budget-card-danger");
                statusLabel.setText("\u26A0 EXCEDIDO");
                statusLabel.getStyleClass().add("budget-status-danger");
            } else if (status.isWarning()) {
                card.getStyleClass().add("budget-card-warning");
                statusLabel.setText("\u26A0 ADVERTENCIA");
                statusLabel.getStyleClass().add("budget-status-warning");
            } else {
                card.getStyleClass().add("budget-card-normal");
            }

            card.getChildren().addAll(nameLabel, bar, amountLabel, statusLabel);
            dashboardView.getBudgetCards().getChildren().add(card);
        }
    }

    /**
     * Updates the forecast panel with projected end-of-month spending.
     * Shows a message if fewer than 7 days of data exist in the current month.
     */
    private void refreshForecast(TransactionSet txs) {
        dashboardView.getForecastPanel().getChildren().clear();

        com.pfa.core.MonthlyForecast forecast = facade.getForecast(txs);

        if (forecast.daysElapsed() < 7) {
            Label insufficientLabel = new Label(
                    "Datos insuficientes para pronóstico. Se necesitan al menos 7 días de datos de gastos en el mes actual ("
                    + forecast.daysElapsed() + " día(s) hasta ahora).");
            insufficientLabel.setStyle("-fx-text-fill: -pfa-text-muted; -fx-font-style: italic;");
            insufficientLabel.setWrapText(true);
            dashboardView.getForecastPanel().getChildren().add(insufficientLabel);
            return;
        }

        // Compute current spending so far this month
        java.time.YearMonth currentMonth = java.time.YearMonth.now();
        java.math.BigDecimal currentSpending = java.math.BigDecimal.ZERO;
        for (com.pfa.core.Transaction tx : txs.transactions()) {
            if (tx.direction() == com.pfa.core.Direction.DEBIT
                    && java.time.YearMonth.from(tx.date()).equals(currentMonth)) {
                currentSpending = currentSpending.add(tx.amount().amount());
            }
        }

        String currencySymbol = forecast.projectedTotal().currency().symbol;

        Label projectedLabel = new Label("Gasto proyectado a fin de mes: "
                + currencySymbol + forecast.projectedTotal().amount().toPlainString());
        projectedLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        Label currentLabel = new Label("Gasto actual hasta ahora: "
                + currencySymbol + currentSpending.toPlainString());
        currentLabel.setStyle("-fx-font-size: 12;");

        Label daysLabel = new Label("Basado en " + forecast.daysElapsed()
                + " de " + forecast.daysInMonth() + " días en este mes.");
        daysLabel.setStyle("-fx-font-size: 11; -fx-text-fill: -pfa-text-muted;");

        dashboardView.getForecastPanel().getChildren().addAll(projectedLabel, currentLabel, daysLabel);
    }

    /**
     * Updates the savings rate panel with monthly savings rate percentages.
     * Savings rate = (income - expenses) / income * 100.
     */
    private void refreshSavingsRate(TransactionSet txs) {
        dashboardView.getSavingsRatePanel().getChildren().clear();

        com.pfa.core.MonthlySavingsRate savingsRates = facade.getSavingsRates(txs);

        if (savingsRates.ratesByMonth().isEmpty()) {
            Label noDataLabel = new Label("Datos de tasa de ahorro no disponibles. Importe transacciones de ingresos para ver tasas de ahorro.");
            noDataLabel.setStyle("-fx-text-fill: -pfa-text-muted; -fx-font-style: italic;");
            noDataLabel.setWrapText(true);
            dashboardView.getSavingsRatePanel().getChildren().add(noDataLabel);
            return;
        }

        for (var entry : savingsRates.ratesByMonth().entrySet()) {
            java.time.YearMonth month = entry.getKey();
            java.math.BigDecimal rate = entry.getValue();

            String rateText;
            String style;
            if (rate == null) {
                rateText = month.format(MONTH_YEAR_FORMAT) + ": N/A (sin ingresos)";
                style = "-fx-text-fill: -pfa-text-muted;";
            } else if (rate.compareTo(java.math.BigDecimal.ZERO) >= 0) {
                rateText = month.format(MONTH_YEAR_FORMAT) + ": " + rate.toPlainString() + "%";
                style = "-fx-text-fill: -pfa-positive; -fx-font-weight: bold;";
            } else {
                rateText = month.format(MONTH_YEAR_FORMAT) + ": " + rate.toPlainString() + "%";
                style = "-fx-text-fill: -pfa-negative; -fx-font-weight: bold;";
            }

            Label rateLabel = new Label(rateText);
            rateLabel.setStyle(style);
            dashboardView.getSavingsRatePanel().getChildren().add(rateLabel);
        }
    }

    /**
     * Updates the monthly comparison bar chart with spending data for the user-selected months.
     * Shows one bar per selected month with total spending.
     */
    private void refreshMonthlyComparison(TransactionSet txs) {
        dashboardView.getMonthlyComparisonChart().getData().clear();

        List<YearMonth> selectedMonths = dashboardView.getSelectedMonths();
        if (selectedMonths.isEmpty()) {
            return;
        }

        MonthlyTrends trends = facade.getMonthlyTrends(txs);

        XYChart.Series<String, Number> spendingSeries = new XYChart.Series<>();
        spendingSeries.setName("Gasto Total (" + facade.getBaseCurrency().name() + ")");

        for (YearMonth month : selectedMonths) {
            java.util.Map<String, Money> categoryTotals = trends.monthlyByCategory().get(month);
            double monthTotal = 0;
            if (categoryTotals != null) {
                monthTotal = categoryTotals.values().stream()
                        .mapToDouble(m -> m.amount().doubleValue())
                        .sum();
            }
            spendingSeries.getData().add(new XYChart.Data<>(month.toString(), monthTotal));
        }

        dashboardView.getMonthlyComparisonChart().getData().add(spendingSeries);
    }

    /**
     * Updates the income vs expenses grouped bar chart with two series:
     * one for income (CREDIT) and one for expenses (DEBIT) per month.
     */
    private void refreshIncomeVsExpenses(TransactionSet txs) {
        dashboardView.getIncomeVsExpensesChart().getData().clear();

        com.pfa.core.IncomeVsExpenses ive = facade.getIncomeVsExpenses(txs);
        if (ive.monthlyData().isEmpty()) {
            return;
        }

        XYChart.Series<String, Number> incomeSeries = new XYChart.Series<>();
        incomeSeries.setName("Ingresos (" + facade.getBaseCurrency().name() + ")");

        XYChart.Series<String, Number> expensesSeries = new XYChart.Series<>();
        expensesSeries.setName("Gastos (" + facade.getBaseCurrency().name() + ")");

        // Sort months chronologically
        java.util.List<YearMonth> sortedMonths = new java.util.ArrayList<>(ive.monthlyData().keySet());
        java.util.Collections.sort(sortedMonths);

        for (YearMonth month : sortedMonths) {
            com.pfa.core.IncomeVsExpenses.MonthEntry entry = ive.monthlyData().get(month);
            String label = month.toString();
            incomeSeries.getData().add(new XYChart.Data<>(label, entry.income().amount().doubleValue()));
            expensesSeries.getData().add(new XYChart.Data<>(label, entry.expenses().amount().doubleValue()));
        }

        dashboardView.getIncomeVsExpensesChart().getData().add(incomeSeries);
        dashboardView.getIncomeVsExpensesChart().getData().add(expensesSeries);
    }

    // --- Helpers ---

    /**
     * Computes the bucket label for a transaction date based on the selected granularity.
     * Daily: ISO date (YYYY-MM-DD), Weekly: "YYYY-Www", Monthly: "YYYY-MM".
     */
    private String computeTrendBucket(java.time.LocalDate date, com.pfa.core.TrendGranularity granularity) {
        return switch (granularity) {
            case DAILY -> date.toString();
            case WEEKLY -> {
                java.time.temporal.WeekFields weekFields = java.time.temporal.WeekFields.ISO;
                int weekNum = date.get(weekFields.weekOfWeekBasedYear());
                int year = date.get(weekFields.weekBasedYear());
                yield String.format("%d-W%02d", year, weekNum);
            }
            case MONTHLY -> java.time.YearMonth.from(date).toString();
        };
    }

    private TransactionSet getCurrentTransactionSet() {
        FilterCriteria criteria = filterPanel.criteriaProperty().get();
        return facade.getFilteredTransactions(criteria);
    }

    private void updateStatusBar() {
        shell.setOcrMode(facade.getOcrMode().name());
        shell.setTransactionCount(facade.getTransactionCount());
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
