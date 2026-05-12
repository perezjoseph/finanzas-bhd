package com.pfa.ui;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.pfa.core.Money;
import com.pfa.core.RecurringPayment;
import com.pfa.core.TrendGranularity;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Dashboard view: category pie chart, spending trend line chart,
 * monthly comparison bar chart, alerts panel, and budget status.
 */
public class DashboardView extends VBox {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMM yyyy");

    private final PieChart categoryPieChart;
    private final LineChart<String, Number> trendChart;
    private final ComboBox<TrendGranularity> granularitySelector;
    private final BarChart<String, Number> monthlyComparisonChart;
    private final BarChart<String, Number> incomeVsExpensesChart;
    private final FlowPane monthSelectorPane;
    private final VBox alertsPanel;
    private final VBox budgetCards;
    private final VBox savingsRatePanel;
    private final VBox forecastPanel;
    private final TableView<RecurringPayment> recurringPaymentsTable;

    private final List<CheckBox> monthCheckBoxes = new ArrayList<>();
    private Runnable onMonthSelectionChanged;
    private Runnable onGranularityChanged;

    public DashboardView() {
        setSpacing(16);
        setPadding(new Insets(16));

        categoryPieChart = createCategoryPieChart();
        trendChart = createTrendChart();
        granularitySelector = createGranularitySelector();
        monthlyComparisonChart = createMonthlyComparisonChart();
        incomeVsExpensesChart = createIncomeVsExpensesChart();
        monthSelectorPane = createMonthSelectorPane();
        alertsPanel = createAlertsPanel();
        budgetCards = createBudgetCards();
        savingsRatePanel = createSavingsRatePanel();
        forecastPanel = createForecastPanel();
        recurringPaymentsTable = createRecurringPaymentsTable();

        getChildren().addAll(buildLayout());
    }

    public PieChart getCategoryPieChart() {
        return categoryPieChart;
    }

    public LineChart<String, Number> getTrendChart() {
        return trendChart;
    }

    public BarChart<String, Number> getMonthlyComparisonChart() {
        return monthlyComparisonChart;
    }

    public BarChart<String, Number> getIncomeVsExpensesChart() {
        return incomeVsExpensesChart;
    }

    public VBox getAlertsPanel() {
        return alertsPanel;
    }

    public VBox getBudgetCards() {
        return budgetCards;
    }

    public VBox getForecastPanel() {
        return forecastPanel;
    }

    /**
     * Sets the callback invoked when the user changes month selection.
     */
    public void setOnMonthSelectionChanged(Runnable callback) {
        this.onMonthSelectionChanged = callback;
    }

    /**
     * Sets the callback invoked when the user changes trend granularity.
     */
    public void setOnGranularityChanged(Runnable callback) {
        this.onGranularityChanged = callback;
    }

    /**
     * Returns the currently selected trend granularity.
     * Defaults to MONTHLY.
     */
    public TrendGranularity getSelectedGranularity() {
        TrendGranularity selected = granularitySelector.getValue();
        return selected != null ? selected : TrendGranularity.MONTHLY;
    }

    /**
     * Returns the granularity selector ComboBox.
     */
    public ComboBox<TrendGranularity> getGranularitySelector() {
        return granularitySelector;
    }

    /**
     * Updates the month selector checkboxes with available months from the data.
     * Pre-selects the most recent months (up to 3) if none are currently selected.
     */
    public void setAvailableMonths(List<YearMonth> months) {
        monthCheckBoxes.clear();
        monthSelectorPane.getChildren().clear();

        List<YearMonth> sorted = new ArrayList<>(months);
        Collections.sort(sorted);

        for (YearMonth month : sorted) {
            CheckBox cb = new CheckBox(month.format(MONTH_FORMATTER));
            cb.setUserData(month);
            cb.setOnAction(e -> {
                if (onMonthSelectionChanged != null) {
                    onMonthSelectionChanged.run();
                }
            });
            monthCheckBoxes.add(cb);
            monthSelectorPane.getChildren().add(cb);
        }

        // Pre-select the most recent 3 months (or fewer if less available)
        int preSelectCount = Math.min(3, monthCheckBoxes.size());
        for (int i = monthCheckBoxes.size() - preSelectCount; i < monthCheckBoxes.size(); i++) {
            monthCheckBoxes.get(i).setSelected(true);
        }
    }

