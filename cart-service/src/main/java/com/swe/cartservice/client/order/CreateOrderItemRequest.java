package com.swe.cartservice.client.order;

import java.util.UUID;

public record CreateOrderItemRequest(
        UUID productId,
        int quantity
) {
}
