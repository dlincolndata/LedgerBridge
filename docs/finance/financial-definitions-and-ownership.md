# Financial Definitions, Data Dictionary & Ownership Contract

## 1. Domain Ownership Matrix (AC1)
This matrix maps source system objects, downstream hub storage layers, and key financial reporting metrics to their accountable domain owners.

| Domain Entity / Object | Source System | Hub / Storage Layer | Downstream Metric | Data Owner / Team |
| :--- | :--- | :--- | :--- | :--- |
| **CRM Orders** | Salesforce (`Opportunity` / `Order`) | PostgreSQL (`staging_orders`) | Bookings, Pipeline Forecast | Sales Ops / RevOps |
| **ERP Invoices** | SAP S/4HANA (`API_BILLING_DOCUMENT`) | PostgreSQL (`fact_invoices`) | Gross Billings, Revenue Recognized | Accounting & Billing |
| **ERP Payments & Cash** | SAP S/4HANA (`Journal Entry` / `Clearing`) | PostgreSQL (`fact_payments`) | Cash Applied, Open AR | Treasury & Accounts Receivable |
| **Credit Memos & Adjustments** | SAP S/4HANA (`Credit Memo Request`) | PostgreSQL (`fact_credits`) | Net Billings, Churn Adjustments | Revenue Accounting |
| **Tax & FX Rates** | Tax Engine & ECB / SAP | PostgreSQL (`dim_exchange_rates`) | Net Revenue, Converted USD Ledger | Tax & Corporate Treasury |

---

## 2. KPI Catalog, Grain & Time Basis (AC2)

### A. Bookings
* **Definition:** The total contractual value of a closed-won customer commitment executed in the CRM.
* **Grain:** Order Line Item / Subscription Schedule.
* **Time Basis:** Timestamp of CRM Stage transition to `Closed Won`.
* **Formula:** $\sum (\text{Quantity} \times \text{List Price}) - \text{Approved Contractual Discounts}$

### B. Billings (Gross & Net)
* **Definition:** The actual amount invoiced to the customer for products or services delivered (SAP Billing Documents).
* **Grain:** Individual SAP Billing Document Header & Item.
* **Time Basis:** Invoicing Date (`BillingDate` in SAP).
* **Formula:** $\text{Gross Billings} = \sum \text{Invoice Line Item Amounts}$ (Net Billings deducts applied credit memos).

### C. Cash Applied
* **Definition:** Cash receipts from customers matched and cleared against open ERP invoices.
* **Grain:** Bank Payment Clearing Event / Line Item match.
* **Time Basis:** Clearing Date in SAP General Ledger.
* **Formula:** $\sum \text{Allocated Payment Amount Cleared per Invoice}$

### D. Open Accounts Receivable (Open AR)
* **Definition:** Outstanding invoice balances that have not yet been fully settled by cash application or credit memos.
* **Grain:** Customer Invoice Snapshot (Daily Grain).
* **Time Basis:** End-of-Day (EOD) snapshot timestamp.
* **Formula:** $\text{Total Invoiced Amount} - (\text{Cash Applied} + \text{Applied Credit Memos})$

### E. Credits & Adjustments
* **Definition:** Reductions in invoice amounts issued due to returns, billing errors, or service level agreements (SLAs).
* **Grain:** Credit Memo Document.
* **Time Basis:** Credit Memo Posting Date.

---

## 3. Currency Handling & Tax Policies
* **Tax Policy:** Calculated at the point of invoice generation based on ship-to jurisdiction rules. Stored as a distinct line item and excluded from net product revenue metrics.
* **FX Conversion Policy:** Transactions executed in foreign currencies are translated to reporting currency (USD) using the **ECB Daily Spot Rate** effective on the transaction posting date (or invoice date for billings).