    /**
     * Returns the list of months currently selected by the user.
     * Valid selection is 2-12 months; returns empty list otherwise.
     */
    public List<YearMonth> getSelectedMonths() {
        List<YearMonth> selected = new ArrayList<>();
        for (CheckBox cb : monthCheckBoxes) {
            if (cb.isSelected()) {
                selected.add((YearMonth) cb.getUserData());
            }
        }
        if (selected.size() < 2 || selected.size() > 12) {
            return List.of();
        }
        Collections.sort(selected);
        return selected;
    }

    /**
     * Shows an empty-state message when no data matches current filters.
     */
    public void showEmptyState(String message) {
        getChildren().clear();
        VBox emptyBox = new VBox();
        emptyBox.getStyleClass().add("empty-state");
        Label emptyLabel = new Label(message);
        emptyBox.getChildren().add(emptyLabel);
        getChildren().add(emptyBox);
    }

    /**
     * Rebuilds the dashboard layout after showEmptyState cleared it.
     */
    public void rebuild() {
        if (getChildren().size() == 1 && getChildren().get(0) instanceof VBox) {
            getChildren().clear();
            getChildren().addAll(buildLayout());
        }
    }

    private List<javafx.scene.Node> buildLayout() {
        // Primary zone: alerts strip (if any) + main charts
        GridPane chartsGrid = new GridPane();
        chartsGrid.setHgap(16);
        chartsGrid.setVgap(16);
        chartsGrid.add(categoryPieChart, 0, 0);

        // Trend chart with granularity toggle above it
        VBox trendSection = new VBox(5);
        HBox granularityBar = new HBox(8);
        granularityBar.setPadding(new Insets(0, 0, 0, 5));
        Label granularityLabel = new Label("Granularidad:");
        granularityBar.getChildren().addAll(granularityLabel, granularitySelector);
        trendSection.getChildren().addAll(granularityBar, trendChart);
        VBox.setVgrow(trendChart, Priority.ALWAYS);

        chartsGrid.add(trendSection, 1, 0);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(45);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(55);
        chartsGrid.getColumnConstraints().addAll(col1, col2);

        GridPane.setHgrow(categoryPieChart, Priority.ALWAYS);
        GridPane.setHgrow(trendChart, Priority.ALWAYS);
        GridPane.setVgrow(categoryPieChart, Priority.ALWAYS);
        GridPane.setVgrow(trendChart, Priority.ALWAYS);

        // Alerts and budget warnings at the top (compact, always visible)
        TitledPane alertsPane = new TitledPane("Alertas y Presupuestos", alertsPanel);
        alertsPane.setExpanded(true);
        alertsPane.setCollapsible(true);

        TitledPane budgetPane = new TitledPane("Estado de Presupuestos", budgetCards);
        budgetPane.setExpanded(true);
        budgetPane.setCollapsible(true);

        // Secondary zone: collapsed by default, drill-down detail
        VBox comparisonSection = new VBox(5);
        Label selectorLabel = new Label("Seleccione meses a comparar (2\u201312):");
        comparisonSection.getChildren().addAll(selectorLabel, monthSelectorPane, monthlyComparisonChart);

        TitledPane comparisonPane = new TitledPane("Comparación Mensual", comparisonSection);
        comparisonPane.setExpanded(false);
        comparisonPane.setCollapsible(true);

        TitledPane incomeVsExpensesPane = new TitledPane("Ingresos vs Gastos", incomeVsExpensesChart);
        incomeVsExpensesPane.setExpanded(false);
        incomeVsExpensesPane.setCollapsible(true);

        TitledPane savingsRatePane = new TitledPane("Tasa de Ahorro Mensual", savingsRatePanel);
        savingsRatePane.setExpanded(false);
        savingsRatePane.setCollapsible(true);

        TitledPane forecastPane = new TitledPane("Pronóstico de Fin de Mes", forecastPanel);
        forecastPane.setExpanded(false);
        forecastPane.setCollapsible(true);

        TitledPane recurringPane = new TitledPane("Pagos Recurrentes", recurringPaymentsTable);
        recurringPane.setExpanded(false);
        recurringPane.setCollapsible(true);

        VBox.setVgrow(chartsGrid, Priority.ALWAYS);

        return List.of(alertsPane, budgetPane, chartsGrid, forecastPane, comparisonPane,
                incomeVsExpensesPane, savingsRatePane, recurringPane);
    }

