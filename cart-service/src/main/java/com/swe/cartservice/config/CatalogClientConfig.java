package com.swe.cartservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class CatalogClientConfig {
    @Bean
    public RestClient catalogRestClient(@Value("${services.catalog.base-url}") String catalogBaseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(Duration.ofSeconds(3));

        return RestClient.builder()
                .baseUrl(catalogBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
