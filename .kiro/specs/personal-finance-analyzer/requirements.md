# Requirements Document

## Introduction

Personal Finance Analyzer is a local-first desktop application built with Java and JavaFX that imports bank statement PDFs from multiple bank accounts, automatically categorizes spending, analyzes trends, and generates financial insights. The application runs entirely on the user's local Windows machine with no cloud dependency unless explicitly enabled. It supports multi-currency analysis across DOP (Dominican Peso), BBD (Barbadian Dollar), and USD (US Dollar).

## Glossary

- **Application**: The Personal Finance Analyzer local desktop application built with Java and JavaFX
- **Statement_Importer**: The component responsible for ingesting and parsing PDF bank statements
- **OCR_Engine**: The component that extracts text from scanned/image-based PDF documents, utilizing CUDA GPU acceleration when available
- **Gmail_Fetcher**: The component that connects to Gmail via OAuth2 to automatically download bank statement PDF attachments
- **Format_Detector**: The component that identifies the bank and statement layout format automatically
- **Transaction_Normalizer**: The component that converts extracted raw data into the standardized transaction structure
- **Categorization_Engine**: The component that automatically classifies transactions into spending categories
- **Currency_Converter**: The component that handles exchange rate conversion between supported currencies (DOP, BBD, USD)
- **Analytics_Engine**: The component that computes financial insights, trends, and statistics from normalized transactions
- **Dashboard**: The visual interface presenting charts, graphs, and financial summaries
- **Filter_Engine**: The component that applies user-defined filters to transaction data
- **Export_Engine**: The component that generates CSV and Excel files from processed transaction data
- **Session_Manager**: The component that saves and loads project sessions (imported data, settings, categories)
- **Transaction**: A single financial event containing date, description, amount, currency, debit/credit indicator, account name, bank/source, transaction type, category, and notes/tags
- **Base_Currency**: The user-selected currency in which all reporting values are displayed
- **Category**: A classification label assigned to a transaction (e.g., Food & Dining, Utilities)

## Requirements

### Requirement 1: Multi-PDF Import

**User Story:** As a user, I want to import multiple PDF bank statements simultaneously from different banks and accounts, so that I can consolidate all my financial data in one place.

#### Acceptance Criteria for Multi-PDF Import

1. WHEN the user selects between 1 and 20 PDF files each no larger than 10 MB, THE Statement_Importer SHALL accept and process all selected files in a single import operation
2. WHEN one or more PDF files are selected, THE Statement_Importer SHALL allow the user to assign each file to a specific bank and account before processing begins, regardless of whether a single file or multiple files are selected
3. WHILE an import operation is in progress, THE Application SHALL display the file name and current status (pending, processing, completed, or failed) for each file being processed
4. IF a PDF file is corrupted or unreadable, THEN THE Statement_Importer SHALL display an error message indicating the file name and the reason for failure, and continue processing the remaining files
5. IF a selected file exceeds 10 MB or is not a valid PDF file, THEN THE Statement_Importer SHALL reject that file with an error message indicating the file name and the validation failure reason, and continue processing the remaining files
6. IF a PDF file has already been imported (duplicate detection), THEN THE Statement_Importer SHALL notify the user that the file was previously imported, skip that file, and continue processing the remaining files
7. IF a PDF file is valid but contains no parseable transactions, THEN THE Statement_Importer SHALL notify the user that no transactions were found in that file and continue processing the remaining files

### Requirement 2: Automatic Format Detection

**User Story:** As a user, I want the system to automatically detect the format of my bank statements, so that I do not have to manually configure parsing rules for each bank.

#### Acceptance Criteria for Format Detection

