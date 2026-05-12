# Implementation Plan: Personal Finance Analyzer

## Overview

This plan addresses the gaps between the existing codebase and the requirements. The project has skeleton implementations for most modules, but critical wiring, UI interactivity, persistence, encryption, and many features are incomplete or non-functional. Tasks are ordered by dependency and priority.

## Tasks

- [x] 1. Project scaffolding and core domain model (pfa-core)
  - [x] 1.1 Create Maven multi-module project structure
  - [x] 1.2 Implement core value objects and enums
  - [x] 1.3 Implement Transaction, Account, and Category records
  - [x] 1.4 Implement Budget, ExchangeRate, and Session data models
  - [x] 1.5 Implement Result type and error model
  - [x] 1.6 Define core service interfaces

- [x] 2. Currency conversion (pfa-core)
  - [x] 2.1 Implement CurrencyConverter with rate store
  - [x] 2.2 Write property tests for CurrencyConverter — identity
  - [x] 2.3 Write property tests for CurrencyConverter — reciprocal consistency
  - [x] 2.4 Write property tests for CurrencyConverter — round-trip stability
  - [x] 2.5 Write property tests for CurrencyConverter — triangulation

- [x] 3. Checkpoint — Core model and currency

- [x] 4. Fix application wiring and make UI functional
  - [x] 4.1 Fix AppController compilation and wire all shell buttons
    - Add `wireSettingsView()` method to AppController that connects SettingsView buttons (Add Gmail, Remove Gmail, Fetch, Save Session, Load Session, Save Rates) to ServiceFacade methods
    - Wire navigation toggle buttons so clicking Dashboard/Transactions/Budgets/Settings switches the center view and refreshes data
    - Wire toolbar Import/Export CSV/Export Excel/Gmail buttons to their handler methods
    - Wire currency selector to `facade.setBaseCurrency()` and refresh dashboard
    - Ensure the app compiles and launches without errors
    - _Requirements: 11.4_

  - [x] 4.2 Implement Gmail account add/remove dialog
    - Create a dialog that prompts for email address and App Password (masked input)
    - On OK, call `facade.saveGmailAccount(new GmailAccount(email, password.toCharArray()))`
    - Add the email to `settingsView.getGmailAccountsList()`
    - Wire Remove button to remove selected account from list and call `facade.removeGmailAccount(email)`
    - Wire Fetch button to call `facade.fetchGmail()` with the selected account and configured rules, show results in an alert
    - _Requirements: 16.1, 16.2, 16.7_

  - [x] 4.3 Implement session save/load in Settings
    - Wire Save Session button: prompt for session name (max 100 chars), call `facade.saveSession(name)`, show confirmation
    - Wire Load Session button: show list of `facade.listSessions()`, on selection call `facade.loadSession(handle)`, refresh all views
    - Display existing sessions in `settingsView.getSessionsList()`
    - Handle overwrite confirmation when name matches existing session
    - _Requirements: 10.1, 10.2, 10.4, 10.5_

  - [x] 4.4 Wire exchange rate saving
    - Read values from `settingsView.getUsdToDopField()`, `getUsdToBbdField()`, `getDopToBbdField()`
    - Validate rates (> 0, ≤ 999,999), show error on invalid input
    - Call `facade.setExchangeRate()` for each pair
    - Show confirmation on success
    - _Requirements: 4.3, 4.5_

- [x] 5. Fix Import Dialog with account assignment and progress
  - [x] 5.1 Add account assignment to ImportDialog
    - For each selected file, show a row with filename + ComboBox for bank (BHD) + ComboBox for account type (Savings/Credit Card)
    - Pass the user's account assignments to the import pipeline instead of hardcoded "default"
    - _Requirements: 1.2_

  - [x] 5.2 Add per-file progress display during import
    - Show status per file: pending → processing → completed/failed
    - Display error messages for rejected files (too large, not PDF/CSV, corrupted, duplicate, empty)
    - Keep dialog open during import showing progress, close on completion
    - _Requirements: 1.3, 1.4, 1.5, 1.6, 1.7_

