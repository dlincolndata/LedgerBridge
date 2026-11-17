# LB-002 | SAP S/4HANA Cloud & Salesforce API Connectivity Specification

## 1. Purpose & Governance Boundary (AC1)
This document defines the authorized integration boundaries, tenant classifications, company codes, and API communication arrangements required for **LedgerBridge** to exchange Quote-to-Cash financial data between **Salesforce** and **SAP S/4HANA Cloud Public Edition**.

> **Security & Privilege Rule:** Having a human developer or administrator login (e.g., SAP Fiori Launchpad, SAP BTP Cockpit, or Salesforce UI login) **does not** grant API read/write permissions. All middleware traffic must authenticate via dedicated, least-privilege machine identities (SAP Communication Users and Salesforce External Client/Connected Apps) bound to explicit API scopes.

---

## 2. SAP S/4HANA Cloud Public Edition Topology

| Attribute | Value / Specification | Notes |
| :--- | :--- | :--- |
| **Tenant Classification** | SAP S/4HANA Cloud Public Edition — Development / Test Tenant *(or SAP Business Accelerator Hub Sandbox)* | 3-System Landscape (`DEV` / `TEST` / `PROD`); no direct ABAP/DB access permitted. |
| **Target Company Code** | `1710` (US Standard) | Primary legal entity for commercial billing and accounts receivable postings. |
| **Communication System** | `LEDGERBRIDGE_CRM_SYS` | Represents the external Spring Boot middleware host in SAP Communication Management. |
| **Communication User** | `CC_LEDGERBRIDGE` | Technical inbound API user (Basic Auth or OAuth 2.0 Client Credentials / mTLS); no interactive Fiori login capability. |
| **Execution Mode** | `SIMULATED` *(Override via `CONNECTIVITY_MODE=LIVE`)* | Explicitly marked `SIMULATED` / `BLOCKED` until a live SAP Public Cloud tenant with active arrangements is bound. |

---

## 3. Required SAP Communication Scenarios (AC1 & IMPLEMENT)

To support end-to-end customer synchronization, External Billing Document Request (EBDR) ingestion, and invoice status reconciliation, the following three standard SAP Communication Scenarios must be activated in the **Maintain Communication Arrangements** Fiori app:

| Scenario ID | Scenario Name | Primary OData Service(s) | Required Operations | LedgerBridge Usage | Current Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`SAP_COM_0008`** | Business Partner, Customer and Supplier Integration | `API_BUSINESS_PARTNER` (`A_BusinessPartner`, `A_Customer`) | `GET` (Read), `POST` (Create), `PATCH` (Update) | Verify and sync Salesforce Account records to SAP Business Partners prior to billing. | `SIMULATED` *(Blocked: No live S/4HANA tenant)* |
| **`SAP_COM_0176`** | External Billing Document Request Integration | `API_BILLING_DOCUMENT_REQUEST_SRV` (`A_BillingDocumentRequest`) | `POST` (Create), `GET` (Read) | Submit closed-won Salesforce Quote/Order payloads as EBDRs in SAP. | `SIMULATED` *(Blocked: No live S/4HANA tenant)* |
| **`SAP_COM_0120`** | Billing Document Integration | `API_BILLING_DOCUMENT_SRV` (`A_BillingDocument`) | `GET` (Read) | Poll/verify posted SAP Billing Document (Invoice) numbers and accounting status. | `SIMULATED` *(Blocked: No live S/4HANA tenant)* |

### Blocker & Privilege Escalation Matrix (VERIFY)
When running in `LIVE` mode against an SAP S/4HANA Cloud tenant, missing configuration surfaces deterministic HTTP blockers:
* **`401 Unauthorized`:** Invalid `CC_LEDGERBRIDGE` credentials, expired certificate/token, or inactive Communication User.
* **`403 Forbidden` (`No authorization for service ...`):** Communication User exists, but the corresponding Communication Scenario (`SAP_COM_0008`, `SAP_COM_0176`, or `SAP_COM_0120`) is missing read/write authorization objects for Company Code `1710`.
* **`404 Not Found` (`Service ... is not active`):** The OData service ICF node has not been activated via a Communication Arrangement in the target tenant.

---

## 4. Salesforce API Authentication & Scope Model

| Attribute | Value / Specification | Notes |
| :--- | :--- | :--- |
| **Org Classification** | Salesforce Developer Edition / Scratch Org (`API v62.0+`) | Sandbox/Dev environment for Quote-to-Cash event simulation. |
| **Auth Flow** | OAuth 2.0 Client Credentials Flow (`POST /services/oauth2/token`) | Uses a dedicated External Client App / Connected App; avoids tied user session tokens. |
| **Integration User** | `ledgerbridge.integration@dlincolndata.com` | Assigned `API Enabled` permission and minimum object-level access (`Account`, `Opportunity`, `Quote`). |
| **Required OAuth Scopes** | `api` | Scoped strictly to REST API data access. |

---

## 5. Secret Management & Redaction Policy
1. **Zero Hardcoded Secrets:** All credentials (`SF_CLIENT_ID`, `SF_CLIENT_SECRET`, `SAP_COMM_USER`, `SAP_COMM_PASSWORD`, `SAP_SANDBOX_API_KEY`) are injected via environment variables (`.env` locally, encrypted secrets in cloud environments).
2. **Trace Redaction:** Smoke test diagnostics (`GET /api/v1/connectivity/smoke-test`) automatically mask `Authorization` headers, `access_token` payloads, and Basic Auth credentials as `***REDACTED***` before logging or returning HTTP trace responses.