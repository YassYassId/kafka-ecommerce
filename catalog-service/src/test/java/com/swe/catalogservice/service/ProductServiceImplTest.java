package com.swe.catalogservice.service;

import com.swe.catalogservice.dto.CreateProductRequest;
import com.swe.catalogservice.dto.ProductResponse;
import com.swe.catalogservice.dto.UpdateProductRequest;
import com.swe.catalogservice.entity.Product;
import com.swe.catalogservice.entity.ProductStatus;
import com.swe.catalogservice.event.PriceChangedEvent;
import com.swe.catalogservice.event.ProductCreatedEvent;
import com.swe.catalogservice.event.ProductRetiredEvent;
import com.swe.catalogservice.event.ProductUpdatedEvent;
import com.swe.catalogservice.exception.DuplicateSkuException;
import com.swe.catalogservice.exception.ProductNotFoundException;
import com.swe.catalogservice.outbox.OutboxService;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OutboxService outboxService;

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

            ArgumentCaptor<UUID> eventIdCaptor = ArgumentCaptor.forClass(UUID.class);
            ArgumentCaptor<UUID> aggregateIdCaptor = ArgumentCaptor.forClass(UUID.class);
            ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<OffsetDateTime> occurredAtCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
            ArgumentCaptor<ProductCreatedEvent> eventCaptor = ArgumentCaptor.forClass(ProductCreatedEvent.class);

            verify(outboxService).saveEvent(
                    eventIdCaptor.capture(),
                    aggregateIdCaptor.capture(),
                    eventTypeCaptor.capture(),
                    occurredAtCaptor.capture(),
                    eventCaptor.capture()
            );

            assertThat(eventIdCaptor.getValue()).isNotNull();
            assertThat(aggregateIdCaptor.getValue()).isEqualTo(generatedId);
            assertThat(eventTypeCaptor.getValue()).isEqualTo("ProductCreated");
            assertThat(occurredAtCaptor.getValue()).isNotNull();

            ProductCreatedEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.eventId()).isEqualTo(eventIdCaptor.getValue());
            assertThat(capturedEvent.productId()).isEqualTo(generatedId);
            assertThat(capturedEvent.sku()).isEqualTo("SKU-ABC-123");
            assertThat(capturedEvent.name()).isEqualTo("Wireless Mouse");
            assertThat(capturedEvent.description()).isEqualTo("Ergonomic wireless mouse");
            assertThat(capturedEvent.category()).isEqualTo("Accessories");
            assertThat(capturedEvent.price()).isEqualByComparingTo("49.99");
            assertThat(capturedEvent.currency()).isEqualTo("USD");
            assertThat(capturedEvent.occurredAt()).isEqualTo(occurredAtCaptor.getValue());
            assertThat(capturedEvent.version()).isEqualTo(1);

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
            verify(outboxService, never()).saveEvent(any(), any(), any(), any(), any());
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

    @Nested
    @DisplayName("updateProduct")
    class UpdateProductTests {

        @Test
        @DisplayName("should update product fields, normalize strings, and publish both ProductUpdated and PriceChanged events")
        void shouldUpdateProductSuccessfullyAndPublishEvents() {
            // Arrange
            UUID productId = UUID.randomUUID();
            OffsetDateTime createdAt = OffsetDateTime.now().minusDays(1);
            OffsetDateTime updatedAt = OffsetDateTime.now();

            Product existingProduct = Product.builder()
                    .id(productId)
                    .sku("SKU-ORIGINAL-01")
                    .name("Old Product Name")
                    .description("Old Description")
                    .category("Old Category")
                    .price(new BigDecimal("19.99"))
                    .currency("USD")
                    .status(ProductStatus.ACTIVE)
                    .createdAt(createdAt)
                    .updatedAt(updatedAt)
                    .build();

            UpdateProductRequest request = new UpdateProductRequest(
                    "  New Product Name  ",
                    "Updated Description",
                    "  Electronics  ",
                    new BigDecimal("49.99"),
                    "  eur  "
            );

            when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));

            // Act
            ProductResponse response = productService.updateProduct(productId, request);

            // Assert
            assertThat(existingProduct.getName()).isEqualTo("New Product Name");
            assertThat(existingProduct.getDescription()).isEqualTo("Updated Description");
            assertThat(existingProduct.getCategory()).isEqualTo("Electronics");
            assertThat(existingProduct.getPrice()).isEqualByComparingTo("49.99");
            assertThat(existingProduct.getCurrency()).isEqualTo("EUR");
            assertThat(existingProduct.getSku()).isEqualTo("SKU-ORIGINAL-01");
            assertThat(existingProduct.getStatus()).isEqualTo(ProductStatus.ACTIVE);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(productId);
            assertThat(response.sku()).isEqualTo("SKU-ORIGINAL-01");
            assertThat(response.name()).isEqualTo("New Product Name");
            assertThat(response.description()).isEqualTo("Updated Description");
            assertThat(response.category()).isEqualTo("Electronics");
            assertThat(response.price()).isEqualByComparingTo("49.99");
            assertThat(response.currency()).isEqualTo("EUR");
            assertThat(response.status()).isEqualTo(ProductStatus.ACTIVE);
            assertThat(response.createdAt()).isEqualTo(createdAt);

            verify(productRepository).findById(productId);

            // Outbox events verification
            ArgumentCaptor<UUID> eventIdCaptor = ArgumentCaptor.forClass(UUID.class);
            ArgumentCaptor<UUID> aggregateIdCaptor = ArgumentCaptor.forClass(UUID.class);
            ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<OffsetDateTime> occurredAtCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
            ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);

            verify(outboxService, times(2)).saveEvent(
                    eventIdCaptor.capture(),
                    aggregateIdCaptor.capture(),
                    eventTypeCaptor.capture(),
                    occurredAtCaptor.capture(),
                    payloadCaptor.capture()
            );

            List<String> eventTypes = eventTypeCaptor.getAllValues();
            assertThat(eventTypes).containsExactly("ProductUpdated", "PriceChanged");

            List<UUID> aggregateIds = aggregateIdCaptor.getAllValues();
            assertThat(aggregateIds).containsOnly(productId);

            List<Object> payloads = payloadCaptor.getAllValues();

            ProductUpdatedEvent updatedEvent = (ProductUpdatedEvent) payloads.get(0);
            assertThat(updatedEvent.eventId()).isEqualTo(eventIdCaptor.getAllValues().get(0));
            assertThat(updatedEvent.productId()).isEqualTo(productId);
            assertThat(updatedEvent.sku()).isEqualTo("SKU-ORIGINAL-01");
            assertThat(updatedEvent.name()).isEqualTo("New Product Name");
            assertThat(updatedEvent.description()).isEqualTo("Updated Description");
            assertThat(updatedEvent.category()).isEqualTo("Electronics");
            assertThat(updatedEvent.currency()).isEqualTo("EUR");
            assertThat(updatedEvent.occurredAt()).isEqualTo(occurredAtCaptor.getAllValues().get(0));
            assertThat(updatedEvent.version()).isEqualTo(1);

            PriceChangedEvent priceEvent = (PriceChangedEvent) payloads.get(1);
            assertThat(priceEvent.eventId()).isEqualTo(eventIdCaptor.getAllValues().get(1));
            assertThat(priceEvent.productId()).isEqualTo(productId);
            assertThat(priceEvent.oldPrice()).isEqualByComparingTo("19.99");
            assertThat(priceEvent.newPrice()).isEqualByComparingTo("49.99");
            assertThat(priceEvent.currency()).isEqualTo("EUR");
            assertThat(priceEvent.occurredAt()).isEqualTo(occurredAtCaptor.getAllValues().get(1));
            assertThat(priceEvent.version()).isEqualTo(1);
        }

        @Test
        @DisplayName("should publish only ProductUpdatedEvent when details change but price remains the same")
        void shouldPublishOnlyProductUpdatedEventWhenOnlyDetailsChange() {
            // Arrange
            UUID productId = UUID.randomUUID();
            Product existingProduct = Product.builder()
                    .id(productId)
                    .sku("SKU-DETAILS-01")
                    .name("Old Name")
                    .description("Old Desc")
                    .category("Old Category")
                    .price(new BigDecimal("25.00"))
                    .currency("USD")
                    .status(ProductStatus.ACTIVE)
                    .build();

            UpdateProductRequest request = new UpdateProductRequest(
                    "New Name",
                    "New Desc",
                    "New Category",
                    new BigDecimal("25.00"),
                    "USD"
            );

            when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));

            // Act
            ProductResponse response = productService.updateProduct(productId, request);

            // Assert
            assertThat(response.name()).isEqualTo("New Name");
            assertThat(response.price()).isEqualByComparingTo("25.00");

            ArgumentCaptor<ProductUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(ProductUpdatedEvent.class);
            verify(outboxService, times(1)).saveEvent(
                    any(UUID.class),
                    eq(productId),
                    eq("ProductUpdated"),
                    any(OffsetDateTime.class),
                    eventCaptor.capture()
            );

            ProductUpdatedEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.productId()).isEqualTo(productId);
            assertThat(capturedEvent.sku()).isEqualTo("SKU-DETAILS-01");
            assertThat(capturedEvent.name()).isEqualTo("New Name");
            assertThat(capturedEvent.description()).isEqualTo("New Desc");
            assertThat(capturedEvent.category()).isEqualTo("New Category");
            assertThat(capturedEvent.currency()).isEqualTo("USD");

            verify(outboxService, never()).saveEvent(any(), any(), eq("PriceChanged"), any(), any());
        }

        @Test
        @DisplayName("should publish only PriceChangedEvent when price changes but details remain the same")
        void shouldPublishOnlyPriceChangedEventWhenOnlyPriceChanges() {
            // Arrange
            UUID productId = UUID.randomUUID();
            Product existingProduct = Product.builder()
                    .id(productId)
                    .sku("SKU-PRICE-01")
                    .name("Same Name")
                    .description("Same Desc")
                    .category("Same Category")
                    .price(new BigDecimal("10.00"))
                    .currency("USD")
                    .status(ProductStatus.ACTIVE)
                    .build();

            UpdateProductRequest request = new UpdateProductRequest(
                    "  Same Name  ",
                    "Same Desc",
                    "  Same Category  ",
                    new BigDecimal("15.50"),
                    "  usd  "
            );

            when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));

            // Act
            ProductResponse response = productService.updateProduct(productId, request);

            // Assert
            assertThat(response.price()).isEqualByComparingTo("15.50");

            ArgumentCaptor<PriceChangedEvent> eventCaptor = ArgumentCaptor.forClass(PriceChangedEvent.class);
            verify(outboxService, times(1)).saveEvent(
                    any(UUID.class),
                    eq(productId),
                    eq("PriceChanged"),
                    any(OffsetDateTime.class),
                    eventCaptor.capture()
            );

            PriceChangedEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.productId()).isEqualTo(productId);
            assertThat(capturedEvent.oldPrice()).isEqualByComparingTo("10.00");
            assertThat(capturedEvent.newPrice()).isEqualByComparingTo("15.50");
            assertThat(capturedEvent.currency()).isEqualTo("USD");

            verify(outboxService, never()).saveEvent(any(), any(), eq("ProductUpdated"), any(), any());
        }

        @Test
        @DisplayName("should not publish any events when no fields change")
        void shouldNotPublishAnyEventsWhenNoFieldsChange() {
            // Arrange
            UUID productId = UUID.randomUUID();
            Product existingProduct = Product.builder()
                    .id(productId)
                    .sku("SKU-NOCHANGE-01")
                    .name("Exact Name")
                    .description("Exact Desc")
                    .category("Exact Category")
                    .price(new BigDecimal("30.00"))
                    .currency("USD")
                    .status(ProductStatus.ACTIVE)
                    .build();

            UpdateProductRequest request = new UpdateProductRequest(
                    "  Exact Name  ",
                    "Exact Desc",
                    "  Exact Category  ",
                    new BigDecimal("30.00"),
                    "  usd  "
            );

            when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));

            // Act
            ProductResponse response = productService.updateProduct(productId, request);

            // Assert
            assertThat(response).isNotNull();
            verify(outboxService, never()).saveEvent(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should throw ProductNotFoundException and not publish events when product does not exist")
        void shouldThrowProductNotFoundExceptionWhenProductNotFound() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            UpdateProductRequest request = new UpdateProductRequest(
                    "New Name",
                    "New Description",
                    "Electronics",
                    new BigDecimal("49.99"),
                    "USD"
            );

            when(productRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> productService.updateProduct(nonExistentId, request))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessage("Product with ID: '" + nonExistentId + "' was not found");

            verify(productRepository).findById(nonExistentId);
            verify(outboxService, never()).saveEvent(any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("retireProduct")
    class RetireProductTests {

        @Test
        @DisplayName("should set product status to RETIRED and publish ProductRetiredEvent when active product exists")
        void shouldRetireActiveProductSuccessfullyAndPublishEvent() {
            // Arrange
            UUID productId = UUID.randomUUID();
            OffsetDateTime createdAt = OffsetDateTime.now().minusDays(2);
            OffsetDateTime updatedAt = OffsetDateTime.now().minusDays(1);

            Product product = Product.builder()
                    .id(productId)
                    .sku("SKU-RETIRE-01")
                    .name("Active Item")
                    .description("Item to retire")
                    .category("Electronics")
                    .price(new BigDecimal("99.99"))
                    .currency("USD")
                    .status(ProductStatus.ACTIVE)
                    .createdAt(createdAt)
                    .updatedAt(updatedAt)
                    .build();

            when(productRepository.findById(productId)).thenReturn(Optional.of(product));

            // Act
            ProductResponse response = productService.retireProduct(productId);

            // Assert
            assertThat(product.getStatus()).isEqualTo(ProductStatus.RETIRED);
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(productId);
            assertThat(response.status()).isEqualTo(ProductStatus.RETIRED);
            assertThat(response.sku()).isEqualTo("SKU-RETIRE-01");
            assertThat(response.name()).isEqualTo("Active Item");

            verify(productRepository).findById(productId);

            ArgumentCaptor<UUID> eventIdCaptor = ArgumentCaptor.forClass(UUID.class);
            ArgumentCaptor<UUID> aggregateIdCaptor = ArgumentCaptor.forClass(UUID.class);
            ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<OffsetDateTime> occurredAtCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
            ArgumentCaptor<ProductRetiredEvent> eventCaptor = ArgumentCaptor.forClass(ProductRetiredEvent.class);

            verify(outboxService).saveEvent(
                    eventIdCaptor.capture(),
                    aggregateIdCaptor.capture(),
                    eventTypeCaptor.capture(),
                    occurredAtCaptor.capture(),
                    eventCaptor.capture()
            );

            assertThat(eventIdCaptor.getValue()).isNotNull();
            assertThat(aggregateIdCaptor.getValue()).isEqualTo(productId);
            assertThat(eventTypeCaptor.getValue()).isEqualTo("ProductRetired");
            assertThat(occurredAtCaptor.getValue()).isNotNull();

            ProductRetiredEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.eventId()).isEqualTo(eventIdCaptor.getValue());
            assertThat(capturedEvent.productId()).isEqualTo(productId);
            assertThat(capturedEvent.sku()).isEqualTo("SKU-RETIRE-01");
            assertThat(capturedEvent.occurredAt()).isEqualTo(occurredAtCaptor.getValue());
            assertThat(capturedEvent.version()).isEqualTo(1);
        }

        @Test
        @DisplayName("should remain RETIRED and not publish event when retiring an already retired product")
        void shouldRemainRetiredWhenAlreadyRetired() {
            // Arrange
            UUID productId = UUID.randomUUID();
            OffsetDateTime now = OffsetDateTime.now();

            Product product = Product.builder()
                    .id(productId)
                    .sku("SKU-RETIRE-02")
                    .name("Already Retired Item")
                    .description("Already retired")
                    .category("Electronics")
                    .price(new BigDecimal("50.00"))
                    .currency("USD")
                    .status(ProductStatus.RETIRED)
                    .createdAt(now.minusDays(5))
                    .updatedAt(now.minusDays(1))
                    .build();

            when(productRepository.findById(productId)).thenReturn(Optional.of(product));

            // Act
            ProductResponse response = productService.retireProduct(productId);

            // Assert
            assertThat(product.getStatus()).isEqualTo(ProductStatus.RETIRED);
            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(ProductStatus.RETIRED);

            verify(productRepository).findById(productId);
            verify(outboxService, never()).saveEvent(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should throw ProductNotFoundException and not publish event when product does not exist")
        void shouldThrowProductNotFoundExceptionWhenProductNotFound() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(productRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> productService.retireProduct(nonExistentId))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessage("Product with ID: '" + nonExistentId + "' was not found");

            verify(productRepository).findById(nonExistentId);
            verify(outboxService, never()).saveEvent(any(), any(), any(), any(), any());
        }
    }
}
