package com.vjk.sample.ap.order.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record Order(
        Long orderId,
        String customerName,
        String customerEmail,
        List<OrderItem> items,
        BigDecimal totalAmount,
        OrderStatus status,
        Instant createdAt
) {
}
