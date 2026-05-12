# Product

## Register

product

## Users

Single power user: a developer who manages multiple BHD bank accounts (savings and credit cards) across three currencies (DOP, USD, BBD). Uses the app on their own machine, at a desk, often reviewing a month's worth of transactions in one sitting. Wants to see everything, fast, without navigating through layers.

## Product Purpose

A local-first desktop tool that imports BHD bank statement PDFs, parses every transaction, auto-categorizes spending, and surfaces trends and anomalies. Success looks like: drop a PDF, see categorized transactions in seconds, spot where money went without manual spreadsheet work.

## Brand Personality

Sharp. Dense. Confident.

The app speaks like a well-organized terminal: no filler, no decoration for its own sake, no "friendly" copy that wastes space. It respects the user's expertise and shows data directly.

## Anti-references

- Generic consumer banking apps (Chase, BofA): oversimplified, hide data behind "insights," patronizing tone
- YNAB: gamification, envelope metaphors, too much onboarding for a single-user tool
- Mint: ad-cluttered, slow, unreliable categorization surfaced as confident
- Any fintech with navy-and-gold palettes, stock photography of happy families, or "your money, simplified" messaging
- SaaS dashboards with hero-metric cards (big number, small label, gradient accent)

## Design Principles

1. **Data density over decoration**: Show more rows, fewer borders. Chrome earns its pixels or gets cut.
2. **Keyboard-navigable**: Power user, not tourist. Every action reachable without a mouse.
3. **Glanceable hierarchy**: The important number is always obvious. Scanning a table of 200 transactions should feel effortless.
4. **Zero friction import**: Drag, drop, done. No wizards, no confirmation dialogs for routine operations.
5. **Trust through transparency**: Show the raw parsed data alongside any categorization or summary. Never hide the source.

## Accessibility & Inclusion

- WCAG AA contrast ratios (4.5:1 body text, 3:1 large text and UI components)
- No reliance on color alone for meaning (pair with icons or labels)
- Respect prefers-reduced-motion
- High contrast between data and chrome so numbers pop at any hour
- Single user, no i18n required; Spanish labels acceptable where they match source data (transaction descriptions stay in original language)
