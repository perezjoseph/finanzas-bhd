package com.pfa.ui;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.pfa.core.Category;
import com.pfa.core.Currency;
import com.pfa.core.FilterCriteria;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;

/**
 * Filter panel with controls for date range, account, currency, category,
 * merchant, amount range, and keyword. Emits FilterCriteria changes.
 */
public class FilterPanel extends TitledPane {

    private final ComboBox<String> dateRangePreset;
    private final DatePicker startDate;
    private final DatePicker endDate;
    private final ListView<String> accountList;
    private final ComboBox<String> currencyFilter;
    private final ListView<String> categoryList;
    private final TextField merchantField;
    private final TextField minAmountField;
    private final TextField maxAmountField;
    private final TextField keywordField;
    private final Label validationErrorLabel;
    private final ObjectProperty<FilterCriteria> criteriaProperty;

    public FilterPanel() {
        setText("Filtros");
        setCollapsible(true);
        setExpanded(true);

        criteriaProperty = new SimpleObjectProperty<>();

        dateRangePreset = new ComboBox<>();
        dateRangePreset.getItems().addAll(
                "Todo el Tiempo", "Este Mes", "Este Trimestre", "Este Año",
                "Último 1 Año", "Últimos 2 Años", "Últimos 3 Años", "Últimos 5 Años",
                "Personalizado");
        dateRangePreset.setValue("Todo el Tiempo");
        dateRangePreset.setMaxWidth(Double.MAX_VALUE);

        startDate = new DatePicker();
        startDate.setPromptText("Fecha inicio");

        endDate = new DatePicker();
        endDate.setPromptText("Fecha fin");

        // Wire preset to auto-set date pickers
        dateRangePreset.setOnAction(e -> applyDatePreset());

        accountList = new ListView<>();
        accountList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        accountList.setPrefHeight(80);

        currencyFilter = new ComboBox<>();
        currencyFilter.getItems().addAll("Todos", "USD", "DOP", "BBD");
        currencyFilter.setValue("Todos");

        categoryList = new ListView<>();
        categoryList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        categoryList.setPrefHeight(100);

        merchantField = new TextField();
        merchantField.setPromptText("Buscar comercio...");

        minAmountField = new TextField();
        minAmountField.setPromptText("Monto mínimo");

        maxAmountField = new TextField();
        maxAmountField.setPromptText("Monto máximo");

        keywordField = new TextField();
        keywordField.setPromptText("Palabra clave / etiqueta");

        validationErrorLabel = new Label();
        validationErrorLabel.getStyleClass().add("validation-error");
        validationErrorLabel.setWrapText(true);
        validationErrorLabel.setVisible(false);
        validationErrorLabel.setManaged(false);

        Button clearButton = new Button("Limpiar Todo");
        clearButton.setOnAction(e -> clearFilters());

        Button applyButton = new Button("Aplicar");
        applyButton.setOnAction(e -> buildCriteria());

        VBox content = new VBox(8);
        content.setPadding(new Insets(10));
        content.getChildren().addAll(
                validationErrorLabel,
                new Label("Rango de Fechas"),
                dateRangePreset,
                startDate, endDate,
                new Label("Cuenta"),
                accountList,
                new Label("Moneda"),
                currencyFilter,
                new Label("Categoría"),
                categoryList,
                new Label("Comercio"),
                merchantField,
                new Label("Rango de Monto"),
                minAmountField, maxAmountField,
                new Label("Palabra Clave"),
                keywordField,
                applyButton, clearButton
        );

        setContent(content);
    }

    public ObjectProperty<FilterCriteria> criteriaProperty() {
        return criteriaProperty;
    }

    /**
     * Populates the account multi-select with account IDs from imported transactions.
     */
    public void setAvailableAccounts(Collection<String> accountIds) {
        accountList.setItems(FXCollections.observableArrayList(accountIds));
    }

    /**
     * Populates the category multi-select from the categorization engine's categories.
     */
    public void setAvailableCategories(List<Category> categories) {
        List<String> names = categories.stream()
                .map(Category::name)
                .sorted()
                .toList();
        categoryList.setItems(FXCollections.observableArrayList(names));
    }

