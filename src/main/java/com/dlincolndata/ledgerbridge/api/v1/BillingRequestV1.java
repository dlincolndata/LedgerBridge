package com.dlincolndata.ledgerbridge.api.v1;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record BillingRequestV1(
        @NotNull @Valid ExternalIdentityDto identity,
        @NotNull @Min(1) Integer revision,
        @NotBlank(message = "currency is required") @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be 3-letter ISO-4217") String currency,
        @NotEmpty @Valid List<BillingLineItemDto> lines
) {
    public String toIdempotencyKey() { return identity.toCanonicalId() + ":rev-" + revision; }
}
