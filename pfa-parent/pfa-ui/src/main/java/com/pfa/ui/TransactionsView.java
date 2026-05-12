package com.pfa.ui;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

import com.pfa.core.CategorizationEngine;
import com.pfa.core.Category;
import com.pfa.core.Direction;
import com.pfa.core.Transaction;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Transactions view: dense table optimized for scanning hundreds of rows.
 * Amounts are right-aligned in monospace, color-coded by direction.
 * Category column is editable via ComboBox for inline re-categorization.
 * Empty state guides the user toward importing.
 */
public class TransactionsView extends VBox {

    private final TableView<Transaction> tableView;
    private final ObservableList<Transaction> transactions;
    private final Label countLabel;
    private CategorizationEngine categorizationEngine;
    private BiConsumer<Transaction, String> onCategoryChanged;
    private BiConsumer<UUID, Boolean> onInternalTransferToggle;

    public TransactionsView() {
        setSpacing(0);
        setPadding(new Insets(0));

        transactions = FXCollections.observableArrayList();
        tableView = createTableView();

        // Header row with transaction count
        countLabel = new Label("0 transacciones");
        countLabel.getStyleClass().add("section-subtitle");

        HBox header = new HBox();
        header.setPadding(new Insets(12, 16, 8, 16));
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().add(countLabel);

        getChildren().addAll(header, tableView);
        VBox.setVgrow(tableView, Priority.ALWAYS);
    }

    /**
     * Sets the categorization engine used to populate the category ComboBox
     * and record overrides on edit.
     */
    public void setCategorizationEngine(CategorizationEngine engine) {
        this.categorizationEngine = engine;
    }

    /**
     * Sets a callback invoked when the user changes a transaction's category.
     * The callback receives the original transaction and the new category name.
     */
    public void setOnCategoryChanged(BiConsumer<Transaction, String> callback) {
        this.onCategoryChanged = callback;
    }

    /**
     * Sets the callback invoked when the user toggles the internal transfer checkbox.
     * The consumer receives the transaction ID and the new boolean value.
     */
    public void setOnInternalTransferToggle(BiConsumer<UUID, Boolean> callback) {
        this.onInternalTransferToggle = callback;
    }

    public void setTransactions(java.util.List<Transaction> txList) {
        transactions.setAll(txList);
        countLabel.setText(txList.size() + " transacción" + (txList.size() == 1 ? "" : "es"));
    }

    public TableView<Transaction> getTableView() {
        return tableView;
    }

