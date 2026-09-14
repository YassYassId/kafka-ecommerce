package com.swe.ordersservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private CorrelationIdFilter correlationIdFilter;

    @BeforeEach
    void setUp() {
        correlationIdFilter = new CorrelationIdFilter();
    }

    @Test
    @DisplayName("should use existing X-Correlation-Id from request header, set MDC and response header, and clean MDC after filter chain")
    void doFilterInternal_WhenHeaderPresent_ShouldUseHeaderAndCleanMdc() throws ServletException, IOException {
        // Arrange
        String existingCorrelationId = "custom-cid-" + UUID.randomUUID();
        when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn(existingCorrelationId);

        AtomicReference<String> mdcValueDuringChain = new AtomicReference<>();
        doAnswer(inv -> {
            mdcValueDuringChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            return null;
        }).when(filterChain).doFilter(request, response);

        // Act
        correlationIdFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertThat(mdcValueDuringChain.get()).isEqualTo(existingCorrelationId);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();

        verify(response).setHeader(CorrelationIdFilter.HEADER_NAME, existingCorrelationId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("should generate new correlation ID when X-Correlation-Id is null, set MDC and response header, and clean MDC")
    void doFilterInternal_WhenHeaderIsNull_ShouldGenerateNewIdAndCleanMdc() throws ServletException, IOException {
        // Arrange
        when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn(null);

        AtomicReference<String> mdcValueDuringChain = new AtomicReference<>();
        doAnswer(inv -> {
            mdcValueDuringChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            return null;
        }).when(filterChain).doFilter(request, response);

        // Act
        correlationIdFilter.doFilterInternal(request, response, filterChain);

        // Assert
        String capturedId = mdcValueDuringChain.get();
        assertThat(capturedId).isNotBlank();
        assertThat(UUID.fromString(capturedId)).isNotNull();
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();

        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq(CorrelationIdFilter.HEADER_NAME), headerCaptor.capture());
        assertThat(headerCaptor.getValue()).isEqualTo(capturedId);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("should generate new correlation ID when X-Correlation-Id is empty string")
    void doFilterInternal_WhenHeaderIsEmpty_ShouldGenerateNewId() throws ServletException, IOException {
        // Arrange
        when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn("");

        AtomicReference<String> mdcValueDuringChain = new AtomicReference<>();
        doAnswer(inv -> {
            mdcValueDuringChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            return null;
        }).when(filterChain).doFilter(request, response);

        // Act
        correlationIdFilter.doFilterInternal(request, response, filterChain);

        // Assert
        String capturedId = mdcValueDuringChain.get();
        assertThat(capturedId).isNotBlank();
        assertThat(UUID.fromString(capturedId)).isNotNull();
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();

        verify(response).setHeader(CorrelationIdFilter.HEADER_NAME, capturedId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("should clean MDC even when filter chain throws an exception")
    void doFilterInternal_WhenFilterChainThrows_ShouldEnsureMdcCleaned() throws ServletException, IOException {
        // Arrange
        String correlationId = UUID.randomUUID().toString();
        when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn(correlationId);

        doThrow(new RuntimeException("Downstream filter exception"))
                .when(filterChain).doFilter(request, response);

        // Act & Assert
        assertThatThrownBy(() -> correlationIdFilter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Downstream filter exception");

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
        verify(response).setHeader(CorrelationIdFilter.HEADER_NAME, correlationId);
    }
}