    private PieChart createCategoryPieChart() {
        PieChart chart = new PieChart();
        chart.setTitle("Gastos por Categoría");
        chart.setLabelsVisible(true);
        chart.setLegendVisible(true);
        chart.setMinSize(300, 250);
        return chart;
    }

    private LineChart<String, Number> createTrendChart() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Mes");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Monto");

        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Tendencias de Gastos");
        chart.setCreateSymbols(true);
        chart.setMinSize(400, 250);
        return chart;
    }

    private BarChart<String, Number> createMonthlyComparisonChart() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Mes");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Gasto Total");

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Comparación Mensual de Gastos");
        chart.setPrefSize(800, 300);
        chart.setBarGap(2);
        chart.setCategoryGap(20);
        return chart;
    }

    private BarChart<String, Number> createIncomeVsExpensesChart() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Mes");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Monto");

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Ingresos vs Gastos");
        chart.setPrefSize(800, 300);
        chart.setBarGap(2);
        chart.setCategoryGap(20);
        return chart;
    }

    private FlowPane createMonthSelectorPane() {
        FlowPane pane = new FlowPane();
        pane.setHgap(10);
        pane.setVgap(5);
        pane.setPadding(new Insets(5));
        return pane;
    }

    private ComboBox<TrendGranularity> createGranularitySelector() {
        ComboBox<TrendGranularity> combo = new ComboBox<>(
                FXCollections.observableArrayList(TrendGranularity.values()));
        combo.setValue(TrendGranularity.MONTHLY);
        combo.setOnAction(e -> {
            if (onGranularityChanged != null) {
                onGranularityChanged.run();
            }
        });
        return combo;
    }

    private VBox createAlertsPanel() {
        VBox panel = new VBox(6);
        panel.setPadding(new Insets(8));
        Label placeholder = new Label("Sin alertas");
        placeholder.getStyleClass().add("section-subtitle");
        panel.getChildren().add(placeholder);
        return panel;
    }

    private VBox createBudgetCards() {
        VBox cards = new VBox(6);
        cards.setPadding(new Insets(8));
        Label placeholder = new Label("Sin presupuestos configurados");
        placeholder.getStyleClass().add("section-subtitle");
        cards.getChildren().add(placeholder);
        return cards;
    }

    private VBox createForecastPanel() {
        VBox panel = new VBox(6);
        panel.setPadding(new Insets(8));
        Label placeholder = new Label("Datos de pronóstico no disponibles aún.");
        placeholder.getStyleClass().add("section-subtitle");
        panel.getChildren().add(placeholder);
        return panel;
    }

    @SuppressWarnings("unchecked")
    private TableView<RecurringPayment> createRecurringPaymentsTable() {
        TableView<RecurringPayment> table = new TableView<>();
        table.setPlaceholder(new Label("No se detectaron pagos recurrentes."));
        table.setPrefHeight(200);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<RecurringPayment, String> merchantCol = new TableColumn<>("Comercio");
        merchantCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().merchantKey()));
        merchantCol.setPrefWidth(200);

        TableColumn<RecurringPayment, String> frequencyCol = new TableColumn<>("Frecuencia");
        frequencyCol.setCellValueFactory(cd ->
                new SimpleStringProperty("~" + cd.getValue().frequencyDays() + " días"));
        frequencyCol.setPrefWidth(100);

        TableColumn<RecurringPayment, String> amountCol = new TableColumn<>("Monto Prom.");
        amountCol.setCellValueFactory(cd -> {
            Money avg = cd.getValue().averageAmount();
            return new SimpleStringProperty(avg.currency().symbol + " " + avg.amount().toPlainString());
        });
        amountCol.setPrefWidth(120);

        table.getColumns().addAll(merchantCol, frequencyCol, amountCol);
        return table;
    }

    /**
     * Returns the recurring payments table for external population.
     */
    public TableView<RecurringPayment> getRecurringPaymentsTable() {
        return recurringPaymentsTable;
    }

    /**
     * Returns the savings rate panel for external population.
     */
    public VBox getSavingsRatePanel() {
        return savingsRatePanel;
    }

    private VBox createSavingsRatePanel() {
        VBox panel = new VBox(6);
        panel.setPadding(new Insets(8));
        Label placeholder = new Label("Datos de tasa de ahorro no disponibles.");
        placeholder.getStyleClass().add("section-subtitle");
        panel.getChildren().add(placeholder);
        return panel;
    }
}
