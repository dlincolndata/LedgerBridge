# LB-003 | Financial Definitions & Authoritative Ownership Contract

## 1. Authoritative Ownership Matrix (AC1)

| Domain Entity / Metric | Authoritative Owner (System of Record) | Downstream Consumer / Projection | Mutation & Governance Rule |
| :--- | :--- | :--- | :--- |
| **Commercial Orders & Quotes (`fact_orders`)** | **Salesforce CRM** (`Opportunity`, `Quote`, `Order`) | LedgerBridge Hub, SAP EBDR Staging | Owned by CRM up to `Closed-Won` / order activation; immutable in hub once submitted to ERP. |
| **Customer Invoices (`fact_invoices`)** | **SAP S/4HANA** (`API_BILLING_DOCUMENT_SRV`, FI Doc `DR` / `RV`) | LedgerBridge Hub, Salesforce Read-Only View | SAP owns invoice numbers, posting dates, tax, and gross/net amounts. Never mutated by CRM. |
| **Customer Payments (`fact_payments`)** | **SAP S/4HANA** (`FI-AR Clearing`, FI Doc `DZ` / `AB`) | LedgerBridge Hub, Salesforce Read-Only View | Stored in a `1 Invoice -> N Payments` relationship; SAP owns clearing dates and cash applied. |
| **Customer Credit Memos (`fact_credits`)** | **SAP S/4HANA** (`Billing Type G2`, FI Doc `DG` / `RV`) | LedgerBridge Hub, Salesforce Read-Only View | Immutable adjustment documents linked to the original invoice; posted invoices are never edited/deleted. |
| **Reconciliation Snapshots** | **LedgerBridge Hub** (`PostgreSQL` Fact/Snapshot Tables) | Financial Reporting & Audit Views | Point-in-time auditable snapshots linking CRM Order IDs to SAP Billing/Clearing IDs. |
| **Reporting KPIs** | **LedgerBridge Hub** (Governed SQL/Domain Formulas) | BI Dashboards & Executive Reporting | Computed strictly from normalized fact tables using the KPI Catalog below. |

---

## 2. KPI Catalog, Grain & Time Basis (AC2)

| KPI Metric | Table Grain | Time Basis | Authoritative Formula | Tax & Sign Treatment |
| :--- | :--- | :--- | :--- | :--- |
| **Bookings** | 1 row per closed-won Salesforce Order/Quote (`fact_orders`) | CRM `ClosedDate` / Order Activation Date | $\sum \text{Contracted Net Order Value}$ | **Excludes** tax (`+`) |
| **Gross Billings** | 1 row per SAP Billing Document (`fact_invoices`) | SAP Invoice Posting Date (`BillingDocumentDate`) | $\text{Net Subtotal} + \text{Tax Amount}$ | **Includes** pass-through tax (`+`) |
| **Net Billings** | 1 row per SAP Invoice (`fact_invoices`) minus Credit Memos (`fact_credits`) | SAP Posting Date (`BillingDocumentDate`) | $\sum \text{Invoice Net Subtotal} - \sum \text{Credit Memo Net Subtotal}$ | **Excludes** pass-through tax (`+` / `-`) |
| **Cash Applied** | 1 row per SAP Clearing Allocation (`fact_payments`) | SAP Clearing Date (`ClearingDate`) | $\sum \text{Allocated Payment Amount}$ | **Includes** tax portion settled (`+`) |
| **Open AR** | Derived per Invoice (`fact_invoices` + `fact_credits` + `fact_payments`) | As-of Reporting Snapshot Timestamp | $\text{Gross Billed Amount} - \text{Gross Credit Memos} - \text{Cash Applied}$ | **Includes** unpaid tax (`>= 0`) |

---

## 3. Tax & Multi-Currency (FX) Handling Policies (AC2 & IMPLEMENT)

