package com.dlincolndata.ledgerbridge.api.v1;
import jakarta.validation.constraints.*;

public record ExternalIdentityDto(
        @NotBlank @Pattern(regexp = "^[^:]+$") String provider,
        @NotBlank @Pattern(regexp = "^[^:]+$") String business,
        @NotBlank @Pattern(regexp = "^[^:]+$") String entityType,
        @NotBlank @Pattern(regexp = "^[^:]+$") String sourceId
) {
    public String toCanonicalId() {
        return String.format("%s:%s:%s:%s", provider.trim().toLowerCase(), business.trim().toLowerCase(), entityType.trim().toLowerCase(), sourceId.trim());
    }
}
