# LB-004 | Canonical Identity & Versioned API Contract (`v1`)

## 1. Composite External Identity (`AC1`)
$$\text{CanonicalExternalId} = \text{lowercase}(\text{provider}) + \text{":"} + \text{lowercase}(\text{business}) + \text{":"} + \text{lowercase}(\text{entityType}) + \text{":"} + \text{sourceId}$$

| Component | Description | Example Values |
| :--- | :--- | :--- |
| **`provider`** | Source system identifier (lowercase) | `salesforce`, `sap_s4hana` |
| **`business`** | Legal entity / company code or tenant (lowercase) | `1710`, `us_enterprise` |
| **`entityType`** | Canonical domain object type (lowercase) | `order`, `billing_request`, `invoice`, `payment`, `credit_memo` |
| **`sourceId`** | Authoritative native record ID (case-preserved) | `8015e000001AbCd`, `90000481` |

* **Example Canonical ID:** `salesforce:1710:billing_request:8015e000001AbCd`
* **Uniqueness:** Enforced via composite unique index `(provider, business, entity_type, source_id)` and serialized as `canonicalExternalId`.

## 2. Correlation ID, Revision-Based Idempotency & State Machine (`AC2`)
* **`X-Correlation-ID`:** UUIDv4 propagated across all `v1` request/response headers, async events, SAP OData calls, and error envelopes (auto-generated if absent).
* **Revision-Based Idempotency Key:** `CanonicalExternalId + ":rev-" + revision` (e.g., `salesforce:1710:billing_request:8015e000001AbCd:rev-1`).
  * **Same Key + Matching SHA-256 Digest:** Returns `200 OK` (safe idempotent replay).
  * **Same Key + Altered SHA-256 Digest:** Returns `409 Conflict` (`LB_IDEMPOTENCY_CONFLICT`).
  * **New Revision (`rev-2`):** Allowed for legitimate amendments while preserving `CanonicalExternalId`.

### State Machine
`RECEIVED` -> `VALIDATED` -> `DISPATCHING` -> `POSTED` -> `SUPERSEDED` (terminal)
Branches: `RECEIVED`/`VALIDATED` -> `REJECTED` (terminal); `DISPATCHING` <-> `RETRY_PENDING` -> `DEAD_LETTERED` (terminal).

## 3. Canonical Error Codes (`IMPLEMENT`)
* `LB_VALIDATION_MISSING_CURRENCY` (`400`): Missing or non-ISO-4217 3-letter currency.
* `LB_VALIDATION_INVALID_QUANTITY` (`400`): Line item quantity `<= 0` or missing.
* `LB_VALIDATION_INVALID_IDENTITY` (`400`): Missing/malformed identity segment or `revision < 1`.
* `LB_IDEMPOTENCY_CONFLICT` (`409`): Reused idempotency key with altered payload.
* `LB_STATE_TRANSITION_INVALID` (`422`): Illegal lifecycle state transition.
* `LB_UPSTREAM_SAP_ERROR` (`502`): SAP S/4HANA OData dispatch failure.
