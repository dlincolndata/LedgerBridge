package com.dlincolndata.ledgerbridge.api.v1;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record BillingLineItemDto(
        @NotNull @Min(1) Integer lineNumber,
        @NotBlank String sku,
        @NotNull @DecimalMin("0.0001") BigDecimal quantity,
        @NotNull @DecimalMin("0.00") BigDecimal unitPrice
) {}