1. WHEN a PDF statement is imported, THE Format_Detector SHALL identify the bank and statement layout within 10 seconds and without user intervention, producing a confidence score between 0 and 1
2. IF the Format_Detector produces a confidence score below 0.8 for format identification, THEN THE Format_Detector SHALL prompt the user to select from a list of supported bank formats or configure a new format manually
3. WHEN a text-based PDF is imported, THE Format_Detector SHALL extract transaction text directly from the PDF text layer without requiring OCR processing
4. WHEN a scanned PDF is detected (a PDF containing no extractable text layer), THE OCR_Engine SHALL extract transaction text with at least 95% character-level accuracy for printed text, sufficient to populate the required transaction fields: date, description, amount, and currency (DOP, BBD, or USD)
5. IF one or more required transaction fields (date, description, amount, currency) cannot be extracted from any document regardless of extraction method (text-based or OCR), THEN THE System SHALL flag the affected transactions as incomplete and present them to the user for manual correction

### Requirement 3: Transaction Normalization

**User Story:** As a user, I want all imported transactions normalized into a consistent structure, so that I can analyze data uniformly across banks and accounts.

#### Acceptance Criteria for Transaction Normalization

1. WHEN transactions are extracted from a statement, THE Transaction_Normalizer SHALL produce a normalized record containing the following fields: date (ISO 8601 format, YYYY-MM-DD), description (maximum 256 characters), amount (numeric value with exactly 2 decimal places), currency (one of DOP, BBD, or USD), debit/credit indicator, account name, bank/source, transaction type, category, and notes/tags
2. THE Transaction_Normalizer SHALL treat date, description, amount, currency, debit/credit indicator, account name, and bank/source as required fields, and treat transaction type, category, and notes/tags as optional fields
3. IF a required field cannot be extracted from the source statement, THEN THE Transaction_Normalizer SHALL assign a placeholder value indicating the field is missing and flag the transaction for user review in the application interface
4. IF the currency extracted from a statement is not one of the supported currencies (DOP, BBD, USD), THEN THE Transaction_Normalizer SHALL flag the transaction for user review and preserve the original currency value as extracted
5. THE Transaction_Normalizer SHALL preserve the original currency of each transaction as extracted from the statement without converting to a different currency

### Requirement 4: Multi-Currency Support

**User Story:** As a user, I want to work with transactions in DOP, BBD, and USD including mixed-currency analysis, so that I can understand my finances across different currency accounts.

#### Acceptance Criteria for Multi-Currency Support

1. THE Currency_Converter SHALL support bidirectional conversion between all pairs of DOP, BBD, and USD, covering all 6 directional pairs (DOP→BBD, BBD→DOP, DOP→USD, USD→DOP, BBD→USD, USD→BBD)
2. WHEN the user selects a base currency for reporting, THE Currency_Converter SHALL convert all transaction amounts to the selected base currency for display in analytics, rounding converted amounts to 2 decimal places using half-up rounding
3. WHEN the user edits an exchange rate manually, THE Currency_Converter SHALL persist the user-provided rate and use it for all subsequent conversions involving that currency pair until the user edits it again
4. THE Application SHALL allow the user to choose DOP, BBD, or USD as the base currency for reporting, with USD as the default base currency
5. IF the user enters an exchange rate that is zero, negative, or greater than 999,999, THEN THE Currency_Converter SHALL reject the input and display an error message indicating the accepted range (greater than 0 and up to 999,999)
6. IF no exchange rate has been set for a currency pair required by a conversion, THEN THE Currency_Converter SHALL display a prompt requesting the user to provide the missing exchange rate before performing the conversion

### Requirement 5: Automatic Transaction Categorization

**User Story:** As a user, I want transactions automatically categorized into spending types, so that I can quickly understand where my money goes without manual effort.

#### Acceptance Criteria for Transaction Categorization

