package com.swe.cartservice.client.order;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        UUID customerId,
        List<CreateOrderItemRequest> items
) {
}
