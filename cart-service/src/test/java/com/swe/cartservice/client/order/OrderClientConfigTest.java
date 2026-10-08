package com.swe.cartservice.client.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class OrderClientConfigTest {

    @Test
    @DisplayName("orderRestClient should construct configured RestClient bean")
    void orderRestClient_ShouldConstructRestClientBean() {
        // Arrange
        OrderClientConfig config = new OrderClientConfig();
        String baseUrl = "http://localhost:8081";

        // Act
        RestClient restClient = config.orderRestClient(baseUrl);

        // Assert
        assertThat(restClient).isNotNull();
    }
}
