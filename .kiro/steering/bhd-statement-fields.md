# BHD Statement Field Reference

This document describes every field found in BHD bank statement PDFs. Use it when parsing, validating, or displaying transaction data.

---

## Savings Account Statement (Estado de Cuenta - Cuentas de Ahorro)

### Header / Account Info

| Field | Description | Example |
|-------|-------------|---------|
| Numero de Cuenta | Masked account number | XXXXXXX-002-2 |
| Numero de Cuenta Regional | Full IBAN-style identifier (DO + check digits + bank code + account) | DO89BCBH000000000XXXXXXX0022 |
| Moneda | Currency of the account (US$ or RD$) | US$ |
| Fecha de Corte | Statement closing date (dd/mm/yyyy) | 28/02/2026 |
| Balance Inicial | Opening balance at start of period | $2,026.87 |
| Cheques en Transito | Outstanding checks not yet cleared | $0.00 |
| Balance Final | Closing balance at end of period | $4,538.96 |

### Customer Info

| Field | Description | Example |
|-------|-------------|---------|
| Nombre | Account holder full name | PANIAGUA RIJO, JORGE DAVID |
| Identificación | Customer ID / document number | 2795076 / 7883 |
| Dirección | Street address | 5to centenario 1 N. 1 |
| Sector | Neighborhood or sector | CENTRO DE LA CIUDAD |
| Provincia | Province | LA ALTAGRACIA |

### Transaction Row Fields

Each transaction line contains:

| Field | Description | Example |
|-------|-------------|---------|
| Fecha | Transaction date (dd/mm/yyyy) | 24/02/2026 |
| Ref. | Internal reference number | 1332246 |
| Detalle | Transaction description/concept | CRTRINTL: HUMICLIMA (BARBADOS) LTD USD TRA |
| Debitos | Amount debited (withdrawn) from account | $10.00 |
| Creditos | Amount credited (deposited) to account | $3,173.00 |
| Balance | Running balance after this transaction | $5,199.87 |

### Transaction Types (by Detalle prefix/pattern)

| Pattern | Meaning |
|---------|---------|
| CRTRINTL: ... USD TRA | International wire transfer (credit or fee) |
| PAGO DE TC XXXX XXXX XXXX XXXX | Credit card payment from this account |
| Impuesto 0.15% Ley 288-04 | Government tax on financial transactions (Law 288-04) |
| Ret.ley 253-12 10% DGII CapUS$ | Tax withholding on interest (Law 253-12, 10% DGII) |
| Pago Intereses CA US$ | Interest payment on savings account |

### Summary Row

| Field | Description | Example |
|-------|-------------|---------|
| 0 CKS | Number of checks processed in period | 0 |
| Total (Debitos) | Sum of all debits | $660.98 |
| Total (Creditos) | Sum of all credits | $3,173.07 |
| Balance Final | Closing balance (must match header) | $4,538.96 |

---

## Credit Card Statement (Estado de Cuenta de Tarjeta de Crédito)

### Header / Card Info

| Field | Description | Example |
|-------|-------------|---------|
| Tipo de tarjeta | Card product name | VISA MI PAIS |
| Numero de tarjeta | Masked card number (first 6 + last 4) | 464133******6819 |
| Límite de crédito | Credit limit (may be dual-currency) | RD$ 20,000.00 y US$ 1,000.00 |
| Fecha de corte | Statement closing date (dd/mm/yyyy) | 26/02/2026 |
| Fecha límite de pago | Payment due date (dd/mm/yyyy) | 23/03/2026 |

### Customer Info

| Field | Description | Example |
|-------|-------------|---------|
| Nombre | Cardholder full name | JORGE DAVID PANIAGUA RIJO |
| Email | Contact email | jorgedavid019@gmail.com |
| Dirección | Street address | 1 NO 1 5to centenario 1 |
| Ciudad | City | HIGUEY |
| Provincia | Province | LA ALTAGRACIA |
| País | Country | REPUBLICA DOMINICANA |

### Statement Summary (Resumen del corte)

Presented in two columns: PESOS (DOP) and DÓLARES (USD).

| Field | Description | Pesos Example | Dólares Example |
|-------|-------------|---------------|-----------------|
| Balance anterior | Previous statement balance | -132.85 | 386.42 |
| Pagos y créditos | Payments and credits applied this period | 0.00 | 1,490.95 |
| Compras y débitos | Purchases and debits this period | 0.00 | 1,393.03 |
| Balance al corte | Current statement balance (amount owed) | -132.85 | 288.50 |

Note: A negative balance in Pesos means the bank owes the customer (overpayment or credit).

### Credit Line Summary (Línea de crédito cuotas BHD)

Only in Pesos. Used for installment purchases.

