package com.dlincolndata.ledgerbridge.api.v1;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing-requests")
@RequiredArgsConstructor
public class BillingRequestV1Controller {
    private final BillingIdempotencyService service;

    @PostMapping
    public ResponseEntity<BillingResponseV1> submit(@RequestHeader(value = "X-Correlation-ID", required = false) String cid, @Valid @RequestBody BillingRequestV1 req) {
        String trace = (cid == null || cid.isBlank()) ? UUID.randomUUID().toString() : cid.trim();
        var out = service.submit(trace, req);
        return ResponseEntity.status(out.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("X-Correlation-ID", trace).header("X-API-Version", "v1").body(out.response());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CanonicalErrorV1> onValidation(MethodArgumentNotValidException ex, @RequestHeader(value = "X-Correlation-ID", required = false) String cid) {
        String trace = (cid == null || cid.isBlank()) ? UUID.randomUUID().toString() : cid.trim();
        var err = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String field = err != null ? err.getField() : "request";
        String msg = err != null ? err.getDefaultMessage() : "Validation error";
        CanonicalErrorCode code = field.contains("currency") ? CanonicalErrorCode.LB_VALIDATION_MISSING_CURRENCY
                : (field.contains("quantity") || field.contains("lines")) ? CanonicalErrorCode.LB_VALIDATION_INVALID_QUANTITY
                : CanonicalErrorCode.LB_VALIDATION_INVALID_IDENTITY;
        return ResponseEntity.badRequest().header("X-Correlation-ID", trace).body(new CanonicalErrorV1(trace, code, msg, field, Instant.now()));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<CanonicalErrorV1> onConflict(IdempotencyConflictException ex, @RequestHeader(value = "X-Correlation-ID", required = false) String cid) {
        String trace = (cid == null || cid.isBlank()) ? UUID.randomUUID().toString() : cid.trim();
        return ResponseEntity.status(HttpStatus.CONFLICT).header("X-Correlation-ID", trace)
                .body(new CanonicalErrorV1(trace, CanonicalErrorCode.LB_IDEMPOTENCY_CONFLICT, ex.getMessage(), "idempotencyKey", Instant.now()));
    }
}