1. WHEN a transaction is normalized, THE Categorization_Engine SHALL assign a category from the predefined set: Food & Dining, Groceries, Utilities, Rent/Housing, Transportation, Fuel, Entertainment, Travel, Healthcare, Shopping, Income, Investments, Transfers, Fees, Subscriptions, Cash Withdrawals, Taxes, Miscellaneous
2. IF the Categorization_Engine cannot match a transaction to a specific category, THEN THE Categorization_Engine SHALL assign the transaction to the "Miscellaneous" category
3. WHEN the user edits a transaction category, THE Categorization_Engine SHALL save the user override, apply the updated category to that transaction, and use the override as a learned rule to automatically categorize future transactions from the same merchant description with the user-selected category
4. WHEN the user creates a custom category, THE Categorization_Engine SHALL include the custom category in all future categorization and reporting, provided the category name is between 1 and 50 characters and does not duplicate an existing predefined or custom category name
5. IF the user attempts to create a custom category with a duplicate name or a name exceeding 50 characters, THEN THE Categorization_Engine SHALL reject the creation and display an error message indicating the validation failure reason
6. WHEN the user marks transactions as internal transfers, THE Analytics_Engine SHALL exclude those transactions from spending analytics

### Requirement 6: Analytics and Insights

**User Story:** As a user, I want comprehensive financial analytics and insights, so that I can understand my spending patterns, detect trends, and make informed financial decisions.

#### Acceptance Criteria for Analytics and Insights

1. WHEN at least 2 months of transaction data are available, THE Analytics_Engine SHALL compute monthly spending trends showing total spending per month for each month in the dataset
2. THE Analytics_Engine SHALL compute spending breakdowns by category, by account, and by currency, expressing amounts in their original currency
3. WHEN at least 2 months of transaction data are available, THE Analytics_Engine SHALL compute average monthly burn rate as total expenses divided by the number of months with transaction data
4. WHEN a user specifies a date range, THE Analytics_Engine SHALL identify the top 10 largest expenses by absolute amount within that date range
5. THE Analytics_Engine SHALL detect recurring payments by identifying transactions to the same merchant that occur at intervals of 25–35 days with amounts within 10% of each other, across at least 3 occurrences
6. THE Analytics_Engine SHALL estimate savings rate per month as (total income minus total expenses) divided by total income, expressed as a percentage
7. THE Analytics_Engine SHALL compute income versus expenses comparison per month, showing total income and total expenses as separate values for each calendar month
8. THE Analytics_Engine SHALL perform cash flow analysis showing net inflows and outflows aggregated per calendar month
9. WHEN account balance data is available for at least 2 months, THE Analytics_Engine SHALL compute net worth trend showing total balance across all accounts per calendar month
10. THE Analytics_Engine SHALL compute merchant frequency analysis showing the number of transactions per merchant, sorted by frequency in descending order
11. WHEN spending in a category during the current month exceeds the average monthly spending in that category over the prior 3 months by more than 50%, THE Analytics_Engine SHALL generate an unusual spending alert identifying the category and the percentage over average
12. THE Analytics_Engine SHALL compute month-over-month spending comparisons showing the absolute and percentage difference in total spending between consecutive months
13. WHEN at least 7 days of spending data exist in the current month, THE Analytics_Engine SHALL forecast end-of-month spending by projecting the current daily average spending rate across the remaining days of the month
14. IF fewer than 2 months of transaction data are available for a trend or forecast computation, THEN THE Analytics_Engine SHALL indicate that insufficient data exists and specify the minimum data needed

### Requirement 7: Visual Dashboards

**User Story:** As a user, I want visual dashboards with charts and graphs, so that I can quickly grasp my financial situation at a glance.

#### Acceptance Criteria for Visual Dashboards

