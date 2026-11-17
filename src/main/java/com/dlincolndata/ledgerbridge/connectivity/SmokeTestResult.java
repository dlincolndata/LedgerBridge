package com.dlincolndata.ledgerbridge.connectivity;

import java.time.Instant;
import java.util.Map;

public record SmokeTestResult(
        String system,
        String scenario,
        String status,
        int httpStatus,
        String endpoint,
        String blockerDetail,
        Map<String, String> redactedHeaders,
        Object samplePayload,
        Instant timestamp
) {}
