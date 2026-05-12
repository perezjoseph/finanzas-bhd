package com.pfa.app;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.pfa.analytics.DefaultAnalyticsEngine;
import com.pfa.categorization.DefaultCategorizationEngine;
import com.pfa.core.AccountBalance;
import com.pfa.core.AnalyticsEngine;
import com.pfa.core.Budget;
import com.pfa.core.BudgetStatus;
import com.pfa.core.CategorizationEngine;
import com.pfa.core.CategoryBreakdown;
import com.pfa.core.Currency;
import com.pfa.core.CurrencyConverter;
import com.pfa.core.DateRange;
import com.pfa.core.DefaultCurrencyConverter;
import com.pfa.core.DefaultFilterEngine;
import com.pfa.core.ExportEngine;
import com.pfa.core.ExportException;
import com.pfa.core.FetchResult;
import com.pfa.core.FetchRule;
import com.pfa.core.FilterCriteria;
import com.pfa.core.FilterEngine;
import com.pfa.core.GmailAccount;
import com.pfa.core.GmailFetcher;
import com.pfa.core.ImportBatchResult;
import com.pfa.core.ImportOptions;
import com.pfa.core.ImportSource;
import com.pfa.core.MonthlyForecast;
import com.pfa.core.MonthlyTrends;
import com.pfa.core.NetWorthTrend;
import com.pfa.core.OcrMode;
import com.pfa.core.RecurringPayment;
import com.pfa.core.SessionHandle;
import com.pfa.core.SessionManager;
import com.pfa.core.SessionSnapshot;
import com.pfa.core.SpendingAlert;
import com.pfa.core.StatementImporter;
import com.pfa.core.Transaction;
import com.pfa.core.TransactionSet;
import com.pfa.export.DefaultExportEngine;
import com.pfa.gmail.DefaultGmailFetcher;
import com.pfa.import_.DefaultOcrEngine;
import com.pfa.import_.DefaultStatementImporter;
import com.pfa.import_.DefaultTransactionNormalizer;
import com.pfa.persistence.DatabaseManager;
import com.pfa.persistence.DefaultSessionManager;
import com.pfa.persistence.DpapiKeyStore;
import com.pfa.persistence.EncryptionService;
import com.pfa.persistence.KeyDerivationService;
import com.pfa.persistence.SessionEncryptionManager;
import com.pfa.persistence.TransactionDao;
import com.pfa.persistence.VaultManager;

/**
 * Central service facade wiring all modules together.
 * Provides a single entry point for the UI layer to access all application services.
 * Manages thread pools and lifecycle.
 */
public class ServiceFacade {

    private static final int IMPORT_POOL_SIZE = Math.min(Runtime.getRuntime().availableProcessors(), 4);

    // Core services
    private CurrencyConverter currencyConverter;
    private FilterEngine filterEngine;
    private CategorizationEngine categorizationEngine;
    private AnalyticsEngine analyticsEngine;
    private StatementImporter statementImporter;
    private ExportEngine exportEngine;
    private GmailFetcher gmailFetcher;
    private SessionManager sessionManager;

    // Persistence
    private DatabaseManager databaseManager;
    private TransactionDao transactionDao;
    private VaultManager vaultManager;

    // OCR
    private DefaultOcrEngine ocrEngine;

    // Format detector (stored for password updates)
    private com.pfa.import_.DefaultFormatDetector formatDetector;

    // Thread pools
    private ExecutorService importPool;

    // State
    private final List<Transaction> transactions = new ArrayList<>();
    private final List<Budget> budgets = new ArrayList<>();
    private Currency baseCurrency = Currency.USD;
    private String pdfPassword;

    /**
     * Initializes all services and opens the workspace database.
     */
    public void initialize() {
        // Currency converter
        currencyConverter = new DefaultCurrencyConverter();

        // Filter engine
        filterEngine = new DefaultFilterEngine();

        // Categorization
        categorizationEngine = new DefaultCategorizationEngine();

        // Analytics
        analyticsEngine = new DefaultAnalyticsEngine(currencyConverter);

        // OCR engine
        String tessDataPath = System.getenv("TESSDATA_PREFIX");
        ocrEngine = new DefaultOcrEngine(tessDataPath != null ? tessDataPath : "");

        // Import pipeline
        formatDetector = new com.pfa.import_.DefaultFormatDetector();
        statementImporter = new DefaultStatementImporter(
                formatDetector,
                ocrEngine,
                new DefaultTransactionNormalizer(),
                categorizationEngine
        );

        // Export
        exportEngine = new DefaultExportEngine();

        // Gmail
        gmailFetcher = new DefaultGmailFetcher();

        // Persistence
        databaseManager = new DatabaseManager();
        vaultManager = new VaultManager();
        try {
            databaseManager.initialize();
            transactionDao = new TransactionDao(databaseManager.getConnection());
        } catch (Exception e) {
            // Fall back to in-memory mode if DB fails
            transactionDao = null;
        }

        // Session manager with encryption
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isEmpty()) {
            appData = System.getProperty("user.home");
        }
        Path appDir = Path.of(appData, "PersonalFinanceAnalyzer");
        EncryptionService encryptionService = new EncryptionService();
        KeyDerivationService keyDerivationService = new KeyDerivationService();
        DpapiKeyStore dpapiKeyStore = new DpapiKeyStore(appDir);
        SessionEncryptionManager encryptionManager = new SessionEncryptionManager(
                encryptionService, keyDerivationService, dpapiKeyStore);
        sessionManager = new DefaultSessionManager(appDir, encryptionManager, vaultManager);