    /**
     * Returns the validation error label for testing purposes.
     */
    public Label getValidationErrorLabel() {
        return validationErrorLabel;
    }

    public void clearFilters() {
        dateRangePreset.setValue("All Time");
        startDate.setValue(null);
        endDate.setValue(null);
        accountList.getSelectionModel().clearSelection();
        currencyFilter.setValue("All");
        categoryList.getSelectionModel().clearSelection();
        merchantField.clear();
        minAmountField.clear();
        maxAmountField.clear();
        keywordField.clear();
        clearValidationError();
        criteriaProperty.set(null);
    }

    private void applyDatePreset() {
        String preset = dateRangePreset.getValue();
        if (preset == null || "Personalizado".equals(preset)) {
            return;
        }

        java.time.LocalDate today = java.time.LocalDate.now();

        switch (preset) {
            case "Todo el Tiempo" -> {
                startDate.setValue(null);
                endDate.setValue(null);
            }
            case "Este Mes" -> {
                startDate.setValue(today.withDayOfMonth(1));
                endDate.setValue(today);
            }
            case "Este Trimestre" -> {
                int quarterMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
                startDate.setValue(today.withMonth(quarterMonth).withDayOfMonth(1));
                endDate.setValue(today);
            }
            case "Este Año" -> {
                startDate.setValue(today.withDayOfYear(1));
                endDate.setValue(today);
            }
            case "Último 1 Año" -> {
                startDate.setValue(today.minusYears(1));
                endDate.setValue(today);
            }
            case "Últimos 2 Años" -> {
                startDate.setValue(today.minusYears(2));
                endDate.setValue(today);
            }
            case "Últimos 3 Años" -> {
                startDate.setValue(today.minusYears(3));
                endDate.setValue(today);
            }
            case "Últimos 5 Años" -> {
                startDate.setValue(today.minusYears(5));
                endDate.setValue(today);
            }
            default -> { }
        }

        buildCriteria();
    }

    private void buildCriteria() {
        clearValidationError();

        Optional<java.time.LocalDate> start = Optional.ofNullable(startDate.getValue());
        Optional<java.time.LocalDate> end = Optional.ofNullable(endDate.getValue());

        // Validate date range
        if (start.isPresent() && end.isPresent() && start.get().isAfter(end.get())) {
            showValidationError("La fecha de inicio no puede ser posterior a la fecha de fin.");
            return;
        }

        // Parse and validate amount range
        Optional<BigDecimal> min = parseAmount(minAmountField.getText());
        Optional<BigDecimal> max = parseAmount(maxAmountField.getText());

        if (min.isPresent() && max.isPresent() && min.get().compareTo(max.get()) > 0) {
            showValidationError("El monto mínimo no puede ser mayor que el monto máximo.");
            return;
        }

        // Account selection
        Set<String> selectedAccounts = new HashSet<>(
                accountList.getSelectionModel().getSelectedItems());

        // Currency
        Set<Currency> currencies = Set.of();
        String currVal = currencyFilter.getValue();
        if (currVal != null && !"All".equals(currVal) && !"Todos".equals(currVal)) {
            currencies = Set.of(Currency.valueOf(currVal));
        }

        // Category selection
        Set<String> selectedCategories = new HashSet<>(
                categoryList.getSelectionModel().getSelectedItems());

        Optional<String> merchant = merchantField.getText().isBlank()
                ? Optional.empty() : Optional.of(merchantField.getText().trim());

        Optional<String> keyword = keywordField.getText().isBlank()
                ? Optional.empty() : Optional.of(keywordField.getText().trim());

        FilterCriteria criteria = new FilterCriteria(
                start, end, selectedAccounts, currencies, selectedCategories,
                merchant, min, max, keyword);

        criteriaProperty.set(criteria);
    }

    private void showValidationError(String message) {
        validationErrorLabel.setText(message);
        validationErrorLabel.setVisible(true);
        validationErrorLabel.setManaged(true);
    }

    private void clearValidationError() {
        validationErrorLabel.setText("");
        validationErrorLabel.setVisible(false);
        validationErrorLabel.setManaged(false);
    }

    private Optional<BigDecimal> parseAmount(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(text.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
