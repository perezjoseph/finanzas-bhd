package com.pfa.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.pfa.core.DateRange;
import com.pfa.core.FetchRule;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Settings view combining:
 * - Base currency selector
 * - Exchange rate editor
 * - Vault mode toggle
 * - OCR status display
 * - Gmail configuration (accounts, fetch rules, manual fetch button)
 * - Sessions management (save, load, list)
 */
public class SettingsView extends VBox {

    private static final String SECTION_STYLE_CLASS = "section-title";

    private final ComboBox<String> baseCurrencySelector;
    private final CheckBox vaultModeToggle;
    private final Label ocrStatusLabel;
    private final ListView<String> gmailAccountsList;
    private final ListView<String> sessionsList;

    // Exposed buttons for controller wiring
    private final Button addGmailButton;
    private final Button removeGmailButton;
    private final Button fetchButton;
    private final Button saveSessionButton;
    private final Button loadSessionButton;
    private final Button saveRatesButton;

    // PDF password
    private final javafx.scene.control.PasswordField pdfPasswordField;
    private final Button savePdfPasswordButton;

    // Exchange rate fields
    private final TextField usdToDopField;
    private final TextField usdToBbdField;
    private final TextField dopToBbdField;

    // Fetch rules UI
    private final ListView<String> fetchRulesList;
    private final TextField senderPatternField;
    private final TextField subjectPatternField;
    private final DatePicker dateFromPicker;
    private final DatePicker dateToPicker;
    private final Button addRuleButton;
    private final Button editRuleButton;
    private final Button removeRuleButton;

    // Fetch rules storage: maps account email -> list of rules
    private final Map<String, List<FetchRule>> fetchRulesByAccount = new HashMap<>();

    public SettingsView() {
        setSpacing(15);
        setPadding(new Insets(15));

        Label title = new Label("Configuración");
        title.getStyleClass().add(SECTION_STYLE_CLASS);

        // === General Section ===
        Label currencyLabel = new Label("Moneda Base");
        currencyLabel.getStyleClass().add(SECTION_STYLE_CLASS);
        baseCurrencySelector = new ComboBox<>();
        baseCurrencySelector.getItems().addAll("USD", "DOP", "BBD");
        baseCurrencySelector.setValue("USD");

        Label ratesLabel = new Label("Tasas de Cambio");
        ratesLabel.getStyleClass().add(SECTION_STYLE_CLASS);
        usdToDopField = new TextField("58.50");
        usdToBbdField = new TextField("2.00");
        dopToBbdField = new TextField("0.034");
        saveRatesButton = new Button("Guardar Tasas");
        GridPane ratesGrid = createRatesGrid();

        VBox generalContent = new VBox(10);
        generalContent.setPadding(new Insets(10));
        generalContent.getChildren().addAll(currencyLabel, baseCurrencySelector, ratesLabel, ratesGrid);

        TitledPane generalPane = new TitledPane("General", generalContent);
        generalPane.setExpanded(true);
        generalPane.setCollapsible(true);

        // === Security Section ===
        Label vaultLabel = new Label("Modo Bóveda");
        vaultLabel.getStyleClass().add(SECTION_STYLE_CLASS);
        vaultModeToggle = new CheckBox("Activar Modo Bóveda (protección con contraseña)");

        Label pdfPasswordLabel = new Label("Contraseña de PDF");
        pdfPasswordLabel.getStyleClass().add(SECTION_STYLE_CLASS);
        pdfPasswordField = new javafx.scene.control.PasswordField();
        pdfPasswordField.setPromptText("Ingrese contraseña del PDF de BHD");
        savePdfPasswordButton = new Button("Guardar Contraseña PDF");

        VBox securityContent = new VBox(10);
        securityContent.setPadding(new Insets(10));
        securityContent.getChildren().addAll(vaultLabel, vaultModeToggle,
                pdfPasswordLabel, pdfPasswordField, savePdfPasswordButton);

        TitledPane securityPane = new TitledPane("Seguridad", securityContent);
        securityPane.setExpanded(false);
        securityPane.setCollapsible(true);

        // === OCR Section ===
        Label ocrLabel = new Label("Motor OCR");
        ocrLabel.getStyleClass().add(SECTION_STYLE_CLASS);
        ocrStatusLabel = new Label("Modo: CPU");

        VBox ocrContent = new VBox(10);
        ocrContent.setPadding(new Insets(10));
        ocrContent.getChildren().addAll(ocrLabel, ocrStatusLabel);

        TitledPane ocrPane = new TitledPane("OCR", ocrContent);
        ocrPane.setExpanded(false);
        ocrPane.setCollapsible(true);

        // === Gmail Section ===
        Label gmailLabel = new Label("Integración Gmail");
        gmailLabel.getStyleClass().add(SECTION_STYLE_CLASS);
        gmailAccountsList = new ListView<>();
        gmailAccountsList.setPrefHeight(100);
        addGmailButton = new Button("Agregar Cuenta Gmail");
        removeGmailButton = new Button("Eliminar");
        fetchButton = new Button("Obtener Estados de Cuenta");
        HBox gmailButtons = new HBox(10, addGmailButton, removeGmailButton, fetchButton);

        Label fetchRulesLabel = new Label("Reglas de Búsqueda (para cuenta seleccionada)");
        fetchRulesLabel.getStyleClass().add(SECTION_STYLE_CLASS);
        fetchRulesList = new ListView<>();
        fetchRulesList.setPrefHeight(80);

        senderPatternField = new TextField();
        senderPatternField.setPromptText("Patrón de remitente (ej. bhd@bhdleon.com.do)");
        subjectPatternField = new TextField();
        subjectPatternField.setPromptText("Patrón de asunto (ej. Estado de Cuenta)");
        dateFromPicker = new DatePicker();
        dateFromPicker.setPromptText("Desde (opcional)");
        dateToPicker = new DatePicker();
        dateToPicker.setPromptText("Hasta (opcional)");

        addRuleButton = new Button("Agregar Regla");
        removeRuleButton = new Button("Eliminar Regla");
        editRuleButton = new Button("Editar Regla");
        HBox ruleButtons = new HBox(10, addRuleButton, editRuleButton, removeRuleButton);

        GridPane ruleInputGrid = new GridPane();
        ruleInputGrid.setHgap(10);
        ruleInputGrid.setVgap(5);
        ruleInputGrid.add(new Label("Remitente:"), 0, 0);
        ruleInputGrid.add(senderPatternField, 1, 0);
        ruleInputGrid.add(new Label("Asunto:"), 0, 1);
        ruleInputGrid.add(subjectPatternField, 1, 1);
        ruleInputGrid.add(new Label("Desde:"), 0, 2);
        ruleInputGrid.add(dateFromPicker, 1, 2);
        ruleInputGrid.add(new Label("Hasta:"), 0, 3);
        ruleInputGrid.add(dateToPicker, 1, 3);

        // Refresh rules list when account selection changes
        gmailAccountsList.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> refreshFetchRulesList());

