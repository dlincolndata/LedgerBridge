package com.dlincolndata.ledgerbridge.api.v1;
import java.time.Instant;
public record CanonicalErrorV1(String correlationId, CanonicalErrorCode errorCode, String message, String field, Instant timestamp) {}
