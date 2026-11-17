## 1. Composite External Identity (`AC1`)
Every Quote-to-Cash entity is uniquely identified across system boundaries by a deterministic 4-part composite external key:

$$\\text{CanonicalExternalId} = \\text{lowercase}(\\text{provider}) + \\text{":"} + \\text{lowercase}(\\text{business}) + \\text{":"} + \\text{lowercase}(\\text{entityType}) + \\text{":"} + \\text{sourceId}$$

| Component | Description | Example Values |
| :--- | :--- | :--- |
| **`provider`** | Source system identifier (normalized lowercase) | `salesforce`, `sap_s4hana` |
| **`business`** | Legal entity / company code or tenant (normalized lowercase) | `1710`, `us_enterprise` |
| **`entityType`** | Canonical domain object classification (normalized lowercase) | `order`, `billing_request`, `invoice`, `payment`, `credit_memo` |
| **`sourceId`** | Authoritative native record ID in source system (case-preserved) | `8015e000001AbCd`, `90000481` |

* **Example Canonical ID:** `salesforce:1710:billing_request:8015e000001AbCd`
* **Uniqueness Guarantee:** Enforced via composite unique index `(provider, business, entity_type, source_id)` and serialized as `canonicalExternalId` across all `v1` payloads.

---

## 2. Correlation ID, Revision-Based Idempotency & State Machine (`AC2`)

### Correlation Tracing (`X-Correlation-ID`)
* Every `v1` billing request requires/propagates a UUIDv4 `X-Correlation-ID` header (auto-generated at API ingress if omitted) across HTTP responses, worker queues, SAP OData calls, and error envelopes.

### Revision-Based Idempotency Key
$$\\text{IdempotencyKey} = \\text{CanonicalExternalId} + \\text{":rev-"} + \\text{revision}$$

* **Example:** `salesforce:1710:billing_request:8015e000001AbCd:rev-1`
* **Same Key + Matching SHA-256 Digest:** Safe retry; returns `200 OK` with the existing canonical response.
* **Same Key + Altered SHA-256 Digest:** Rejected with `409 Conflict` (`LB_IDEMPOTENCY_CONFLICT`) to prevent silent financial drift.
* **Incremented Revision (`rev-2`):** Permitted for legitimate commercial amendments while preserving `CanonicalExternalId`.

### Lifecycle State Machine
```text
[RECEIVED] ──(schema & currency/qty valid)──> [VALIDATED]
    │                                              │
(validation error)                        (acquire idempotency lock)
    ▼                                              ▼
[REJECTED]                                    [DISPATCHING] ──(SAP 201 Created)──> [POSTED]
                                                   │                                  │
                                         (transient 5xx/429)                   (SAP Credit/Cancel)
                                                   ▼                                  ▼
                                             [RETRY_PENDING]                      [SUPERSEDED]
                                                   │
                                          (max retries exceeded)
                                                   ▼
                                            [DEAD_LETTERED]