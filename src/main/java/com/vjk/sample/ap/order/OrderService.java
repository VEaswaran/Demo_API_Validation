package com.vjk.sample.ap.order;

import com.vjk.sample.ap.order.model.CreateOrderRequest;
import com.vjk.sample.ap.order.model.Order;
import com.vjk.sample.ap.order.model.OrderItem;
import com.vjk.sample.ap.order.model.OrderStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OrderService {

    private final Map<Long, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1000);

    public OrderService() {
        createOrder(sampleRequest());
    }

    public Order createOrder(CreateOrderRequest request) {
        long id = idSequence.incrementAndGet();
        List<OrderItem> items = List.copyOf(request.items());
        Order order = new Order(
                id,
                request.customerName(),
                request.customerEmail(),
                items,
                calculateTotal(items),
                OrderStatus.CREATED,
                Instant.now()
        );
        orders.put(id, order);
        return order;
    }

    public Order getOrder(Long orderId) {
        Order order = orders.get(orderId);
        if (order == null) {
            throw new OrderNotFoundException(orderId);
        }
        return order;
    }

    public List<Order> getAllOrders() {
        List<Order> result = new ArrayList<>(orders.values());
        result.sort(Comparator.comparing(Order::orderId));
        return result;
    }

    public Order sampleOrder() {
        CreateOrderRequest request = sampleRequest();
        return new Order(
                1001L,
                request.customerName(),
                request.customerEmail(),
                request.items(),
                calculateTotal(request.items()),
                OrderStatus.CREATED,
                Instant.parse("2026-01-01T10:00:00Z")
        );
    }

    public CreateOrderRequest sampleRequest() {
        return new CreateOrderRequest(
                "John Doe",
                "john.doe@example.com",
                List.of(
                        new OrderItem("P-100", "Wireless Mouse", 2, new BigDecimal("25.50")),
                        new OrderItem("P-200", "Mechanical Keyboard", 1, new BigDecimal("89.99"))
                )
        );
    }

    private BigDecimal calculateTotal(List<OrderItem> items) {
        return items.stream()
                .map(OrderItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
