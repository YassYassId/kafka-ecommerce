package com.swe.cartservice.client.order;

import com.swe.cartservice.exception.OrderServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OrderClientImplTest {

    private MockRestServiceServer mockServer;
    private OrderClientImpl orderClient;
    private UUID customerId;
    private UUID productId;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        productId = UUID.randomUUID();
        idempotencyKey = UUID.randomUUID().toString();

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        orderClient = new OrderClientImpl(restClient);
    }

    @Test
    @DisplayName("should successfully create order via orders service")
    void shouldCreateOrderSuccessfully() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        String responseJson = """
                {
                    "orderId": "%s",
                    "status": "PENDING"
                }
                """.formatted(orderId);

        CreateOrderRequest request = new CreateOrderRequest(
                customerId,
                List.of(new CreateOrderItemRequest(productId, 2))
        );

        mockServer.expect(requestTo("/api/v1/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", idempotencyKey))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.items[0].productId").value(productId.toString()))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        // Act
        OrderResponse response = orderClient.createOrder(idempotencyKey, request);

        // Assert
        mockServer.verify();
        assertThat(response).isNotNull();
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("should throw OrderServiceUnavailableException when orders service returns 500 Internal Server Error")
    void shouldThrowOrderServiceUnavailableWhenServerError() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest(
                customerId,
                List.of(new CreateOrderItemRequest(productId, 1))
        );

        mockServer.expect(requestTo("/api/v1/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", idempotencyKey))
                .andRespond(withServerError());

        // Act & Assert
        assertThatThrownBy(() -> orderClient.createOrder(idempotencyKey, request))
                .isInstanceOf(OrderServiceUnavailableException.class)
                .hasMessage("Orders service is temporarily unavailable");

        mockServer.verify();
    }

    @Test
    @DisplayName("should throw OrderServiceUnavailableException when orders service returns 503 Service Unavailable")
    void shouldThrowOrderServiceUnavailableWhen503() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest(
                customerId,
                List.of(new CreateOrderItemRequest(productId, 1))
        );

        mockServer.expect(requestTo("/api/v1/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", idempotencyKey))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        // Act & Assert
        assertThatThrownBy(() -> orderClient.createOrder(idempotencyKey, request))
                .isInstanceOf(OrderServiceUnavailableException.class)
                .hasMessage("Orders service is temporarily unavailable");

        mockServer.verify();
    }

    @Test
    @DisplayName("should throw OrderServiceUnavailableException when ResourceAccessException occurs")
    void shouldThrowOrderServiceUnavailableWhenNetworkError() {
        // Arrange
        RestClient mockRestClient = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        when(mockRestClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(any(String.class))).thenThrow(
                new ResourceAccessException("I/O error", new IOException("Connection timed out"))
        );

        OrderClientImpl clientWithMock = new OrderClientImpl(mockRestClient);
        CreateOrderRequest request = new CreateOrderRequest(
                customerId,
                List.of(new CreateOrderItemRequest(productId, 1))
        );

        // Act & Assert
        assertThatThrownBy(() -> clientWithMock.createOrder(idempotencyKey, request))
                .isInstanceOf(OrderServiceUnavailableException.class)
                .hasMessage("Orders service is temporarily unavailable");
    }
}
