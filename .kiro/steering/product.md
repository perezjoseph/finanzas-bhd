# Personal Finance Analyzer

## What We're Building

A desktop application for personal finance analysis. It imports bank statement PDFs from BHD (Banco BHD, Dominican Republic), automatically categorizes spending, analyzes trends, and generates financial insights. All data stays on the user's machine — nothing leaves the computer unless the user explicitly connects to Gmail to fetch statements.

## Who It's For

A user with multiple BHD bank accounts (savings and credit cards) denominated in Dominican Pesos (DOP) and US Dollars (USD), who also spends in Barbados using Barbadian Dollars (BBD). They want a single place to see all their finances across currencies and accounts.

## Core Workflow

1. **Import statements** — Drag and drop PDF or CSV bank statements, or fetch them directly from Gmail
2. **Automatic parsing** — The app reads the statement, detects the format, and extracts every transaction
3. **Categorization** — Each transaction is automatically classified (groceries, dining, fuel, subscriptions, etc.)
4. **Analysis** — Dashboards show spending trends, recurring payments, budget progress, and alerts
5. **Export** — Save processed data to CSV or Excel for use elsewhere

## Supported Banks

- **BHD Savings Account** — Monthly statements showing deposits, withdrawals, fees, and running balance
- **BHD Credit Card (VISA Mi País)** — Monthly statements with purchases in both DOP and USD, grouped by currency

## Currencies

- **DOP** — Dominican Peso (RD$)
- **BBD** — Barbadian Dollar
- **USD** — US Dollar (default for reporting)

The user can switch the reporting currency and manually set exchange rates.

## Key Capabilities

- Import multiple statements at once from different accounts
- Handle scanned PDFs using OCR (GPU-accelerated when available)
- Normalize all transactions into a consistent format regardless of source
- Auto-categorize spending with learning from user corrections
- Detect subscriptions and recurring payments automatically
- Alert on unusual spending spikes
- Track budgets per category with warnings at 80% and over-limit
- Filter transactions by date, account, currency, category, merchant, amount, or keywords
- Visualize data with pie charts, time-series graphs, Sankey diagrams, and monthly comparisons
- Fetch statement PDFs directly from Gmail using App Passwords
- Save and restore analysis sessions
- Export to CSV and Excel

## Privacy & Security

- All data stored locally and encrypted
- No network activity unless Gmail fetch is explicitly enabled
- Optional password-protected vault mode
- No telemetry, no analytics, no cloud dependency

## Gmail Integration

Works like a lightweight email client focused on bank statements. The user enters their Gmail address and an App Password, configures which sender (BHD) and subject patterns to look for, and clicks "Fetch" to download statement PDFs directly into the import pipeline.