        // Thread pools
        importPool = Executors.newFixedThreadPool(IMPORT_POOL_SIZE);

        // Load existing transactions from DB
        loadTransactionsFromDb();
    }

    /**
     * Imports statement files and returns the batch result.
     */
    public ImportBatchResult importFiles(List<ImportSource> sources) {
        ImportOptions options = (pdfPassword != null && !pdfPassword.isEmpty())
                ? ImportOptions.withPassword(pdfPassword)
                : ImportOptions.defaults();
        ImportBatchResult result = statementImporter.importAll(sources, options);
        transactions.addAll(result.successes());

        // Persist to DB
        if (transactionDao != null) {
            try {
                transactionDao.insertBatch(result.successes());
            } catch (java.sql.SQLException e) {
                // Log but don't fail the import
            }
        }

        return result;
    }

    /**
     * Returns all transactions filtered by the given criteria.
     */
    public TransactionSet getFilteredTransactions(FilterCriteria criteria) {
        if (criteria == null) {
            return new TransactionSet(List.copyOf(transactions), null);
        }
        return filterEngine.apply(transactions, criteria);
    }

    /**
     * Returns all loaded transactions.
     */
    public List<Transaction> getAllTransactions() {
        return List.copyOf(transactions);
    }

    /**
     * Returns monthly spending trends for the given transaction set.
     */
    public MonthlyTrends getMonthlyTrends(TransactionSet txs) {
        return analyticsEngine.monthlyTrends(txs, baseCurrency);
    }

    /**
     * Returns category breakdown for the given transaction set.
     */
    public CategoryBreakdown getCategoryBreakdown(TransactionSet txs) {
        return analyticsEngine.categoryBreakdown(txs);
    }

    /**
     * Returns the average monthly burn rate.
     */
    public com.pfa.core.Money getAverageBurnRate(TransactionSet txs) {
        return analyticsEngine.averageBurnRate(txs, baseCurrency);
    }

    /**
     * Returns top N expenses within a date range.
     */
    public List<Transaction> getTopExpenses(TransactionSet txs, DateRange range, int limit) {
        return analyticsEngine.topExpenses(txs, range, limit);
    }

    /**
     * Detects recurring payments.
     */
    public List<RecurringPayment> detectRecurring(TransactionSet txs) {
        return analyticsEngine.detectRecurring(txs);
    }

    /**
     * Returns unusual spending alerts.
     */
    public List<SpendingAlert> getUnusualSpending(TransactionSet txs) {
        return analyticsEngine.unusualSpending(txs);
    }

    /**
     * Returns end-of-month spending forecast.
     */
    public MonthlyForecast getForecast(TransactionSet txs) {
        return analyticsEngine.forecastCurrentMonth(txs, baseCurrency);
    }

    /**
     * Returns income vs expenses comparison per month.
     */
    public com.pfa.core.IncomeVsExpenses getIncomeVsExpenses(TransactionSet txs) {
        return analyticsEngine.incomeVsExpenses(txs, baseCurrency);
    }

    /**
     * Returns budget statuses.
     */
    public List<BudgetStatus> getBudgetStatuses(TransactionSet txs) {
        return analyticsEngine.budgetStatus(txs, budgets, baseCurrency);
    }

    /**
     * Returns net worth trend.
     */
    public NetWorthTrend getNetWorthTrend(List<AccountBalance> balances) {
        return analyticsEngine.netWorthTrend(balances);
    }

    /**
     * Returns monthly savings rates.
     */
    public com.pfa.core.MonthlySavingsRate getSavingsRates(TransactionSet txs) {
        return analyticsEngine.monthlySavingsRate(txs, baseCurrency);
    }

    /**
     * Adds a budget.
     */
    public void addBudget(Budget budget) {
        budgets.add(budget);
    }

    /**
     * Removes a budget by ID.
     */
    public void removeBudget(java.util.UUID id) {
        budgets.removeIf(b -> b.id().equals(id));
    }

    /**
     * Returns all budgets.
     */
    public List<Budget> getBudgets() {
        return List.copyOf(budgets);
    }

    /**
     * Exports transactions to CSV.
     */
    public void exportCsv(Path target, TransactionSet txs, FilterCriteria filter) throws ExportException {
        exportEngine.exportCsv(target, txs, filter);
    }

    /**
     * Exports transactions to Excel.
     */
    public void exportExcel(Path target, TransactionSet txs, FilterCriteria filter) throws ExportException {
        exportEngine.exportExcel(target, txs, filter);
    }

    /**
     * Fetches statements from Gmail.
     */
    public FetchResult fetchGmail(GmailAccount account, List<FetchRule> rules) {
        return gmailFetcher.fetch(account, rules);
    }

    /**
     * Saves a Gmail account.
     */
    public void saveGmailAccount(GmailAccount account) {
        gmailFetcher.saveAccount(account);
    }

    /**
     * Removes a Gmail account.
     */
    public void removeGmailAccount(String email) {
        gmailFetcher.removeAccount(email);
    }

    /**
     * Returns a saved Gmail account by email, or null if not found.
     */
    public GmailAccount getGmailAccount(String email) {
        return gmailFetcher.getAccount(email);
    }

    /**
     * Saves the current session.
     */
    public SessionHandle saveSession(String name) {
        SessionSnapshot snapshot = new SessionSnapshot(
                "1.0.0",
                List.of(),
                List.copyOf(transactions),
                categorizationEngine.allCategories(),
                List.of(),
                List.of(),
                List.copyOf(budgets),
                java.util.Map.of("baseCurrency", baseCurrency.name())
        );
        return sessionManager.save(name, snapshot);
    }

    /**
     * Loads a session by handle.
     */
    public void loadSession(SessionHandle handle) {
        SessionSnapshot snapshot = sessionManager.load(handle);
        transactions.clear();
        transactions.addAll(snapshot.transactions());
    }

    /**
     * Lists available sessions.
     */
    public List<SessionHandle> listSessions() {
        return sessionManager.list();
    }

    /**
     * Sets an exchange rate.
     */
    public void setExchangeRate(Currency from, Currency to, BigDecimal rate) {
        currencyConverter.setRate(from, to, rate);
    }

    /**
     * Gets the base reporting currency.
     */
    public Currency getBaseCurrency() {
        return baseCurrency;
    }

    /**
     * Sets the base reporting currency.
     */
    public void setBaseCurrency(Currency currency) {
        this.baseCurrency = currency;
    }

    /**
     * Gets the PDF password used for encrypted BHD statements.
     */
    public String getPdfPassword() {
        return pdfPassword;
    }

    /**
     * Sets the PDF password to use when opening encrypted BHD statement PDFs.
     */
    public void setPdfPassword(String password) {
        this.pdfPassword = password;
        // Update the format detector so it can open encrypted PDFs for detection
        if (formatDetector instanceof com.pfa.import_.DefaultFormatDetector dfd) {
            dfd.setPdfPassword(password);
        }
    }

    /**
     * Returns the active OCR mode.
     */
    public OcrMode getOcrMode() {
        return ocrEngine.activeMode();
    }

    /**
     * Returns the transaction count.
     */
    public int getTransactionCount() {
        return transactions.size();
    }

    /**
     * Returns the import thread pool for async operations.
     */
    public ExecutorService getImportPool() {
        return importPool;
    }

    /**
     * Updates a transaction's internal transfer flag in memory and persists the change.
     * Returns the updated transaction, or null if not found.
     */
    public Transaction setTransactionInternalTransfer(java.util.UUID id, boolean isInternalTransfer) {
        for (int i = 0; i < transactions.size(); i++) {
            Transaction tx = transactions.get(i);
            if (tx.id().equals(id)) {
                Transaction updated = new Transaction(
                        tx.id(), tx.accountId(), tx.date(), tx.description(),
                        tx.amount(), tx.direction(), tx.bank(), tx.transactionType(),
                        tx.category(), tx.tags(), isInternalTransfer, tx.issues(), tx.sourceFileHash()
                );
                transactions.set(i, updated);

                // Persist to DB
                if (transactionDao != null) {
                    try {
                        transactionDao.updateInternalTransfer(id, isInternalTransfer);
                    } catch (java.sql.SQLException e) {
                        // Log but don't fail the UI operation
                    }
                }
                return updated;
            }
        }
        return null;
    }

    /**
     * Returns the categorization engine for category overrides.
     */
    public CategorizationEngine getCategorizationEngine() {
        return categorizationEngine;
    }

    /**
     * Returns the vault manager for authentication and vault mode control.
     */
    public VaultManager getVaultManager() {
        return vaultManager;
    }

    /**
     * Shuts down all services and thread pools.
     */
    public void shutdown() {
        if (importPool != null) {
            importPool.shutdown();
        }
        if (ocrEngine != null) {
            ocrEngine.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    private void loadTransactionsFromDb() {
        if (transactionDao != null) {
            try {
                transactions.addAll(transactionDao.findAll());
            } catch (Exception e) {
                // Silently continue with empty transaction list
            }
        }
    }
}
