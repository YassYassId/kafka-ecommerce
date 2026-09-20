package com.swe.catalogservice.exception;

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
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

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
        when(request.getRequestURI()).thenReturn("/api/v1/products");
    }

    @Test
    @DisplayName("handleDuplicateSkuException should return 409 Conflict with ApiError")
    void handleDuplicateSkuException_ShouldReturn409() {
        // Arrange
        DuplicateSkuException ex = new DuplicateSkuException("SKU-DUP-01");

        // Act
        ResponseEntity<ApiError> response = exceptionHandler.handleDuplicateSkuException(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().error()).isEqualTo("Conflict");
        assertThat(response.getBody().message()).isEqualTo("Product with SKU 'SKU-DUP-01' already exists");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/products");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("handleProductNotFound should return 404 Not Found with ApiError")
    void handleProductNotFound_ShouldReturn404() {
        // Arrange
        UUID productId = UUID.randomUUID();
        ProductNotFoundException ex = new ProductNotFoundException(productId);

        // Act
        ResponseEntity<ApiError> response = exceptionHandler.handleProductNotFound(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().error()).isEqualTo("Not Found");
        assertThat(response.getBody().message()).isEqualTo("Product with ID: '" + productId + "' was not found");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/products");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("handleValidationException should return 400 Bad Request with formatted field errors")
    void handleValidationException_ShouldReturn400() {
        // Arrange
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError1 = new FieldError("createProductRequest", "sku", "must not be blank");
        FieldError fieldError2 = new FieldError("createProductRequest", "price", "must be greater than or equal to 0.00");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));

        MethodParameter parameter = mock(MethodParameter.class);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        // Act
        ResponseEntity<ApiError> response = exceptionHandler.handleValidationException(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("Bad Request");
        assertThat(response.getBody().message()).contains("sku: must not be blank");
        assertThat(response.getBody().message()).contains("price: must be greater than or equal to 0.00");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/products");
        assertThat(response.getBody().timestamp()).isNotNull();
    }
}