- [x] 6. Fix FilterPanel with all filter types
  - [x] 6.1 Add account and category filter controls
    - Add account multi-select (populated from imported transactions' account IDs)
    - Add category multi-select (populated from `facade.getCategorizationEngine().allCategories()`)
    - Include these in the built `FilterCriteria`
    - Add validation error display for invalid date/amount ranges
    - _Requirements: 8.1, 8.2, 8.6_

- [x] 7. Fix Transactions view with inline editing
  - [x] 7.1 Add inline category editing
    - Make the Category column editable with a ComboBox cell showing all categories (predefined + custom)
    - On edit commit, call `facade.getCategorizationEngine().recordOverride(tx, newCategory)` and update the transaction
    - _Requirements: 5.3_

  - [x] 7.2 Add internal transfer toggle
    - Add a checkbox column "Internal Transfer"
    - On toggle, update the transaction's `isInternalTransfer` flag and persist
    - _Requirements: 5.6_

- [x] 8. Fix Dashboard with complete visualizations
  - [x] 8.1 Implement "Other" grouping for pie chart
    - Group categories representing < 2% of total into an "Other" segment
    - _Requirements: 7.1_

  - [x] 8.2 Add granularity switching to trend chart
    - Add a toggle (Daily/Weekly/Monthly) above the trend chart
    - Recompute and redisplay the line chart based on selected granularity
    - Default to monthly, last 6 months
    - _Requirements: 7.2_

  - [x] 8.3 Implement monthly comparison bars
    - Add a bar chart showing side-by-side spending for 2–12 user-selected months
    - _Requirements: 7.4_

  - [x] 8.4 Implement multi-currency series in charts
    - When transactions span multiple currencies, show separate series/segments per currency instead of mixing
    - _Requirements: 7.6_

- [x] 9. Fix Budgets view with full CRUD
  - [x] 9.1 Wire Add Budget button
    - Read category, amount, period from the form fields
    - Validate amount (0.01–999,999,999.99), period (Monthly or Custom with start/end dates)
    - Call `facade.addBudget(budget)` and refresh the list
    - _Requirements: 14.1_

  - [x] 9.2 Wire Delete Budget button
    - Delete selected budget via `facade.removeBudget(id)`
    - Refresh the list
    - _Requirements: 14.5_

  - [x] 9.3 Show budget warnings and alerts
    - Display 80% warning styling and over-limit alert styling on budget cards
    - Mark expired budgets as read-only
    - _Requirements: 14.3, 14.4, 14.6_

- [x] 10. Fix persistence — full session serialization
  - [x] 10.1 Implement complete SessionSnapshot serialization
    - Replace the placeholder `serializeSnapshot`/`deserializeSnapshot` in `DefaultSessionManager` with full JSON or binary serialization of all fields: transactions, accounts, categories, learned rules, exchange rates, budgets, settings
    - Implement atomic save (write to temp, then move)
    - Handle schema version checking on load
    - _Requirements: 10.1, 10.2, 10.3_

  - [x] 10.2 Implement data encryption
    - Encrypt the workspace database file using AES-256-GCM
    - Derive encryption key from vault password via Argon2id when vault mode is enabled
    - Use Windows DPAPI for key storage when vault mode is disabled
    - Abort save on encryption failure, retain previous version
    - _Requirements: 12.1, 12.2_

  - [x] 10.3 Wire vault mode in UI
    - When vault checkbox is toggled ON in Settings, prompt for password, call `vaultManager.enableVault(password)`
    - On app startup, if vault is enabled, show password prompt before loading data
    - Implement 5-attempt lockout (60 seconds) in the UI
    - _Requirements: 12.4, 12.5_

- [x] 11. Fix Gmail integration — fetch rules UI
  - [x] 11.1 Add fetch rules configuration UI
    - In Settings, allow user to add/edit/remove fetch rules per Gmail account: sender pattern, subject pattern, optional date range
    - Store rules and pass them to `facade.fetchGmail(account, rules)`
    - _Requirements: 16.3_

  - [x] 11.2 Show fetch results
    - After fetch completes, show dialog with count of downloaded PDFs and any errors
    - Auto-import downloaded PDFs through the standard import pipeline
    - _Requirements: 16.4, 16.5, 16.6_

- [x] 12. Add missing analytics to Dashboard
  - [x] 12.1 Add recurring payments panel
    - Show detected recurring payments (merchant, frequency, average amount) in a table or list on the dashboard
    - _Requirements: 6.5_

  - [x] 12.2 Add savings rate display
    - Show monthly savings rate as (income - expenses) / income percentage
    - _Requirements: 6.6_

  - [x] 12.3 Add income vs expenses comparison
    - Show monthly income vs expenses as a grouped bar chart or table
    - _Requirements: 6.7_

  - [x] 12.4 Add forecast display
    - Show projected end-of-month spending when ≥ 7 days of data exist
    - _Requirements: 6.13_

- [x] 13. Property-based tests for import pipeline
  - [x] 13.1 Write property tests for TransactionNormalizer (already exists — verify passing)
    - **Property: Currency preservation** — normalized currency equals raw currency
    - **Property: Amount non-negativity** — normalized amount ≥ 0
    - **Property: Description length bound** — ≤ 256 characters
    - **Property: Required field completeness** — no MissingRequired issues when all fields present
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [x] 13.2 Write property tests for CategorizationEngine (already exists — verify passing)
    - **Property: Total coverage** — assign() never returns null
    - **Property: Override precedence** — learned overrides take priority
    - **Property: Fallback guarantee** — unmatched → Miscellaneous
    - _Requirements: 5.1, 5.2, 5.3_

- [x] 14. Packaging
  - [x] 14.1 Verify jpackage installer profile works
    - Install WiX Toolset or configure for EXE output
    - Run `mvn package -P installer` and verify MSI/EXE is produced
    - Alternatively, configure a fat JAR with `maven-shade-plugin` for users without WiX
    - _Requirements: 11.5, 11.6_

- [x] 15. Final verification
  - [x] 15.1 End-to-end test: import a real BHD savings PDF, verify transactions appear in table, categories assigned, dashboard charts populated
  - [x] 15.2 End-to-end test: import a BHD credit card PDF, verify dual-currency transactions parsed correctly
  - [x] 15.3 End-to-end test: import a CSV, verify column mapping works
  - [x] 15.4 End-to-end test: export to CSV and Excel, verify file contents
  - [x] 15.5 End-to-end test: save and load session, verify state restored
  - [x] 15.6 Verify all property tests pass: `mvn test`

- [x] 16. Fix Gmail IMAP authentication with App Passwords
  - [x] 16.1 Force PLAIN auth mechanism in DefaultGmailFetcher
    - Jakarta Mail was attempting XOAUTH2 authentication (which Gmail advertises) instead of PLAIN when using App Passwords, causing auth failures
    - Added `props.setProperty("mail.imaps.auth.mechanisms", "PLAIN")` to IMAP properties in `DefaultGmailFetcher.createImapProperties()`
    - This forces Jakarta Mail to use PLAIN auth over the existing SSL/TLS connection, which is the correct mechanism for Google App Passwords
    - _Requirements: 16.1_

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["4.1"] },
    { "id": 1, "tasks": ["4.2", "4.3", "4.4", "5.1", "5.2", "6.1"] },
    { "id": 2, "tasks": ["7.1", "7.2", "8.1", "8.2", "8.3", "8.4", "9.1", "9.2", "9.3"] },
    { "id": 3, "tasks": ["10.1", "10.2", "10.3", "11.1", "11.2"] },
    { "id": 4, "tasks": ["12.1", "12.2", "12.3", "12.4"] },
    { "id": 5, "tasks": ["13.1", "13.2"] },
    { "id": 6, "tasks": ["14.1"] },
    { "id": 7, "tasks": ["15.1", "15.2", "15.3", "15.4", "15.5", "15.6"] },
    { "id": 8, "tasks": ["16.1"] }
  ]
}
```
