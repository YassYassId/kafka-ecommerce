package com.swe.cartservice.client.order;

import com.swe.cartservice.exception.OrderServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class OrderClientImpl implements OrderClient {

    private final RestClient orderRestClient;

    @Override
    public OrderResponse createOrder(String idempotencyKey, CreateOrderRequest request) {

        try {
            return orderRestClient.post()
                    .uri("/api/v1/orders")
                    .header("Idempotency-Key", idempotencyKey)
                    .body(request)
                    .retrieve()
                    .body(OrderResponse.class);

        } catch (HttpServerErrorException | ResourceAccessException ex) {
            throw new OrderServiceUnavailableException(ex);
        }
    }

    @Override
    public OrderReconciliationResult reconcileCheckout(String idempotencyKey, CreateOrderRequest request) {

        try {
            OrderResponse response = orderRestClient.post()
                    .uri("/api/v1/orders/checkout-reconciliation")
                    .header("Idempotency-Key", idempotencyKey)
                    .body(request)
                    .retrieve()
                    .body(OrderResponse.class);

            if (response == null) {
                throw new IllegalStateException("Orders Service returned an empty reconciliation response");
            }

            return OrderReconciliationResult.found(response);

        } catch (HttpClientErrorException.NotFound ex) {

            return OrderReconciliationResult.notFound();

        } catch (HttpServerErrorException | ResourceAccessException ex) {

            throw new OrderServiceUnavailableException(ex);
        }
    }
}