### Tax Policy
* **System of Record:** SAP S/4HANA computes and owns authoritative sales tax / VAT (`tax_amount`, tax code, jurisdiction). Salesforce tax figures are non-binding estimates.
* **Revenue vs. AR Rule:** Pass-through tax is **excluded** from revenue KPIs (**Bookings**, **Net Billings**) because it is a tax liability owed to the government, but **included** in **Gross Billings**, **Cash Applied**, and **Open AR** because the customer is legally billed for—and must pay—the full gross balance.

### Multi-Currency & FX Policy (Dual-Currency Storage)
Every financial fact row (`fact_invoices`, `fact_payments`, `fact_credits`) persists monetary values in **both** currencies without overwriting source figures:
1. **Transaction Currency (`transaction_currency`, `amount_txn`):** Exact currency billed to the customer (e.g., `EUR`, `GBP`, `USD`), preserved immutably as posted in SAP.
2. **Functional / Reporting Currency (`reporting_currency = 'USD'`, `amount_usd`):** Converted using the SAP Exchange Rate (`exchange_rate`) captured on the **Invoice Posting Date** (for billings/credits) and **Clearing Date** (for payments).
3. **Realized FX Gain/Loss:** Exchange rate fluctuations between invoice posting and payment clearing are recorded as Realized FX Gain/Loss in SAP FI; an invoice settled in full in `transaction_currency` always closes to `0.00` Open AR.

---

## 4. Data Dictionary (Core Fact Tables)

### `fact_invoices` (SAP Customer Invoices)
* `invoice_fact_id` (`UUID`, PK): Surrogate primary key in LedgerBridge.
* `sap_billing_doc_id` (`VARCHAR(20)`, Unique): Authoritative SAP Billing Document / FI Document ID (`DR` / `RV`).
* `sap_fi_doc_type` (`VARCHAR(4)`): SAP FI Document Type (`DR`).
* `salesforce_order_id` (`VARCHAR(50)`): Upstream CRM Order/Quote reference.
* `company_code` (`VARCHAR(4)`): SAP Company Code (e.g., `1710`).
* `billing_date` (`DATE`): Authoritative SAP posting date.
* `transaction_currency` (`CHAR(3)`): Customer billing currency (ISO 4217).
* `exchange_rate` (`NUMERIC(12,6)`): SAP posting-date FX rate to USD.
* `net_subtotal_txn` (`NUMERIC(15,2)`): Net service/product revenue excluding tax.
* `tax_amount_txn` (`NUMERIC(15,2)`): Authoritative SAP pass-through tax.
* `gross_billed_txn` (`NUMERIC(15,2)`): $\text{net\_subtotal\_txn} + \text{tax\_amount\_txn}$.
* `net_subtotal_usd` / `gross_billed_usd` (`NUMERIC(15,2)`): Functional USD equivalents.

### `fact_payments` (SAP Customer Payments — `1 Invoice : N Payments`)
* `payment_fact_id` (`UUID`, PK): Surrogate primary key in LedgerBridge.
* `sap_clearing_doc_id` (`VARCHAR(20)`): SAP FI Clearing / Payment Document ID (`DZ` / `AB`).
* `sap_fi_doc_type` (`VARCHAR(4)`): SAP FI Document Type (`DZ` or `AB`).
* `sap_billing_doc_id` (`VARCHAR(20)`, FK -> `fact_invoices`): Target invoice receiving cash allocation.
* `clearing_date` (`DATE`): Date payment was applied/cleared in SAP.
* `transaction_currency` (`CHAR(3)`): Payment currency.
* `exchange_rate` (`NUMERIC(12,6)`): SAP clearing-date FX rate to USD.
* `applied_amount_txn` (`NUMERIC(15,2)`): Cash applied against the target invoice's gross balance.
* `applied_amount_usd` (`NUMERIC(15,2)`): Functional USD equivalent at clearing rate.

