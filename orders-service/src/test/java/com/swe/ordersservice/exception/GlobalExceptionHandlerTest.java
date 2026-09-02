package com.swe.ordersservice.exception;

import com.swe.ordersservice.dto.ErrorResponse;
import com.swe.ordersservice.dto.FieldErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn("/api/v1/orders");
    }

    @Test
    @DisplayName("handleOrderNotFoundException should return 404 Not Found")
    void handleOrderNotFoundException_ShouldReturn404() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        OrderNotFoundException ex = new OrderNotFoundException(orderId);

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleOrderNotFoundException(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().error()).isEqualTo("Not Found");
        assertThat(response.getBody().message()).isEqualTo("Order not found with ID: " + orderId);
        assertThat(response.getBody().path()).isEqualTo("/api/v1/orders");
    }

    @Test
    @DisplayName("handleMethodArgumentNotValidException should return 400 Bad Request with field errors")
    void handleMethodArgumentNotValidException_ShouldReturn400WithFieldErrors() {
        // Arrange
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("orderRequest", "customerId", null, false, null, null, "Customer ID is required");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        MethodParameter parameter = mock(MethodParameter.class);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleMethodArgumentNotValidException(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("Bad Request");
        assertThat(response.getBody().fieldErrors()).hasSize(1);

        FieldErrorDetail errorDetail = response.getBody().fieldErrors().getFirst();
        assertThat(errorDetail.field()).isEqualTo("customerId");
        assertThat(errorDetail.message()).isEqualTo("Customer ID is required");
        assertThat(errorDetail.rejectedValue()).isNull();
    }

    @Test
    @DisplayName("handleMethodArgumentTypeMismatchException should return 400 Bad Request")
    void handleMethodArgumentTypeMismatchException_ShouldReturn400() {
        // Arrange
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("orderId");
        when(ex.getValue()).thenReturn("invalid-uuid");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleMethodArgumentTypeMismatchException(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("Bad Request");
        assertThat(response.getBody().message()).contains("orderId").contains("invalid-uuid");
    }

    @Test
    @DisplayName("handleHttpMessageNotReadableException should return 400 Bad Request")
    void handleHttpMessageNotReadableException_ShouldReturn400() {
        // Arrange
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
        when(ex.getMessage()).thenReturn("Malformed JSON");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleHttpMessageNotReadableException(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("Bad Request");
        assertThat(response.getBody().message()).isEqualTo("Malformed JSON request or invalid data format");
    }

    @Test
    @DisplayName("handleGenericException should return 500 Internal Server Error")
    void handleGenericException_ShouldReturn500() {
        // Arrange
        Exception ex = new RuntimeException("Unexpected DB deadlock");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().error()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().message()).isEqualTo("An unexpected internal error occurred. Please try again later.");
    }
}
