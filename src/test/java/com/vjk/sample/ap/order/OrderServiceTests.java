package com.vjk.sample.ap.order;

import com.vjk.sample.ap.order.model.CreateOrderRequest;
import com.vjk.sample.ap.order.model.Order;
import com.vjk.sample.ap.order.model.OrderItem;
import com.vjk.sample.ap.order.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class OrderServiceTests {

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService();
    }

    @Test
    void constructorSeedsOrder1001() {
        Order seeded = orderService.getOrder(1001L);

        assertThat(seeded.customerName()).isEqualTo("John Doe");
        assertThat(seeded.customerEmail()).isEqualTo("john.doe@example.com");
        assertThat(seeded.items()).hasSize(2);
        assertThat(seeded.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(seeded.totalAmount()).isEqualByComparingTo("140.99");
    }

    @Test
    void createOrderAssignsIncrementingIds() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Jane Smith",
                "jane.smith@example.com",
                List.of(new OrderItem("P-300", "USB-C Hub", 3, new BigDecimal("10.00")))
        );

        Order first = orderService.createOrder(request);
        Order second = orderService.createOrder(request);

        assertThat(first.orderId()).isGreaterThan(1001L);
        assertThat(second.orderId()).isEqualTo(first.orderId() + 1);
    }

    @Test
    void createOrderCalculatesTotalAndStatus() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Alice",
                "alice@example.com",
                List.of(
                        new OrderItem("P-100", "Widget", 2, new BigDecimal("25.50")),
                        new OrderItem("P-200", "Gadget", 1, new BigDecimal("89.99"))
                )
        );

        Order order = orderService.createOrder(request);

        // 2 * 25.50 + 1 * 89.99 = 140.99
        assertThat(order.totalAmount()).isEqualByComparingTo("140.99");
        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.createdAt()).isNotNull();
    }

    @Test
    void createOrderPersistsForRetrieval() {
        Order created = orderService.createOrder(orderService.sampleRequest());

        Order fetched = orderService.getOrder(created.orderId());

        assertThat(fetched).isSameAs(created);
    }

    @Test
    void getOrderThrowsForUnknownId() {
        assertThatThrownBy(() -> orderService.getOrder(999999L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("999999");
    }

    @Test
    void getAllOrdersReturnsSortedById() {
        orderService.createOrder(orderService.sampleRequest());
        orderService.createOrder(orderService.sampleRequest());

        List<Order> all = orderService.getAllOrders();

        assertThat(all).hasSize(3);
        assertThat(all).extracting(Order::orderId)
                .isSorted()
                .startsWith(1001L);
    }

    @Test
    void sampleOrderHasFixedIdAndTimestamp() {
        Order sample = orderService.sampleOrder();

        assertThat(sample.orderId()).isEqualTo(1001L);
        assertThat(sample.createdAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
        assertThat(sample.totalAmount()).isEqualByComparingTo("140.99");
    }

    @Test
    void sampleOrderDoesNotPersistToStore() {
        int before = orderService.getAllOrders().size();

        orderService.sampleOrder();

        assertThat(orderService.getAllOrders()).hasSize(before);
    }
}