| Field | Description | Example |
|-------|-------------|---------|
| Crédito disponible | Available installment credit | 0.00 |
| Balance anterior | Previous installment balance | 0.00 |
| Pagos | Payments toward installments | 0.00 |
| Compras o avances del mes | Installment purchases or cash advances this month | 0.00 |
| Cuota | Monthly installment payment amount | 0.00 |
| Balance al corte | Current installment balance | 0.00 |

### Minimum Payment (Pago mínimo)

| Field | Description | Pesos Example | Dólares Example |
|-------|-------------|---------------|-----------------|
| Tarjeta de crédito | Minimum payment for regular credit | 0.00 | 8.02 |
| Cuotas BHD | Minimum payment for installment line | 0.00 | — |
| Pago mínimo total | Total minimum payment due | 0.00 | 8.02 |

### Transaction Row Fields

Transactions are grouped by currency section: "TRANSACCIONES EN DOLARES US$" or "TRANSACCIONES EN PESOS RD$".

| Field | Description | Example |
|-------|-------------|---------|
| Fecha trans. | Transaction date (dd/mm/yyyy) — when purchase was made | 22/01/2026 |
| Fecha aplicación | Posting date (dd/mm/yyyy) — when it appeared on account | 27/01/2026 |
| Número de tarjeta | Last 4 digits of card used | 6819 |
| Concepto | Merchant name and location | MASSY STORES Worthing-BB |
| Débitos | Amount charged (purchase) | 99.56 |
| Créditos | Amount credited (payment/refund) | 840.95 |

### Transaction Types (by Concepto pattern)

| Pattern | Meaning |
|---------|---------|
| MERCHANT_NAME LOCATION-COUNTRY | Standard purchase (e.g., "MASSY STORES Oistins-BB") |
| PAGO DEBITO A CUENTA MBP | Payment from linked bank account (Mobile Banking Payment) |
| PAYPAL *SERVICE* ID LOCATION-COUNTRY | Online subscription via PayPal |

### Country Codes in Merchant Location

| Code | Country |
|------|---------|
| BB | Barbados |
| GB | United Kingdom (used by some online services) |
| DO | Dominican Republic |

### Totals Row

| Field | Description | Example |
|-------|-------------|---------|
| TOTAL DE TRANSACCIONES EN DOLARES US$ (Débitos) | Sum of all USD purchases | 1,393.03 |
| TOTAL DE TRANSACCIONES EN DOLARES US$ (Créditos) | Sum of all USD payments/credits | 1,490.95 |

### Rewards Program (Información de Estrellas)

| Field | Description | Example |
|-------|-------------|---------|
| Estrellas ganadas en el mes | Reward points earned this month | 684.00 |
| Estrellas vencidas en el mes | Points expired this month | 72.00 |
| Estrellas canjeadas en el mes | Points redeemed this month | 0.00 |
| Estrellas disponibles | Total available points balance | 4,939.00 |
| Estrellas próximas a vencer | Points expiring soon | 272.00 |

### Interest Rates (Tasa Financiamiento Anual)

Presented separately for RD$ and US$.

| Field | Description | RD$ Example | US$ Example |
|-------|-------------|-------------|-------------|
| Tasa financiamiento | Annual financing rate (APR) | 60.00% | 0.00% |
| Tasa compra cuotas BHD | Installment purchase rate | 0.00% | — |
| Tasa avance cuotas BHD | Cash advance installment rate | 0.00% | — |

### Average Balances (Saldos Promedios)

| Field | Description | RD$ Example | US$ Example |
|-------|-------------|-------------|-------------|
| Intereses financiamiento | Finance charges accrued | 0.00 | 0.00 |
| Interes si opta x financiar | Interest if you choose to finance (carry balance) | 0.00 | 22.41 |
| Bal. prom. consumos mes anterior | Average daily balance from previous month | 0.00 | 0.00 |
| Bal. prom. consumos mes actual | Average daily balance current month | 0.00 | 448.18 |

### Payment Summary Table (bottom of statement)

| Field | Description |
|-------|-------------|
| Cuotas vencidas | Number of overdue installments |
| Importe vencido | Overdue amount |
| Pago mínimo (Tarjeta de crédito) | Minimum payment for card balance |
| Pago mínimo (Cuotas BHD) | Minimum payment for installment balance |
| Pago mínimo total | Combined minimum payment |
| Pago total (Tarjeta de crédito) | Full payoff amount for card |
| Pago total (Cuotas BHD) | Full payoff for installments |
| Pago total | Total amount to pay off everything |

---

## Parsing Notes

- Dates are always dd/mm/yyyy format
- Amounts use period as decimal separator and comma as thousands separator
- Credit card statements may span multiple pages; transactions continue across page breaks
- The "Balance al inicial" line in savings statements is not a transaction — it's the period opening marker
- Negative balances on credit cards mean the bank owes the customer
- Transaction dates (Fecha trans.) can precede the statement period — they post (Fecha aplicación) within the period
- Country code suffix after merchant location (e.g., "-BB") indicates where the transaction occurred
- "MBP" in payment descriptions stands for Mobile Banking Payment