    private TableView<Transaction> createTableView() {
        TableView<Transaction> table = new TableView<>(transactions);
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(createEmptyState());

        // Date column: compact, fixed width
        TableColumn<Transaction, String> dateCol = new TableColumn<>("Fecha");
        dateCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().date().toString()));
        dateCol.setPrefWidth(90);
        dateCol.setMinWidth(80);
        dateCol.setEditable(false);

        // Description: takes remaining space
        TableColumn<Transaction, String> descCol = new TableColumn<>("Descripción");
        descCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().description()));
        descCol.setPrefWidth(300);
        descCol.setEditable(false);

        // Amount: monospace, right-aligned, color-coded
        TableColumn<Transaction, String> amountCol = new TableColumn<>("Monto");
        amountCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().amount().amount().toPlainString()));
        amountCol.setPrefWidth(110);
        amountCol.setCellFactory(col -> new AmountCell());
        amountCol.setEditable(false);

        // Currency
        TableColumn<Transaction, String> currencyCol = new TableColumn<>("Currency");
        currencyCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().amount().currency().name()));
        currencyCol.setPrefWidth(60);
        currencyCol.setMinWidth(50);
        currencyCol.setEditable(false);

        // Direction: narrow indicator
        TableColumn<Transaction, String> directionCol = new TableColumn<>("\u00b1");
        directionCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().direction() == Direction.DEBIT ? "\u2212" : "+"));
        directionCol.setPrefWidth(35);
        directionCol.setMinWidth(30);
        directionCol.setStyle("-fx-alignment: center;");
        directionCol.setEditable(false);

        // Category: editable via ComboBox
        TableColumn<Transaction, String> categoryCol = createCategoryColumn();

        // Account: compact
        TableColumn<Transaction, String> accountCol = new TableColumn<>("Cuenta");
        accountCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().accountId()));
        accountCol.setPrefWidth(100);
        accountCol.setEditable(false);

        // Internal Transfer: checkbox toggle
        TableColumn<Transaction, Boolean> transferCol = new TableColumn<>("Transferencia Interna");
        transferCol.setCellValueFactory(cd -> {
            Transaction tx = cd.getValue();
            SimpleBooleanProperty prop = new SimpleBooleanProperty(tx.isInternalTransfer());
            prop.addListener((obs, oldVal, newVal) -> {
                if (onInternalTransferToggle != null) {
                    onInternalTransferToggle.accept(tx.id(), newVal);
                }
            });
            return prop;
        });
        transferCol.setCellFactory(CheckBoxTableCell.forTableColumn(transferCol));
        transferCol.setEditable(true);
        transferCol.setPrefWidth(120);

        table.getColumns().addAll(java.util.List.of(
                dateCol, descCol, amountCol, currencyCol, directionCol, categoryCol, accountCol,
                transferCol));

        return table;
    }

    private TableColumn<Transaction, String> createCategoryColumn() {
        TableColumn<Transaction, String> categoryCol = new TableColumn<>("Categoría");
        categoryCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().category().orElse("")));
        categoryCol.setPrefWidth(120);
        categoryCol.setEditable(true);

        categoryCol.setCellFactory(col -> {
            ObservableList<String> categoryNames = buildCategoryList();
            return new ComboBoxTableCell<>(categoryNames);
        });

        categoryCol.setOnEditCommit(event -> {
            String newCategory = event.getNewValue();
            if (newCategory == null || newCategory.isBlank()) {
                return;
            }

            int rowIndex = event.getTablePosition().getRow();
            Transaction original = transactions.get(rowIndex);

            // Skip if category didn't actually change
            String currentCategory = original.category().orElse("");
            if (currentCategory.equals(newCategory)) {
                return;
            }

            // Record the override in the categorization engine
            if (categorizationEngine != null) {
                categorizationEngine.recordOverride(original, newCategory);
            }

            // Create updated transaction with new category
            Transaction updated = new Transaction(
                    original.id(),
                    original.accountId(),
                    original.date(),
                    original.description(),
                    original.amount(),
                    original.direction(),
                    original.bank(),
                    original.transactionType(),
                    Optional.of(newCategory),
                    original.tags(),
                    original.isInternalTransfer(),
                    original.issues(),
                    original.sourceFileHash()
            );

            transactions.set(rowIndex, updated);

            // Notify callback
            if (onCategoryChanged != null) {
                onCategoryChanged.accept(original, newCategory);
            }
        });

        return categoryCol;
    }

    private ObservableList<String> buildCategoryList() {
        if (categorizationEngine == null) {
            return FXCollections.observableArrayList();
        }
        return FXCollections.observableArrayList(
                categorizationEngine.allCategories().stream()
                        .map(Category::name)
                        .sorted()
                        .toList()
        );
    }

    private Label createEmptyState() {
        Label empty = new Label("Sin transacciones aún. Importe un estado de cuenta para comenzar.");
        empty.getStyleClass().add("empty-state");
        return empty;
    }

    /**
     * Custom cell that right-aligns amounts in monospace and colors by direction.
     */
    private static class AmountCell extends TableCell<Transaction, String> {

        private static final String STYLE_DEBIT = "amount-debit";
        private static final String STYLE_CREDIT = "amount-credit";

        AmountCell() {
            getStyleClass().add("amount-cell");
            setAlignment(Pos.CENTER_RIGHT);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                getStyleClass().removeAll(STYLE_DEBIT, STYLE_CREDIT);
                return;
            }

            setText(item);

            // Color based on row direction
            Transaction tx = super.getTableView().getItems().get(getIndex());
            getStyleClass().removeAll(STYLE_DEBIT, STYLE_CREDIT);
            if (tx != null) {
                if (tx.direction() == Direction.DEBIT) {
                    getStyleClass().add(STYLE_DEBIT);
                } else {
                    getStyleClass().add(STYLE_CREDIT);
                }
            }
        }
    }
}
