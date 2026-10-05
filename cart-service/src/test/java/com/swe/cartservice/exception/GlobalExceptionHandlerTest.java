package com.swe.cartservice.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("handleCartNotFound should return 404 NOT_FOUND with ErrorResponse")
    void handleCartNotFound_ShouldReturn404() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        CartNotFoundException ex = new CartNotFoundException(customerId);

        // Act
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = exceptionHandler.handleCartNotFound(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().error()).isEqualTo("Not Found");
        assertThat(response.getBody().message()).isEqualTo("Cart not found for customer: " + customerId);
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("handleCartItemNotFound should return 404 NOT_FOUND with ErrorResponse")
    void handleCartItemNotFound_ShouldReturn404() {
        // Arrange
        UUID productId = UUID.randomUUID();
        CartItemNotFoundException ex = new CartItemNotFoundException(productId);

        // Act
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = exceptionHandler.handleCartItemNotFound(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().error()).isEqualTo("Not Found");
        assertThat(response.getBody().message()).isEqualTo("Product not found in cart: " + productId);
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("handleValidation should return 400 BAD_REQUEST with ValidationErrorResponse and field error map")
    void handleValidation_ShouldReturn400WithErrorsMap() {
        // Arrange
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError1 = new FieldError("addCartItemRequest", "productId", "must not be null");
        FieldError fieldError2 = new FieldError("addCartItemRequest", "quantity", "must be greater than 0");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));

        // Act
        ResponseEntity<GlobalExceptionHandler.ValidationErrorResponse> response = exceptionHandler.handleValidation(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("Bad Request");
        assertThat(response.getBody().errors()).containsEntry("productId", "must not be null");
        assertThat(response.getBody().errors()).containsEntry("quantity", "must be greater than 0");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("handleRedisFailure should return 503 SERVICE_UNAVAILABLE with ErrorResponse")
    void handleRedisFailure_ShouldReturn503() {
        // Arrange
        DataAccessException ex = new QueryTimeoutException("Redis connection timed out");

        // Act
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = exceptionHandler.handleRedisFailure(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(503);
        assertThat(response.getBody().error()).isEqualTo("Service Unavailable");
        assertThat(response.getBody().message()).isEqualTo("Cart service storage is temporarily unavailable");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("handleMalformedJson should return 400 BAD_REQUEST with ErrorResponse")
    void handleMalformedJson_ShouldReturn400() {
        // Arrange
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);

        // Act
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = exceptionHandler.handleMalformedJson(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("Bad Request");
        assertThat(response.getBody().message()).isEqualTo("Malformed JSON request");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("handleUnexpectedException should return 500 INTERNAL_SERVER_ERROR with ErrorResponse")
    void handleUnexpectedException_ShouldReturn500() {
        // Arrange
        RuntimeException ex = new RuntimeException("Unexpected runtime failure");

        // Act
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = exceptionHandler.handleUnexpectedException(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().error()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("handleProductNotFound should return 404 NOT_FOUND with ErrorResponse")
    void handleProductNotFound_ShouldReturn404() {
        // Arrange
        UUID productId = UUID.randomUUID();
        ProductNotFoundException ex = new ProductNotFoundException(productId);

        // Act
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = exceptionHandler.handleProductNotFound(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().error()).isEqualTo("Not Found");
        assertThat(response.getBody().message()).isEqualTo("Product not found: " + productId);
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("handleProductNotAvailable should return 409 CONFLICT with ErrorResponse")
    void handleProductNotAvailable_ShouldReturn409() {
        // Arrange
        UUID productId = UUID.randomUUID();
        ProductNotAvailableException ex = new ProductNotAvailableException(productId);

        // Act
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = exceptionHandler.handleProductNotAvailable(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().error()).isEqualTo("Conflict");
        assertThat(response.getBody().message()).isEqualTo("Product is not available: " + productId);
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("handleCatalogUnavailable should return 503 SERVICE_UNAVAILABLE with ErrorResponse")
    void handleCatalogUnavailable_ShouldReturn503() {
        // Arrange
        CatalogUnavailableException ex = new CatalogUnavailableException();

        // Act
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = exceptionHandler.handleCatalogUnavailable(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(503);
        assertThat(response.getBody().error()).isEqualTo("Service Unavailable");
        assertThat(response.getBody().message()).isEqualTo("Catalog service is temporarily unavailable");
        assertThat(response.getBody().timestamp()).isNotNull();
    }
}
