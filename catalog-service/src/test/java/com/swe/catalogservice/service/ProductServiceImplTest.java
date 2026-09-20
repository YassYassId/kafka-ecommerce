package com.swe.catalogservice.service;

import com.swe.catalogservice.dto.CreateProductRequest;
import com.swe.catalogservice.dto.ProductResponse;
import com.swe.catalogservice.entity.Product;
import com.swe.catalogservice.entity.ProductStatus;
import com.swe.catalogservice.exception.DuplicateSkuException;
import com.swe.catalogservice.exception.ProductNotFoundException;
import com.swe.catalogservice.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    @Nested
    @DisplayName("createProduct")
    class CreateProductTests {

        @Test
        @DisplayName("should create, normalize, and save product successfully")
        void shouldCreateProductSuccessfully() {
            // Arrange
            CreateProductRequest request = new CreateProductRequest(
                    "  sku-abc-123  ",
                    "  Wireless Mouse  ",
                    "Ergonomic wireless mouse",
                    "  Accessories  ",
                    new BigDecimal("49.99"),
                    "  usd  "
            );

            UUID generatedId = UUID.randomUUID();
            OffsetDateTime now = OffsetDateTime.now();

            when(productRepository.existsBySku("SKU-ABC-123")).thenReturn(false);
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
                Product productToSave = invocation.getArgument(0);
                productToSave.setId(generatedId);
                productToSave.setCreatedAt(now);
                productToSave.setUpdatedAt(now);
                return productToSave;
            });

            // Act
            ProductResponse response = productService.createProduct(request);

            // Assert
            ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
            verify(productRepository).save(productCaptor.capture());

            Product capturedProduct = productCaptor.getValue();
            assertThat(capturedProduct.getSku()).isEqualTo("SKU-ABC-123");
            assertThat(capturedProduct.getName()).isEqualTo("Wireless Mouse");
            assertThat(capturedProduct.getDescription()).isEqualTo("Ergonomic wireless mouse");
            assertThat(capturedProduct.getCategory()).isEqualTo("Accessories");
            assertThat(capturedProduct.getPrice()).isEqualByComparingTo("49.99");
            assertThat(capturedProduct.getCurrency()).isEqualTo("USD");
            assertThat(capturedProduct.getStatus()).isEqualTo(ProductStatus.ACTIVE);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(generatedId);
            assertThat(response.sku()).isEqualTo("SKU-ABC-123");
            assertThat(response.name()).isEqualTo("Wireless Mouse");
            assertThat(response.description()).isEqualTo("Ergonomic wireless mouse");
            assertThat(response.category()).isEqualTo("Accessories");
            assertThat(response.price()).isEqualByComparingTo("49.99");
            assertThat(response.currency()).isEqualTo("USD");
            assertThat(response.status()).isEqualTo(ProductStatus.ACTIVE);
            assertThat(response.createdAt()).isEqualTo(now);
            assertThat(response.updatedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("should throw DuplicateSkuException when SKU already exists")
        void shouldThrowDuplicateSkuExceptionWhenSkuExists() {
            // Arrange
            CreateProductRequest request = new CreateProductRequest(
                    "sku-dup-123",
                    "Keyboard",
                    "Mechanical Keyboard",
                    "Accessories",
                    new BigDecimal("99.99"),
                    "USD"
            );

            when(productRepository.existsBySku("SKU-DUP-123")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> productService.createProduct(request))
                    .isInstanceOf(DuplicateSkuException.class)
                    .hasMessage("Product with SKU 'SKU-DUP-123' already exists");

            verify(productRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getProduct")
    class GetProductTests {

        @Test
        @DisplayName("should return ProductResponse when product exists")
        void shouldReturnProductResponseWhenProductFound() {
            // Arrange
            UUID productId = UUID.randomUUID();
            OffsetDateTime now = OffsetDateTime.now();

            Product product = Product.builder()
                    .id(productId)
                    .sku("SKU-MONITOR-01")
                    .name("4K Monitor")
                    .description("32-inch 4K IPS display")
                    .category("Monitors")
                    .price(new BigDecimal("399.99"))
                    .currency("USD")
                    .status(ProductStatus.ACTIVE)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            when(productRepository.findById(productId)).thenReturn(Optional.of(product));

            // Act
            ProductResponse response = productService.getProduct(productId);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(productId);
            assertThat(response.sku()).isEqualTo("SKU-MONITOR-01");
            assertThat(response.name()).isEqualTo("4K Monitor");
            assertThat(response.description()).isEqualTo("32-inch 4K IPS display");
            assertThat(response.category()).isEqualTo("Monitors");
            assertThat(response.price()).isEqualByComparingTo("399.99");
            assertThat(response.currency()).isEqualTo("USD");
            assertThat(response.status()).isEqualTo(ProductStatus.ACTIVE);
            assertThat(response.createdAt()).isEqualTo(now);
            assertThat(response.updatedAt()).isEqualTo(now);

            verify(productRepository).findById(productId);
        }

        @Test
        @DisplayName("should throw ProductNotFoundException when product does not exist")
        void shouldThrowProductNotFoundExceptionWhenProductNotFound() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(productRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> productService.getProduct(nonExistentId))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessage("Product with ID: '" + nonExistentId + "' was not found");

            verify(productRepository).findById(nonExistentId);
        }
    }

    @Nested
    @DisplayName("getProducts")
    class GetProductsTests {

        @Test
        @DisplayName("should normalize filter parameters and map products to responses")
        void shouldNormalizeFiltersAndReturnPagedResponses() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            UUID productId = UUID.randomUUID();
            OffsetDateTime now = OffsetDateTime.now();

            Product product = Product.builder()
                    .id(productId)
                    .sku("SKU-MOUSE-01")
                    .name("Wireless Mouse")
                    .description("Ergonomic mouse")
                    .category("Peripherals")
                    .price(new BigDecimal("29.99"))
                    .currency("USD")
                    .status(ProductStatus.ACTIVE)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            Page<Product> productPage = new PageImpl<>(List.of(product), pageable, 1);

            when(productRepository.findAllFiltered(
                    eq("%mouse%"),
                    eq("Peripherals"),
                    eq(ProductStatus.ACTIVE),
                    eq(pageable)
            )).thenReturn(productPage);

            // Act
            Page<ProductResponse> result = productService.getProducts(
                    "  Mouse  ",
                    "  Peripherals  ",
                    ProductStatus.ACTIVE,
                    pageable
            );

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent()).hasSize(1);

            ProductResponse response = result.getContent().getFirst();
            assertThat(response.id()).isEqualTo(productId);
            assertThat(response.sku()).isEqualTo("SKU-MOUSE-01");
            assertThat(response.name()).isEqualTo("Wireless Mouse");
            assertThat(response.category()).isEqualTo("Peripherals");
            assertThat(response.price()).isEqualByComparingTo("29.99");
            assertThat(response.currency()).isEqualTo("USD");
            assertThat(response.status()).isEqualTo(ProductStatus.ACTIVE);

            verify(productRepository).findAllFiltered("%mouse%", "Peripherals", ProductStatus.ACTIVE, pageable);
        }

        @Test
        @DisplayName("should pass null filters when name and category are null or blank")
        void shouldPassNullWhenFiltersAreNullOrBlank() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 20);
            Page<Product> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(productRepository.findAllFiltered(
                    isNull(),
                    isNull(),
                    isNull(),
                    eq(pageable)
            )).thenReturn(emptyPage);

            // Act
            Page<ProductResponse> result = productService.getProducts("   ", "", null, pageable);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
            verify(productRepository).findAllFiltered(null, null, null, pageable);
        }
    }
}