### `fact_credits` (SAP Customer Credit Memos)
* `credit_fact_id` (`UUID`, PK): Surrogate primary key in LedgerBridge.
* `sap_credit_doc_id` (`VARCHAR(20)`, Unique): SAP Credit Memo Document ID (`DG` / `RV`).
* `sap_fi_doc_type` (`VARCHAR(4)`): SAP FI Document Type (`DG`).
* `sap_billing_doc_id` (`VARCHAR(20)`, FK -> `fact_invoices`): Original invoice being credited.
* `credit_date` (`DATE`): SAP credit memo posting date.
* `net_credit_txn` / `tax_credit_txn` / `gross_credit_txn` (`NUMERIC(15,2)`): Net, tax, and gross credit amounts.

---

## 5. SAP Provider-Specific Assumption Log (`BLART` Document Classification)

LedgerBridge enforces strict document-type filtering when ingesting SAP FI/SD postings so only Quote-to-Cash Accounts Receivable events affect customer revenue and Open AR:

### In-Scope SAP Document Types (Ingested & Reconciled)
| SAP Doc Type (`BLART`) | Official SAP Description | LedgerBridge Classification | Accounting Behavior |
| :--- | :--- | :--- | :--- |
| **`DR`** | Customer invoice *(paired with SD Billing `F2` -> `RV`)* | `fact_invoices` | Increases Gross Billings, Net Billings, and customer Open AR (`+`). |
| **`DZ`** | Customer payment | `fact_payments` | Increases Cash Applied (`+`) and reduces customer Open AR (`-`). |
| **`DG`** | Customer credit memo *(paired with SD Credit `G2` -> `RV`)* | `fact_credits` | Reduces Net Billings (`-`) and reduces customer Open AR (`-`). |
| **`AB`** | General / Clearing document | `fact_payments` / `AR Adjustment` | Ingested only when tied to a Customer (`KOART = 'D'`) AR clearing/netting event. |

### Out-of-Scope SAP Document Types (Explicitly Excluded)
| SAP Doc Type (`BLART`) | Official SAP Description | Exclusion Rationale |
| :--- | :--- | :--- |
| **`KR`** | Vendor invoice | Accounts Payable (Procure-to-Pay) liability; out of Quote-to-Cash scope. |
| **`KZ`** | Vendor payment | Outgoing AP cash disbursement; out of Quote-to-Cash scope. |
| **`KG`** | Vendor credit memo | Accounts Payable supplier adjustment; out of Quote-to-Cash scope. |
| **`KN`** | Vendor net invoice and credit memo | Net-method Accounts Payable document; out of Quote-to-Cash scope. |
| **`CO`** | CO posting | Internal Controlling cost-center/profitability allocation; no external customer AR impact. |
| **`SA`** | General G/L accounts | Manual G/L journal postings without customer subledger (`KOART = 'D'`) sub-items. |

---

## 6. Formula Verification Walkthrough (VERIFY)

**Scenario:** Enterprise customer signs a `$10,000.00` USD annual contract in Salesforce, billed in Georgia with `6%` sales tax (`$600.00`), followed by a partial payment and a partial service credit.

1. **CRM Closed-Won Order (`fact_orders`):**
   * **Bookings:** `$10,000.00` *(CRM Order Date; excludes tax)*
2. **SAP Posts Customer Invoice (`DR` -> `fact_invoices`):**
   * `net_subtotal_txn` = `$10,000.00` | `tax_amount_txn` = `$600.00`
   * **Gross Billings:** $\$10,000.00 + \$600.00 = \$10,600.00$
   * **Net Billings:** `$10,000.00`
   * **Open AR:** `$10,600.00`
3. **Customer Submits Partial Payment (`DZ` -> `fact_payments`):**
   * `applied_amount_txn` = `$6,000.00`
   * **Cash Applied:** `$6,000.00`
   * **Open AR:** $\$10,600.00 - \$6,000.00 = \$4,600.00$
4. **SAP Issues Partial Credit Memo (`DG` -> `fact_credits`):**
   * `net_credit_txn` = `$1,000.00` | `tax_credit_txn` = `$60.00` | `gross_credit_txn` = `$1,060.00`
   * **Net Billings:** $\$10,000.00 - \$1,000.00 = \$9,000.00$
   * **Open AR:** $\$10,600.00 - \$1,060.00 - \$6,000.00 = \$3,540.00$