1. THE Dashboard SHALL display a pie chart for category-based spending breakdowns, showing each category's percentage of total spending, with categories representing less than 2% of total grouped into an "Other" segment
2. THE Dashboard SHALL display a time-series line graph for spending trends, defaulting to the most recent 6 months at monthly granularity, with the user able to switch granularity to daily or weekly
3. THE Dashboard SHALL display Sankey or cash flow diagrams showing money movement between accounts and categories
4. THE Dashboard SHALL display monthly comparison views showing side-by-side spending for at least 2 and up to 12 user-selected months
5. WHEN the user applies or changes a filter, THE Dashboard SHALL update all visible visualizations within 2 seconds
6. THE Dashboard SHALL display amounts in each visualization using the original transaction currency, and where a visualization aggregates transactions across multiple currencies, THE Dashboard SHALL show separate series or segments per currency
7. IF no transaction data exists for the selected time period or filter combination, THEN THE Dashboard SHALL display an empty-state message indicating no data is available for the current selection

### Requirement 8: Transaction Filtering

**User Story:** As a user, I want to filter transactions by multiple criteria, so that I can focus on specific subsets of my financial data.

#### Acceptance Criteria for Transaction Filtering

1. THE Filter_Engine SHALL return only transactions matching the active filter criteria, supporting the following filter types: date range (start date, end date, or both), account, currency (DOP, BBD, USD), category, merchant, amount range (minimum, maximum, or both), and keywords/tags (case-insensitive partial match against transaction description and tags)
2. WHEN the user applies multiple filters simultaneously, THE Filter_Engine SHALL combine all active filters using logical AND to narrow results
3. WHEN filters are applied, THE Dashboard SHALL update all analytics and visualizations to reflect only the filtered transactions
4. THE Filter_Engine SHALL apply filters within 500 milliseconds for datasets of up to 50,000 transactions
5. IF the applied filters match zero transactions, THEN THE Dashboard SHALL display an empty-state indicator and retain the active filters for the user to modify
6. IF the user provides an invalid filter combination (end date earlier than start date, or minimum amount greater than maximum amount), THEN THE Filter_Engine SHALL display an error message indicating the invalid range and SHALL NOT apply the invalid filter
7. WHEN the user clears all filters, THE Dashboard SHALL display the complete unfiltered transaction set and reset all filter controls to their default state

### Requirement 9: Data Export

**User Story:** As a user, I want to export my processed financial data to CSV and Excel formats, so that I can use the data in other tools or share it.

#### Acceptance Criteria for Data Export

1. WHEN the user requests a CSV export, THE Export_Engine SHALL generate a UTF-8 encoded CSV file with a header row followed by all visible (filtered or unfiltered) transactions, where each row contains the standardized fields: date, description, amount, currency, debit/credit indicator, account name, bank/source, transaction type, category, and notes/tags, with amounts in their original transaction currency
2. WHEN the user requests an Excel export, THE Export_Engine SHALL generate an Excel file with a header row followed by all visible (filtered or unfiltered) transactions on the primary sheet, where each row contains the standardized fields: date, description, amount, currency, debit/credit indicator, account name, bank/source, transaction type, category, and notes/tags, with amounts in their original transaction currency
3. WHEN generating a CSV export, THE Export_Engine SHALL include the applied filter criteria and export date as comment lines prefixed with "#" at the top of the file before the header row
4. WHEN generating an Excel export, THE Export_Engine SHALL include the applied filter criteria and export date on a separate "Metadata" sheet within the workbook
5. IF the export operation fails due to file system errors or insufficient disk space, THEN THE Export_Engine SHALL display an error message indicating the failure reason and preserve the current application state without data loss
6. WHEN an export operation completes successfully, THE Export_Engine SHALL display a success notification indicating the output file path
7. THE Export_Engine SHALL complete the file generation within 30 seconds for datasets of up to 50,000 transactions

### Requirement 10: Session Management

**User Story:** As a user, I want to save and load project sessions, so that I can resume my analysis without re-importing statements.

#### Acceptance Criteria for Session Management

