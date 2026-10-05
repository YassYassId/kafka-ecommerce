package com.swe.cartservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogClientConfigTest {

    @Test
    @DisplayName("catalogRestClient should construct configured RestClient bean")
    void catalogRestClient_ShouldConstructRestClientBean() {
        // Arrange
        CatalogClientConfig config = new CatalogClientConfig();
        String baseUrl = "http://localhost:8084";

        // Act
        RestClient restClient = config.catalogRestClient(baseUrl);

        // Assert
        assertThat(restClient).isNotNull();
    }
}
