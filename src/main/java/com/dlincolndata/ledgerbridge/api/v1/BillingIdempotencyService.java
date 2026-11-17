package com.dlincolndata.ledgerbridge.api.v1;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BillingIdempotencyService {
    private final Map<String, Stored> store = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = JsonMapper.builder().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true).build();
    public record Outcome(BillingResponseV1 response, boolean replayed) {}
    private record Stored(String hash, BillingResponseV1 response) {}

    public Outcome submit(String correlationId, BillingRequestV1 req) {
        String key = req.toIdempotencyKey();
        String hash = hash(req);
        Stored existing = store.get(key);
        if (existing != null) {
            if (!existing.hash().equals(hash)) throw new IdempotencyConflictException("Reused idempotency key with altered payload: " + key);
            var r = existing.response();
            return new Outcome(new BillingResponseV1(correlationId, r.canonicalExternalId(), r.idempotencyKey(), r.revision(), r.state(), r.payloadHash()), true);
        }
        BillingState state = BillingState.RECEIVED;
        if (!state.canTransitionTo(BillingState.VALIDATED)) throw new IllegalStateException("Invalid transition");
        state = BillingState.VALIDATED;
        var created = new BillingResponseV1(correlationId, req.identity().toCanonicalId(), key, req.revision(), state, hash);
        store.put(key, new Stored(hash, created));
        return new Outcome(created, false);
    }

    private String hash(BillingRequestV1 req) {
        try {
            byte[] dig = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsString(req).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