1. WHEN the user saves a session, THE Session_Manager SHALL persist all imported transactions, user-defined categories, exchange rates, and application settings to local storage and display a confirmation indicating the session was saved successfully
2. WHEN the user loads a saved session, THE Session_Manager SHALL restore the application state including all transactions, categories, exchange rates, and settings within 5 seconds for sessions containing up to 100,000 transactions, and display a confirmation indicating the session was loaded successfully
3. IF the session file is corrupted or incompatible, THEN THE Session_Manager SHALL display an error message indicating the nature of the failure (corruption detected or version mismatch) and preserve the current application state without modification
4. WHEN the user requests to save a session, THE Session_Manager SHALL prompt the user to provide a session name (maximum 100 characters) and display a list of existing saved sessions so the user can identify whether to create a new session or overwrite an existing one
5. IF the user selects to overwrite an existing saved session, THEN THE Session_Manager SHALL request confirmation before replacing the previously saved session data

### Requirement 11: Local-First Architecture

**User Story:** As a user, I want the application to run entirely on my local machine with no cloud dependency, so that my financial data remains private and accessible offline.

#### Acceptance Criteria for Local-First Architecture

1. WHEN the host machine has no active network connectivity, THE Application SHALL continue to operate with all non-network features fully functional, making no failed outbound network requests for telemetry, analytics, or update checks
2. IF the host machine has no active network interface, THEN THE Application SHALL start and function with all features available without displaying network-related errors
3. THE Application SHALL store all user data exclusively on the local file system, including imported bank statements, parsed transactions, user preferences, and any generated reports
4. THE Application SHALL provide a JavaFX desktop user interface that renders correctly at screen resolutions from 1280x720 to 3840x2160 with proper DPI scaling
5. THE Application SHALL be distributed as either a standalone Windows installer (MSI or EXE) that bundles a Java runtime, or a runnable JAR that requires the user to have a compatible Java runtime installed
6. THE Application SHALL target Windows 10 or later as the primary platform

### Requirement 12: Security and Data Handling

**User Story:** As a user, I want my financial data handled securely on my local machine, so that sensitive information is protected from unauthorized access.

#### Acceptance Criteria for Security and Data Handling

1. THE Application SHALL store all financial data (imported bank statement content, parsed transactions, account balances, and user-configured settings containing account details) in encrypted form on the local file system
2. IF encryption of financial data fails during a save operation, THEN THE Application SHALL abort the save, retain the previous encrypted version, and display an error message indicating that data could not be saved securely
3. THE Application SHALL NOT transmit any user financial data to external servers or make outbound network requests containing user financial data, even when cloud features are enabled
4. IF the user enables password-protected vault mode, THEN THE Application SHALL require the user to enter the vault password before granting access to stored financial data
5. IF a user fails vault authentication 5 consecutive times, THEN THE Application SHALL lock access to the vault for a minimum of 60 seconds before allowing another attempt

### Requirement 13: CSV Statement Support

**User Story:** As a user, I want to import CSV bank statements in addition to PDFs, so that I can use the application with banks that provide CSV exports.

#### Acceptance Criteria for CSV Statement Support

1. WHEN the user selects one or more CSV files for import, THE Statement_Importer SHALL parse and normalize transactions from CSV format into the standardized transaction structure (date, description, amount, currency, debit/credit indicator, account name, bank/source, transaction type, category, and notes/tags)
2. IF the Format_Detector cannot identify the CSV column layout, THEN THE Statement_Importer SHALL present a column mapping interface allowing the user to assign each CSV column to a standardized transaction field before import proceeds
3. IF a CSV file contains malformed rows, unsupported encoding, or is empty, THEN THE Statement_Importer SHALL report a descriptive error for that file and continue processing remaining files
4. WHEN the user completes a column mapping for an unrecognized CSV format, THE Statement_Importer SHALL save the mapping configuration so it can be reused for future imports from the same source
5. WHILE a CSV import operation is in progress, THE Application SHALL display progress feedback for each file being processed when a progress display component is available; IF progress feedback cannot be displayed, THEN THE Statement_Importer SHALL still proceed with the import without blocking

