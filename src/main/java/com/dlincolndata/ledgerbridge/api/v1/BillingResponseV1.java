package com.dlincolndata.ledgerbridge.api.v1;
public record BillingResponseV1(String correlationId, String canonicalExternalId, String idempotencyKey, int revision, BillingState state, String payloadHash) {}
