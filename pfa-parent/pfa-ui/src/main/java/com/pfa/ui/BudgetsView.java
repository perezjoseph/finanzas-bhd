package com.pfa.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Budgets view with CRUD for budgets: category, amount, period.
 * Shows progress bars with 80% warning and over-limit coloring.
 * Expired budgets are displayed as read-only with distinct styling.
 */
public class BudgetsView extends VBox {

    private final VBox budgetCardsContainer;
    private final ListView<String> budgetList;
    private final ComboBox<String> categoryComboBox;
    private final TextField amountField;
    private final ComboBox<String> periodSelector;
    private final DatePicker startDatePicker;
    private final DatePicker endDatePicker;
    private final HBox dateControls;
    private final Button addButton;
    private final Button deleteButton;

    public BudgetsView() {
        setSpacing(10);
        setPadding(new Insets(10));

        Label title = new Label("Gestión de Presupuestos");
        title.getStyleClass().add("section-title");

        budgetCardsContainer = new VBox(8);
        budgetCardsContainer.setPadding(new Insets(5));

        budgetList = new ListView<>();
        budgetList.setPrefHeight(300);
        VBox.setVgrow(budgetList, Priority.ALWAYS);

        categoryComboBox = new ComboBox<>();
        categoryComboBox.setEditable(true);
        categoryComboBox.setPromptText("Categoría");
        categoryComboBox.setPrefWidth(150);

        amountField = new TextField();
        amountField.setPromptText("Monto del presupuesto");
        amountField.setPrefWidth(120);

        periodSelector = new ComboBox<>();
        periodSelector.getItems().addAll("Mensual", "Personalizado");
        periodSelector.setValue("Mensual");

        startDatePicker = new DatePicker();
        startDatePicker.setPromptText("Fecha inicio");
        startDatePicker.setPrefWidth(130);

        endDatePicker = new DatePicker();
        endDatePicker.setPromptText("Fecha fin");
        endDatePicker.setPrefWidth(130);

        dateControls = new HBox(5, new Label("Desde:"), startDatePicker, new Label("Hasta:"), endDatePicker);
        dateControls.setVisible(false);
        dateControls.setManaged(false);

        // Show/hide date pickers based on period selection
        periodSelector.setOnAction(e -> {
            boolean isCustom = "Personalizado".equals(periodSelector.getValue());
            dateControls.setVisible(isCustom);
            dateControls.setManaged(isCustom);
        });

        addButton = new Button("Agregar Presupuesto");
        deleteButton = new Button("Eliminar");

        HBox controls = new HBox(10);
        controls.getChildren().addAll(categoryComboBox, amountField, periodSelector, addButton, deleteButton);

        getChildren().addAll(title, budgetCardsContainer, budgetList, controls, dateControls);
    }

    public ListView<String> getBudgetList() {
        return budgetList;
    }

    public VBox getBudgetCardsContainer() {
        return budgetCardsContainer;
    }

    public ComboBox<String> getCategoryComboBox() {
        return categoryComboBox;
    }

    public TextField getAmountField() {
        return amountField;
    }

    public ComboBox<String> getPeriodSelector() {
        return periodSelector;
    }

    public DatePicker getStartDatePicker() {
        return startDatePicker;
    }

    public DatePicker getEndDatePicker() {
        return endDatePicker;
    }

    public Button getAddButton() {
        return addButton;
    }

    public Button getDeleteButton() {
        return deleteButton;
    }

    /**
     * Clears all budget entries from the list and cards container.
     */
    public void clearBudgets() {
        budgetList.getItems().clear();
        budgetCardsContainer.getChildren().clear();
    }

    /**
     * Adds a budget status card with progress bar, warning/alert styling,
     * and expired state handling.
     *
     * @param id          the budget UUID
     * @param category    the budget category name
     * @param percentUsed percentage of budget consumed (0-100+)
     * @param spent       formatted spent amount
     * @param limit       formatted budget limit
     * @param isExpired   whether the budget period has ended
     */
    public void addBudgetCard(java.util.UUID id, String category, double percentUsed,
                              String spent, String limit, boolean isExpired) {
        HBox card = new HBox(10);
        card.setPadding(new Insets(8));
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("budget-card");

        Label nameLabel = new Label(category);
        nameLabel.setPrefWidth(140);
        nameLabel.setStyle("-fx-font-weight: bold;");

        ProgressBar bar = new ProgressBar(Math.min(percentUsed / 100.0, 1.0));
        bar.setPrefWidth(200);
        bar.setPrefHeight(20);

        Label amountLabel = new Label(spent + " / " + limit);
        Label statusLabel = new Label();
        statusLabel.getStyleClass().add("budget-status-label");

        if (isExpired) {
            card.getStyleClass().add("budget-card-expired");
            statusLabel.setText("EXPIRADO");
            statusLabel.getStyleClass().add("budget-status-expired");
        } else if (percentUsed >= 100) {
            card.getStyleClass().add("budget-card-danger");
            statusLabel.setText("\u26A0 EXCEDIDO");
            statusLabel.getStyleClass().add("budget-status-danger");
        } else if (percentUsed >= 80) {
            card.getStyleClass().add("budget-card-warning");
            statusLabel.setText("\u26A0 ADVERTENCIA");
            statusLabel.getStyleClass().add("budget-status-warning");
        } else {
            card.getStyleClass().add("budget-card-normal");
        }

        Label percentLabel = new Label(String.format("%.0f%%", percentUsed));
        percentLabel.setPrefWidth(50);

        card.getChildren().addAll(nameLabel, bar, percentLabel, amountLabel, statusLabel);
        budgetCardsContainer.getChildren().add(card);

        // Also add to the list for selection purposes
        String suffix = isExpired ? " [EXPIRADO]" : "";
        budgetList.getItems().add(id.toString() + "|" + category + ": " + spent + " / " + limit
                + " (" + (int) percentUsed + "%)" + suffix);
    }

    /**
     * Adds a budget status card without expired flag (defaults to not expired).
     */
    public void addBudgetCard(java.util.UUID id, String category, double percentUsed, String spent, String limit) {
        addBudgetCard(id, category, percentUsed, spent, limit, false);
    }

    /**
     * Adds a budget status card without ID or expired flag (backward compatibility).
     */
    public void addBudgetCard(String category, double percentUsed, String spent, String limit) {
        addBudgetCard(java.util.UUID.randomUUID(), category, percentUsed, spent, limit, false);
    }

    /**
     * Returns the UUID of the currently selected budget, or null if none selected.
     * The budget list items store the UUID as a prefix before the pipe character.
     */
    public java.util.UUID getSelectedBudgetId() {
        String selected = budgetList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return null;
        }
        int pipeIndex = selected.indexOf('|');
        if (pipeIndex < 0) {
            return null;
        }
        try {
            return java.util.UUID.fromString(selected.substring(0, pipeIndex));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
