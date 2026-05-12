package com.pfa.ui;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Main application shell: top toolbar, left nav rail, center content, bottom status.
 * Styled via pfa-dark.css. No inline styles.
 */
public class AppShell {

    private final BorderPane root;
    private final StackPane contentArea;
    private final Label statusLabel;
    private final Label ocrModeLabel;
    private final Label transactionCountLabel;
    private final Label sessionLabel;

    // Toolbar buttons
    private final Button importButton;
    private final Button gmailButton;
    private final Button exportCsvButton;
    private final Button exportExcelButton;
    private final ComboBox<String> currencySelector;

    // Navigation
    private final ToggleButton dashboardBtn;
    private final ToggleButton transactionsBtn;
    private final ToggleButton budgetsBtn;
    private final ToggleButton sessionsBtn;
    private final ToggleButton settingsBtn;
    private final ToggleGroup navGroup;

    public AppShell() {
        root = new BorderPane();
        root.getStyleClass().add("app-shell");

        contentArea = new StackPane();
        contentArea.getStyleClass().add("content-area");

        statusLabel = new Label("Listo");
        ocrModeLabel = new Label("OCR: CPU");
        transactionCountLabel = new Label("Transacciones: 0");
        sessionLabel = new Label("Sin sesión");

        // Toolbar actions
        importButton = new Button("Importar");
        importButton.setTooltip(new Tooltip("Importar estados de cuenta PDF o CSV (Ctrl+I)"));

        gmailButton = new Button("Gmail");
        gmailButton.setTooltip(new Tooltip("Obtener estados de cuenta desde Gmail"));

        exportCsvButton = new Button("CSV");
        exportCsvButton.setTooltip(new Tooltip("Exportar transacciones a CSV (Ctrl+E)"));

        exportExcelButton = new Button("Excel");
        exportExcelButton.setTooltip(new Tooltip("Exportar transacciones a Excel"));

        currencySelector = new ComboBox<>();
        currencySelector.getItems().addAll("USD", "DOP", "BBD");
        currencySelector.setValue("USD");
        currencySelector.setTooltip(new Tooltip("Moneda de reporte"));

        // Navigation rail
        navGroup = new ToggleGroup();
        dashboardBtn = createNavButton("Panel", navGroup);
        transactionsBtn = createNavButton("Transacciones", navGroup);
        budgetsBtn = createNavButton("Presupuestos", navGroup);
        sessionsBtn = createNavButton("Sesiones", navGroup);
        settingsBtn = createNavButton("Configuración", navGroup);
        dashboardBtn.setSelected(true);

        root.setTop(createToolBar());
        root.setLeft(createNavPanel());
        root.setCenter(contentArea);
        root.setBottom(createStatusBar());
    }

    public Scene createScene() {
        Scene scene = new Scene(root, 1280, 720);

        // Load stylesheet
        String css = getClass().getResource("/css/pfa-dark.css").toExternalForm();
        scene.getStylesheets().add(css);

        // Global keyboard shortcuts
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.I, KeyCombination.CONTROL_DOWN),
                importButton::fire);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN),
                exportCsvButton::fire);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.DIGIT1, KeyCombination.CONTROL_DOWN),
                dashboardBtn::fire);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.DIGIT2, KeyCombination.CONTROL_DOWN),
                transactionsBtn::fire);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.DIGIT3, KeyCombination.CONTROL_DOWN),
                budgetsBtn::fire);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.DIGIT4, KeyCombination.CONTROL_DOWN),
                sessionsBtn::fire);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.DIGIT5, KeyCombination.CONTROL_DOWN),
                settingsBtn::fire);

        return scene;
    }

    public void show(Stage stage) {
        stage.setTitle("Analizador de Finanzas Personales");
        stage.setScene(createScene());
        stage.setMinWidth(1024);
        stage.setMinHeight(600);
        stage.show();
    }

    public StackPane getContentArea() {
        return contentArea;
    }

    public BorderPane getRoot() {
        return root;
    }

    // --- Toolbar accessors ---

    public Button getImportButton() {
        return importButton;
    }

    public Button getGmailButton() {
        return gmailButton;
    }

    public Button getExportCsvButton() {
        return exportCsvButton;
    }

    public Button getExportExcelButton() {
        return exportExcelButton;
    }

    public ComboBox<String> getCurrencySelector() {
        return currencySelector;
    }

    // --- Navigation accessors ---

    public ToggleButton getDashboardBtn() {
        return dashboardBtn;
    }

    public ToggleButton getTransactionsBtn() {
        return transactionsBtn;
    }

    public ToggleButton getBudgetsBtn() {
        return budgetsBtn;
    }

    public ToggleButton getSessionsBtn() {
        return sessionsBtn;
    }

    public ToggleButton getSettingsBtn() {
        return settingsBtn;
    }

    public ToggleGroup getNavGroup() {
        return navGroup;
    }

    // --- Status bar ---

    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    public void setOcrMode(String mode) {
        ocrModeLabel.setText("OCR: " + mode);
    }

    public void setTransactionCount(int count) {
        transactionCountLabel.setText("Transacciones: " + count);
    }

    public void setSessionName(String name) {
        sessionLabel.setText(name != null ? name : "Sin sesión");
    }

    private HBox createToolBar() {
        HBox toolbar = new HBox();
        toolbar.getStyleClass().add("app-toolbar");

        HBox actions = new HBox(6, importButton, gmailButton,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                exportCsvButton, exportExcelButton);
        actions.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label currLabel = new Label("Moneda:");
        HBox currencyBox = new HBox(6, currLabel, currencySelector);
        currencyBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        toolbar.getChildren().addAll(actions, spacer, currencyBox);
        return toolbar;
    }

    private VBox createNavPanel() {
        VBox nav = new VBox(2);
        nav.getStyleClass().add("nav-panel");
        nav.setPrefWidth(160);
        nav.getChildren().addAll(dashboardBtn, transactionsBtn, budgetsBtn, sessionsBtn, settingsBtn);
        return nav;
    }

    private ToggleButton createNavButton(String text, ToggleGroup group) {
        ToggleButton btn = new ToggleButton(text);
        btn.setToggleGroup(group);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.getStyleClass().add("nav-button");
        HBox.setHgrow(btn, Priority.ALWAYS);
        return btn;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(24);
        statusBar.getStyleClass().add("status-bar");
        statusBar.getChildren().addAll(statusLabel, ocrModeLabel, transactionCountLabel, sessionLabel);
        return statusBar;
    }
}