### Requirement 14: Budget Creation and Tracking

**User Story:** As a user, I want to create budgets for spending categories and track my progress against them, so that I can control my spending.

#### Acceptance Criteria for Budget Creation and Tracking

1. WHEN the user creates a budget for a category, THE Application SHALL store the budget amount (between 0.01 and 999,999,999.99 in the user's selected base currency), the associated category, and the time period defined as either monthly (calendar month) or custom (user-specified start date and end date spanning between 1 and 365 days)
2. WHILE a budget's time period includes the current date, THE Analytics_Engine SHALL compute the remaining budget amount by subtracting all spending in that category (converted to the budget's base currency using the current exchange rates) from the budget limit
3. WHEN spending in a budgeted category reaches 80% of the budget limit, THE Application SHALL display a warning notification on the Dashboard indicating the category name and percentage consumed
4. WHEN spending in a budgeted category exceeds the budget limit, THE Application SHALL display an over-budget alert on the Dashboard indicating the category name and the amount exceeded
5. WHEN the user edits or deletes an existing budget, THE Application SHALL update or remove the budget and recalculate all associated remaining amounts and notification states
6. WHEN a budget's time period end date passes, THE Application SHALL mark the budget as expired and retain it in a read-only historical view without generating further notifications

### Requirement 15: CUDA-Accelerated OCR

**User Story:** As a user, I want the OCR engine to leverage my NVIDIA GPU via CUDA, so that scanned PDF processing is significantly faster than CPU-only processing.

#### Acceptance Criteria for CUDA-Accelerated OCR

1. WHEN a CUDA-compatible NVIDIA GPU is detected on the host machine, THE OCR_Engine SHALL use GPU acceleration for text extraction from scanned PDFs
2. IF no CUDA-compatible GPU is available, THEN THE OCR_Engine SHALL fall back to CPU-based processing without errors or loss of functionality
3. WHEN CUDA acceleration is active, THE OCR_Engine SHALL process a single-page scanned PDF within 3 seconds
4. THE Application SHALL display the current OCR processing mode (GPU-accelerated or CPU) in the system status or settings view
5. WHEN the user imports multiple scanned PDFs, THE OCR_Engine SHALL process pages in parallel using available GPU resources to maximize throughput

### Requirement 16: Gmail Statement Auto-Fetch

**User Story:** As a user, I want to optionally connect my Gmail account to automatically download bank statement PDFs from my email, so that I don't have to manually save and import each statement.

#### Acceptance Criteria for Gmail Statement Auto-Fetch

1. WHEN the user enables Gmail integration, THE Gmail_Fetcher SHALL authenticate via IMAP using the user's Gmail address and a Google App Password, storing the credentials in encrypted form on the local file system
2. THE Gmail_Fetcher SHALL allow the user to configure one or more Gmail accounts, each with its own email address and App Password
3. FOR each configured Gmail account, THE Gmail_Fetcher SHALL allow the user to define one or more fetch rules specifying: sender email address to match, subject line keywords or patterns to match, and an optional date range to limit the search scope
4. WHEN authenticated, THE Gmail_Fetcher SHALL scan only emails matching the user's configured fetch rules and download PDF attachments from those emails
5. WHEN statement PDFs are downloaded from Gmail, THE Gmail_Fetcher SHALL pass them directly to the Statement_Importer for processing using the same pipeline as manual imports
6. THE Gmail_Fetcher SHALL store only the downloaded PDF files and authentication credentials locally; no email content or metadata beyond attachment filenames shall be persisted
7. IF the user disables Gmail integration or removes a configured account, THEN THE Gmail_Fetcher SHALL remove all stored authentication credentials for that account
8. THE Gmail_Fetcher SHALL only operate when explicitly triggered by the user via a "Fetch from Gmail" action or on a user-configured schedule; it SHALL NOT run automatically in the background without user consent
