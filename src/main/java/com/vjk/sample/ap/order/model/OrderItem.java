package com.vjk.sample.ap.order.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrderItem(
        @NotBlank String productId,
        @NotBlank String productName,
        @Min(1) int quantity,
        @NotNull @DecimalMin("0.00") BigDecimal unitPrice
) {
    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