        VBox gmailContent = new VBox(10);
        gmailContent.setPadding(new Insets(10));
        gmailContent.getChildren().addAll(gmailLabel, gmailAccountsList, gmailButtons,
                fetchRulesLabel, fetchRulesList, ruleInputGrid, ruleButtons);

        TitledPane gmailPane = new TitledPane("Integración Gmail", gmailContent);
        gmailPane.setExpanded(false);
        gmailPane.setCollapsible(true);

        // === Sessions Section ===
        Label sessionsLabel = new Label("Sesiones");
        sessionsLabel.getStyleClass().add(SECTION_STYLE_CLASS);
        sessionsList = new ListView<>();
        sessionsList.setPrefHeight(100);
        saveSessionButton = new Button("Guardar Sesión");
        loadSessionButton = new Button("Cargar Sesión");
        HBox sessionButtons = new HBox(10, saveSessionButton, loadSessionButton);

        VBox sessionsContent = new VBox(10);
        sessionsContent.setPadding(new Insets(10));
        sessionsContent.getChildren().addAll(sessionsLabel, sessionsList, sessionButtons);

        TitledPane sessionsPane = new TitledPane("Sesiones", sessionsContent);
        sessionsPane.setExpanded(false);
        sessionsPane.setCollapsible(true);

        getChildren().addAll(title, generalPane, securityPane, ocrPane, gmailPane, sessionsPane);
    }

    // --- Accessors ---

    public ComboBox<String> getBaseCurrencySelector() {
        return baseCurrencySelector;
    }

    public CheckBox getVaultModeToggle() {
        return vaultModeToggle;
    }

    public void setOcrStatus(String status) {
        ocrStatusLabel.setText("Modo: " + status);
    }

    public ListView<String> getGmailAccountsList() {
        return gmailAccountsList;
    }

    public ListView<String> getSessionsList() {
        return sessionsList;
    }

    public Button getAddGmailButton() {
        return addGmailButton;
    }

    public Button getRemoveGmailButton() {
        return removeGmailButton;
    }

    public Button getFetchButton() {
        return fetchButton;
    }

    public Button getSaveSessionButton() {
        return saveSessionButton;
    }

    public Button getLoadSessionButton() {
        return loadSessionButton;
    }

    public Button getSaveRatesButton() {
        return saveRatesButton;
    }

    public javafx.scene.control.PasswordField getPdfPasswordField() {
        return pdfPasswordField;
    }

    public Button getSavePdfPasswordButton() {
        return savePdfPasswordButton;
    }

    public TextField getUsdToDopField() {
        return usdToDopField;
    }

    public TextField getUsdToBbdField() {
        return usdToBbdField;
    }

    public TextField getDopToBbdField() {
        return dopToBbdField;
    }

    // --- Fetch Rules Accessors ---

    public Button getAddRuleButton() {
        return addRuleButton;
    }

    public Button getRemoveRuleButton() {
        return removeRuleButton;
    }

    public Button getEditRuleButton() {
        return editRuleButton;
    }

    public TextField getSenderPatternField() {
        return senderPatternField;
    }

    public TextField getSubjectPatternField() {
        return subjectPatternField;
    }

    public DatePicker getDateFromPicker() {
        return dateFromPicker;
    }

    public DatePicker getDateToPicker() {
        return dateToPicker;
    }

    public ListView<String> getFetchRulesList() {
        return fetchRulesList;
    }

    /**
     * Adds a fetch rule for the given account email.
     */
    public void addFetchRule(String accountEmail, FetchRule rule) {
        fetchRulesByAccount.computeIfAbsent(accountEmail, k -> new ArrayList<>()).add(rule);
        refreshFetchRulesList();
    }

    /**
     * Removes the fetch rule at the given index for the currently selected account.
     */
    public void removeSelectedFetchRule() {
        String selectedAccount = gmailAccountsList.getSelectionModel().getSelectedItem();
        int selectedIndex = fetchRulesList.getSelectionModel().getSelectedIndex();
        if (selectedAccount != null && selectedIndex >= 0) {
            List<FetchRule> rules = fetchRulesByAccount.get(selectedAccount);
            if (rules != null && selectedIndex < rules.size()) {
                rules.remove(selectedIndex);
                refreshFetchRulesList();
            }
        }
    }

    /**
     * Returns the currently selected fetch rule for the selected account, or null if none selected.
     */
    public FetchRule getSelectedFetchRule() {
        String selectedAccount = gmailAccountsList.getSelectionModel().getSelectedItem();
        int selectedIndex = fetchRulesList.getSelectionModel().getSelectedIndex();
        if (selectedAccount != null && selectedIndex >= 0) {
            List<FetchRule> rules = fetchRulesByAccount.get(selectedAccount);
            if (rules != null && selectedIndex < rules.size()) {
                return rules.get(selectedIndex);
            }
        }
        return null;
    }

    /**
     * Replaces the currently selected fetch rule with the given updated rule.
     */
    public void updateSelectedFetchRule(FetchRule updatedRule) {
        String selectedAccount = gmailAccountsList.getSelectionModel().getSelectedItem();
        int selectedIndex = fetchRulesList.getSelectionModel().getSelectedIndex();
        if (selectedAccount != null && selectedIndex >= 0) {
            List<FetchRule> rules = fetchRulesByAccount.get(selectedAccount);
            if (rules != null && selectedIndex < rules.size()) {
                rules.set(selectedIndex, updatedRule);
                refreshFetchRulesList();
            }
        }
    }

    /**
     * Returns the fetch rules for the given account email.
     */
    public List<FetchRule> getFetchRulesForAccount(String accountEmail) {
        return fetchRulesByAccount.getOrDefault(accountEmail, List.of());
    }

    /**
     * Removes all fetch rules associated with the given account email.
     */
    public void removeFetchRulesForAccount(String accountEmail) {
        fetchRulesByAccount.remove(accountEmail);
        refreshFetchRulesList();
    }

    /**
     * Refreshes the fetch rules list view to show rules for the currently selected account.
     */
    private void refreshFetchRulesList() {
        fetchRulesList.getItems().clear();
        String selectedAccount = gmailAccountsList.getSelectionModel().getSelectedItem();
        if (selectedAccount == null) {
            return;
        }
        List<FetchRule> rules = fetchRulesByAccount.getOrDefault(selectedAccount, List.of());
        for (FetchRule rule : rules) {
            String display = rule.senderPattern() + " | " + rule.subjectPattern();
            if (rule.dateRange().isPresent()) {
                DateRange range = rule.dateRange().get();
                display += " | " + range.start() + " to " + range.end();
            }
            fetchRulesList.getItems().add(display);
        }
    }

    private GridPane createRatesGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(5);

        grid.add(new Label("USD \u2192 DOP:"), 0, 0);
        grid.add(usdToDopField, 1, 0);

        grid.add(new Label("USD \u2192 BBD:"), 0, 1);
        grid.add(usdToBbdField, 1, 1);

        grid.add(new Label("DOP \u2192 BBD:"), 0, 2);
        grid.add(dopToBbdField, 1, 2);

        grid.add(saveRatesButton, 1, 3);

        return grid;
    }
}
