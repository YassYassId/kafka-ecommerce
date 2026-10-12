package com.swe.cartservice.service;

import com.swe.cartservice.client.catalog.CatalogClient;
import com.swe.cartservice.client.order.CreateOrderItemRequest;
import com.swe.cartservice.client.order.CreateOrderRequest;
import com.swe.cartservice.client.order.OrderClient;
import com.swe.cartservice.client.order.OrderResponse;
import com.swe.cartservice.dto.AddCartItemRequest;
import com.swe.cartservice.dto.CatalogProductResponse;
import com.swe.cartservice.dto.CheckoutResponse;
import com.swe.cartservice.dto.ProductStatus;
import com.swe.cartservice.exception.CartItemNotFoundException;
import com.swe.cartservice.exception.CartNotFoundException;
import com.swe.cartservice.exception.CatalogUnavailableException;
import com.swe.cartservice.exception.CheckoutConflictException;
import com.swe.cartservice.exception.EmptyCartException;
import com.swe.cartservice.exception.OrderServiceUnavailableException;
import com.swe.cartservice.exception.ProductNotAvailableException;
import com.swe.cartservice.exception.ProductNotFoundException;
import com.swe.cartservice.metrics.CartMetrics;
import com.swe.cartservice.model.Cart;
import com.swe.cartservice.model.CartItem;
import com.swe.cartservice.model.CheckoutRecord;
import com.swe.cartservice.repository.CartRepository;
import com.swe.cartservice.repository.CheckoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartMetrics cartMetrics;

    @Mock
    private CatalogClient catalogClient;

    @Mock
    private OrderClient orderClient;

    @Mock
    private CheckoutRepository checkoutRepository;

    @InjectMocks
    private CartServiceImpl cartService;

    private UUID customerId;
    private UUID productId1;
    private UUID productId2;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        productId1 = UUID.randomUUID();
        productId2 = UUID.randomUUID();
    }

    @Nested
    @DisplayName("getCart")
    class GetCartTests {

        @Test
        @DisplayName("should return existing cart when repository returns cart")
        void shouldReturnExistingCart() {
            // Arrange
            Cart existingCart = new Cart(
                    customerId,
                    List.of(new CartItem(productId1, 2, new BigDecimal("19.99"))),
                    OffsetDateTime.now()
            );
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(existingCart));

            // Act
            Cart result = cartService.getCart(customerId);

            // Assert
            assertThat(result).isEqualTo(existingCart);
            assertThat(result.items()).hasSize(1);
            verify(cartRepository).findByCustomerId(customerId);
            verifyNoInteractions(cartMetrics);
        }

        @Test
        @DisplayName("should return empty cart when repository returns empty Optional")
        void shouldReturnEmptyCartWhenNotFound() {
            // Arrange
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());

            // Act
            Cart result = cartService.getCart(customerId);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.customerId()).isEqualTo(customerId);
            assertThat(result.items()).isEmpty();
            assertThat(result.updatedAt()).isNotNull();
            verify(cartRepository).findByCustomerId(customerId);
            verifyNoInteractions(cartMetrics);
        }
    }

    @Nested
    @DisplayName("addItem")
    class AddItemTests {

        @Test
        @DisplayName("should add new item to empty cart, save and record metric")
        void shouldAddNewItemToEmptyCart() {
            // Arrange
            CatalogProductResponse product = new CatalogProductResponse(
                    productId1, new BigDecimal("49.99"), "USD", ProductStatus.ACTIVE
            );
            when(catalogClient.getProduct(productId1)).thenReturn(product);
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());
            AddCartItemRequest request = new AddCartItemRequest(productId1, 3);

            // Act
            Cart result = cartService.addItem(customerId, request);

            // Assert
            assertThat(result.customerId()).isEqualTo(customerId);
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).productId()).isEqualTo(productId1);
            assertThat(result.items().get(0).quantity()).isEqualTo(3);
            assertThat(result.items().get(0).price()).isEqualByComparingTo("49.99");

            ArgumentCaptor<Cart> cartCaptor = ArgumentCaptor.forClass(Cart.class);
            verify(cartRepository).save(cartCaptor.capture());
            assertThat(cartCaptor.getValue().items()).hasSize(1);
            assertThat(cartCaptor.getValue().items().get(0).productId()).isEqualTo(productId1);
            verify(cartMetrics).itemAdded();
        }

        @Test
        @DisplayName("should update quantity and price when adding already existing product and record metric")
        void shouldUpdateQuantityWhenItemAlreadyInCart() {
            // Arrange
            CatalogProductResponse product = new CatalogProductResponse(
                    productId1, new BigDecimal("45.00"), "USD", ProductStatus.ACTIVE
            );
            when(catalogClient.getProduct(productId1)).thenReturn(product);
            Cart existingCart = new Cart(
                    customerId,
                    List.of(new CartItem(productId1, 2, new BigDecimal("40.00"))),
                    OffsetDateTime.now().minusHours(1)
            );
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(existingCart));
            AddCartItemRequest request = new AddCartItemRequest(productId1, 3);

            // Act
            Cart result = cartService.addItem(customerId, request);

            // Assert
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).productId()).isEqualTo(productId1);
            assertThat(result.items().get(0).quantity()).isEqualTo(5); // 2 + 3
            assertThat(result.items().get(0).price()).isEqualByComparingTo("45.00");

            verify(cartRepository).save(any(Cart.class));
            verify(cartMetrics).itemAdded();
        }

        @Test
        @DisplayName("should append item when adding distinct product to existing non-empty cart and record metric")
        void shouldAppendItemToExistingNonEmptyCart() {
            // Arrange
            CatalogProductResponse product = new CatalogProductResponse(
                    productId2, new BigDecimal("25.00"), "USD", ProductStatus.ACTIVE
            );
            when(catalogClient.getProduct(productId2)).thenReturn(product);
            Cart existingCart = new Cart(
                    customerId,
                    List.of(new CartItem(productId1, 1, new BigDecimal("10.00"))),
                    OffsetDateTime.now()
            );
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(existingCart));
            AddCartItemRequest request = new AddCartItemRequest(productId2, 4);

            // Act
            Cart result = cartService.addItem(customerId, request);

            // Assert
            assertThat(result.items()).hasSize(2);
            assertThat(result.items()).extracting(CartItem::productId).containsExactlyInAnyOrder(productId1, productId2);
            verify(cartRepository).save(any(Cart.class));
            verify(cartMetrics).itemAdded();
        }

        @Test
        @DisplayName("should throw ProductNotAvailableException when product status is not ACTIVE")
        void shouldThrowProductNotAvailableExceptionWhenNotActive() {
            // Arrange
            CatalogProductResponse product = new CatalogProductResponse(
                    productId1, new BigDecimal("49.99"), "USD", ProductStatus.RETIRED
            );
            when(catalogClient.getProduct(productId1)).thenReturn(product);
            AddCartItemRequest request = new AddCartItemRequest(productId1, 2);

            // Act & Assert
            assertThatThrownBy(() -> cartService.addItem(customerId, request))
                    .isInstanceOf(ProductNotAvailableException.class)
                    .hasMessageContaining(productId1.toString());

            verify(cartRepository, never()).save(any());
            verifyNoInteractions(cartMetrics);
        }

        @Test
        @DisplayName("should propagate ProductNotFoundException when catalog returns not found")
        void shouldPropagateProductNotFoundException() {
            // Arrange
            when(catalogClient.getProduct(productId1))
                    .thenThrow(new ProductNotFoundException(productId1));
            AddCartItemRequest request = new AddCartItemRequest(productId1, 1);

            // Act & Assert
            assertThatThrownBy(() -> cartService.addItem(customerId, request))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining(productId1.toString());

            verify(cartRepository, never()).save(any());
            verifyNoInteractions(cartMetrics);
        }

        @Test
        @DisplayName("should propagate CatalogUnavailableException when catalog service fails")
        void shouldPropagateCatalogUnavailableException() {
            // Arrange
            when(catalogClient.getProduct(productId1))
                    .thenThrow(new CatalogUnavailableException());
            AddCartItemRequest request = new AddCartItemRequest(productId1, 1);

            // Act & Assert
            assertThatThrownBy(() -> cartService.addItem(customerId, request))
                    .isInstanceOf(CatalogUnavailableException.class);

            verify(cartRepository, never()).save(any());
            verifyNoInteractions(cartMetrics);
        }
    }

    @Nested
    @DisplayName("updateItemQuantity")
    class UpdateItemQuantityTests {

        @Test
        @DisplayName("should update item quantity successfully and record metric")
        void shouldUpdateItemQuantity() {
            // Arrange
            Cart existingCart = new Cart(
                    customerId,
                    List.of(
                            new CartItem(productId1, 2, new BigDecimal("15.00")),
                            new CartItem(productId2, 1, new BigDecimal("30.00"))
                    ),
                    OffsetDateTime.now()
            );
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(existingCart));

            // Act
            Cart result = cartService.updateItemQuantity(customerId, productId1, 7);

            // Assert
            assertThat(result.items()).hasSize(2);
            CartItem updatedItem = result.items().stream()
                    .filter(item -> item.productId().equals(productId1))
                    .findFirst()
                    .orElseThrow();
            assertThat(updatedItem.quantity()).isEqualTo(7);
            assertThat(updatedItem.price()).isEqualByComparingTo("15.00");

            verify(cartRepository).save(any(Cart.class));
            verify(cartMetrics).itemUpdated();
        }

        @Test
        @DisplayName("should throw CartNotFoundException when cart does not exist and not record metric")
        void shouldThrowCartNotFoundExceptionWhenCartNotFound() {
            // Arrange
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> cartService.updateItemQuantity(customerId, productId1, 5))
                    .isInstanceOf(CartNotFoundException.class)
                    .hasMessageContaining(customerId.toString());

            verify(cartRepository, never()).save(any());
            verifyNoInteractions(cartMetrics);
        }

        @Test
        @DisplayName("should throw CartItemNotFoundException when item is not in cart and not record metric")
        void shouldThrowCartItemNotFoundExceptionWhenItemNotFound() {
            // Arrange
            Cart existingCart = new Cart(
                    customerId,
                    List.of(new CartItem(productId1, 2, new BigDecimal("15.00"))),
                    OffsetDateTime.now()
            );
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(existingCart));

            // Act & Assert
            assertThatThrownBy(() -> cartService.updateItemQuantity(customerId, productId2, 5))
                    .isInstanceOf(CartItemNotFoundException.class)
                    .hasMessageContaining(productId2.toString());

            verify(cartRepository, never()).save(any());
            verifyNoInteractions(cartMetrics);
        }
    }

    @Nested
    @DisplayName("removeItem")
    class RemoveItemTests {

        @Test
        @DisplayName("should remove item and save cart when other items remain and record metric")
        void shouldRemoveItemWhenOtherItemsRemain() {
            // Arrange
            Cart existingCart = new Cart(
                    customerId,
                    List.of(
                            new CartItem(productId1, 2, new BigDecimal("10.00")),
                            new CartItem(productId2, 1, new BigDecimal("20.00"))
                    ),
                    OffsetDateTime.now()
            );
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(existingCart));

            // Act
            Cart result = cartService.removeItem(customerId, productId1);

            // Assert
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).productId()).isEqualTo(productId2);
            verify(cartRepository).save(any(Cart.class));
            verify(cartRepository, never()).deleteByCustomerId(any());
            verify(cartMetrics).itemRemoved();
        }

        @Test
        @DisplayName("should delete cart and return empty cart when removing the last remaining item and record metric")
        void shouldDeleteCartWhenRemovingLastItem() {
            // Arrange
            Cart existingCart = new Cart(
                    customerId,
                    List.of(new CartItem(productId1, 2, new BigDecimal("10.00"))),
                    OffsetDateTime.now()
            );
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(existingCart));

            // Act
            Cart result = cartService.removeItem(customerId, productId1);

            // Assert
            assertThat(result.items()).isEmpty();
            assertThat(result.customerId()).isEqualTo(customerId);
            verify(cartRepository).deleteByCustomerId(customerId);
            verify(cartRepository, never()).save(any());
            verify(cartMetrics).itemRemoved();
        }

        @Test
        @DisplayName("should throw CartNotFoundException when cart does not exist and not record metric")
        void shouldThrowCartNotFoundExceptionWhenCartNotFound() {
            // Arrange
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> cartService.removeItem(customerId, productId1))
                    .isInstanceOf(CartNotFoundException.class);
            verify(cartRepository, never()).deleteByCustomerId(any());
            verify(cartRepository, never()).save(any());
            verifyNoInteractions(cartMetrics);
        }

        @Test
        @DisplayName("should throw CartItemNotFoundException when item does not exist in cart and not record metric")
        void shouldThrowCartItemNotFoundExceptionWhenItemNotInCart() {
            // Arrange
            Cart existingCart = new Cart(
                    customerId,
                    List.of(new CartItem(productId1, 2, new BigDecimal("10.00"))),
                    OffsetDateTime.now()
            );
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(existingCart));

            // Act & Assert
            assertThatThrownBy(() -> cartService.removeItem(customerId, productId2))
                    .isInstanceOf(CartItemNotFoundException.class);
            verify(cartRepository, never()).deleteByCustomerId(any());
            verify(cartRepository, never()).save(any());
            verifyNoInteractions(cartMetrics);
        }
    }

    @Nested
    @DisplayName("clearCart")
    class ClearCartTests {

        @Test
        @DisplayName("should delete cart from repository by customer ID and record metric")
        void shouldDeleteCartFromRepository() {
            // Act
            cartService.clearCart(customerId);

            // Assert
            verify(cartRepository).deleteByCustomerId(customerId);
            verify(cartMetrics).cartCleared();
        }
    }

    @Nested
    @DisplayName("checkout")
    class CheckoutTests {

        @Test
        @DisplayName("should successfully checkout valid cart, call order client, complete checkout, and return checkout response")
        void shouldCheckoutSuccessfully() {
            // Arrange
            String idempotencyKey = UUID.randomUUID().toString();
            UUID orderId = UUID.randomUUID();
            String rawJson = "{\"customerId\":\"" + customerId + "\"}";

            Cart cart = new Cart(
                    customerId,
                    List.of(
                            new CartItem(productId1, 2, new BigDecimal("19.99")),
                            new CartItem(productId2, 1, new BigDecimal("29.99"))
                    ),
                    OffsetDateTime.now()
            );

            CatalogProductResponse product1 = new CatalogProductResponse(
                    productId1, new BigDecimal("19.99"), "USD", ProductStatus.ACTIVE
            );
            CatalogProductResponse product2 = new CatalogProductResponse(
                    productId2, new BigDecimal("29.99"), "USD", ProductStatus.ACTIVE
            );

            when(checkoutRepository.findByCustomerIdAndKey(customerId, idempotencyKey)).thenReturn(Optional.empty());
            when(cartRepository.findRawCartByCustomerId(customerId)).thenReturn(Optional.of(rawJson));
            when(checkoutRepository.claimCheckout(customerId, idempotencyKey, rawJson)).thenReturn(true);
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
            when(catalogClient.getProduct(productId1)).thenReturn(product1);
            when(catalogClient.getProduct(productId2)).thenReturn(product2);

            OrderResponse orderResponse = new OrderResponse(orderId, "PENDING");
            when(orderClient.createOrder(eq(idempotencyKey), any(CreateOrderRequest.class))).thenReturn(orderResponse);
            when(checkoutRepository.completeCheckout(any(CheckoutRecord.class), eq(rawJson))).thenReturn(true);

            // Act
            CheckoutResponse response = cartService.checkout(customerId, idempotencyKey);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.orderId()).isEqualTo(orderId);
            assertThat(response.status()).isEqualTo("PENDING");

            ArgumentCaptor<CreateOrderRequest> orderRequestCaptor = ArgumentCaptor.forClass(CreateOrderRequest.class);
            verify(orderClient).createOrder(eq(idempotencyKey), orderRequestCaptor.capture());

            CreateOrderRequest capturedRequest = orderRequestCaptor.getValue();
            assertThat(capturedRequest.customerId()).isEqualTo(customerId);
            assertThat(capturedRequest.items()).hasSize(2);
            assertThat(capturedRequest.items())
                    .extracting(CreateOrderItemRequest::productId)
                    .containsExactly(productId1, productId2);
            assertThat(capturedRequest.items())
                    .extracting(CreateOrderItemRequest::quantity)
                    .containsExactly(2, 1);

            ArgumentCaptor<CheckoutRecord> recordCaptor = ArgumentCaptor.forClass(CheckoutRecord.class);
            verify(checkoutRepository).completeCheckout(recordCaptor.capture(), eq(rawJson));
            assertThat(recordCaptor.getValue().customerId()).isEqualTo(customerId);
            assertThat(recordCaptor.getValue().idempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(recordCaptor.getValue().orderId()).isEqualTo(orderId);
            assertThat(recordCaptor.getValue().status()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("should replay completed checkout without claiming or calling catalog/orders")
        void shouldReplayCompletedCheckoutWhenRecordExists() {
            // Arrange
            String idempotencyKey = UUID.randomUUID().toString();
            UUID existingOrderId = UUID.randomUUID();
            CheckoutRecord existingRecord = new CheckoutRecord(customerId, idempotencyKey, existingOrderId, "PENDING");

            when(checkoutRepository.findByCustomerIdAndKey(customerId, idempotencyKey))
                    .thenReturn(Optional.of(existingRecord));

            // Act
            CheckoutResponse response = cartService.checkout(customerId, idempotencyKey);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.orderId()).isEqualTo(existingOrderId);
            assertThat(response.status()).isEqualTo("PENDING");

            verifyNoInteractions(cartRepository);
            verifyNoInteractions(catalogClient);
            verifyNoInteractions(orderClient);
            verify(checkoutRepository, never()).claimCheckout(any(), any(), any());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when idempotency key is null or blank")
        void shouldThrowIllegalArgumentExceptionWhenIdempotencyKeyIsBlank() {
            assertThatThrownBy(() -> cartService.checkout(customerId, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Idempotency-Key must not be blank");

            assertThatThrownBy(() -> cartService.checkout(customerId, "   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Idempotency-Key must not be blank");

            verifyNoInteractions(checkoutRepository);
            verifyNoInteractions(cartRepository);
        }

        @Test
        @DisplayName("should throw CartNotFoundException when raw cart does not exist")
        void shouldThrowCartNotFoundExceptionWhenCartDoesNotExist() {
            // Arrange
            String idempotencyKey = UUID.randomUUID().toString();
            when(checkoutRepository.findByCustomerIdAndKey(customerId, idempotencyKey)).thenReturn(Optional.empty());
            when(cartRepository.findRawCartByCustomerId(customerId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> cartService.checkout(customerId, idempotencyKey))
                    .isInstanceOf(CartNotFoundException.class)
                    .hasMessageContaining(customerId.toString());

            verifyNoInteractions(catalogClient);
            verifyNoInteractions(orderClient);
            verify(checkoutRepository, never()).claimCheckout(any(), any(), any());
        }

        @Test
        @DisplayName("should throw CheckoutConflictException when claim fails")
        void shouldThrowCheckoutConflictExceptionWhenClaimFails() {
            // Arrange
            String idempotencyKey = UUID.randomUUID().toString();
            String rawJson = "{\"customerId\":\"" + customerId + "\"}";

            when(checkoutRepository.findByCustomerIdAndKey(customerId, idempotencyKey)).thenReturn(Optional.empty());
            when(cartRepository.findRawCartByCustomerId(customerId)).thenReturn(Optional.of(rawJson));
            when(checkoutRepository.claimCheckout(customerId, idempotencyKey, rawJson)).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> cartService.checkout(customerId, idempotencyKey))
                    .isInstanceOf(CheckoutConflictException.class);

            verifyNoInteractions(catalogClient);
            verifyNoInteractions(orderClient);
        }

        @Test
        @DisplayName("should throw EmptyCartException and release claim when cart items list is empty")
        void shouldThrowEmptyCartExceptionWhenCartIsEmpty() {
            // Arrange
            String idempotencyKey = UUID.randomUUID().toString();
            String rawJson = "{\"customerId\":\"" + customerId + "\"}";
            Cart emptyCart = new Cart(customerId, List.of(), OffsetDateTime.now());

            when(checkoutRepository.findByCustomerIdAndKey(customerId, idempotencyKey)).thenReturn(Optional.empty());
            when(cartRepository.findRawCartByCustomerId(customerId)).thenReturn(Optional.of(rawJson));
            when(checkoutRepository.claimCheckout(customerId, idempotencyKey, rawJson)).thenReturn(true);
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(emptyCart));

            // Act & Assert
            assertThatThrownBy(() -> cartService.checkout(customerId, idempotencyKey))
                    .isInstanceOf(EmptyCartException.class)
                    .hasMessageContaining(customerId.toString());

            verify(checkoutRepository).releaseClaim(customerId, idempotencyKey);
            verifyNoInteractions(catalogClient);
            verifyNoInteractions(orderClient);
        }

        @Test
        @DisplayName("should throw ProductNotAvailableException and release claim when any product in cart is not ACTIVE")
        void shouldThrowProductNotAvailableExceptionWhenProductNotActive() {
            // Arrange
            String idempotencyKey = UUID.randomUUID().toString();
            String rawJson = "{\"customerId\":\"" + customerId + "\"}";
            Cart cart = new Cart(
                    customerId,
                    List.of(new CartItem(productId1, 1, new BigDecimal("10.00"))),
                    OffsetDateTime.now()
            );
            CatalogProductResponse product = new CatalogProductResponse(
                    productId1, new BigDecimal("10.00"), "USD", ProductStatus.RETIRED
            );

            when(checkoutRepository.findByCustomerIdAndKey(customerId, idempotencyKey)).thenReturn(Optional.empty());
            when(cartRepository.findRawCartByCustomerId(customerId)).thenReturn(Optional.of(rawJson));
            when(checkoutRepository.claimCheckout(customerId, idempotencyKey, rawJson)).thenReturn(true);
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
            when(catalogClient.getProduct(productId1)).thenReturn(product);

            // Act & Assert
            assertThatThrownBy(() -> cartService.checkout(customerId, idempotencyKey))
                    .isInstanceOf(ProductNotAvailableException.class)
                    .hasMessageContaining(productId1.toString());

            verify(checkoutRepository).releaseClaim(customerId, idempotencyKey);
            verifyNoInteractions(orderClient);
        }

        @Test
        @DisplayName("should propagate ProductNotFoundException and release claim when catalog client throws ProductNotFoundException")
        void shouldPropagateProductNotFoundException() {
            // Arrange
            String idempotencyKey = UUID.randomUUID().toString();
            String rawJson = "{\"customerId\":\"" + customerId + "\"}";
            Cart cart = new Cart(
                    customerId,
                    List.of(new CartItem(productId1, 1, new BigDecimal("10.00"))),
                    OffsetDateTime.now()
            );

            when(checkoutRepository.findByCustomerIdAndKey(customerId, idempotencyKey)).thenReturn(Optional.empty());
            when(cartRepository.findRawCartByCustomerId(customerId)).thenReturn(Optional.of(rawJson));
            when(checkoutRepository.claimCheckout(customerId, idempotencyKey, rawJson)).thenReturn(true);
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
            when(catalogClient.getProduct(productId1)).thenThrow(new ProductNotFoundException(productId1));

            // Act & Assert
            assertThatThrownBy(() -> cartService.checkout(customerId, idempotencyKey))
                    .isInstanceOf(ProductNotFoundException.class);

            verify(checkoutRepository).releaseClaim(customerId, idempotencyKey);
            verifyNoInteractions(orderClient);
        }

        @Test
        @DisplayName("should propagate CatalogUnavailableException and release claim when catalog client is unavailable")
        void shouldPropagateCatalogUnavailableException() {
            // Arrange
            String idempotencyKey = UUID.randomUUID().toString();
            String rawJson = "{\"customerId\":\"" + customerId + "\"}";
            Cart cart = new Cart(
                    customerId,
                    List.of(new CartItem(productId1, 1, new BigDecimal("10.00"))),
                    OffsetDateTime.now()
            );

            when(checkoutRepository.findByCustomerIdAndKey(customerId, idempotencyKey)).thenReturn(Optional.empty());
            when(cartRepository.findRawCartByCustomerId(customerId)).thenReturn(Optional.of(rawJson));
            when(checkoutRepository.claimCheckout(customerId, idempotencyKey, rawJson)).thenReturn(true);
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
            when(catalogClient.getProduct(productId1)).thenThrow(new CatalogUnavailableException());

            // Act & Assert
            assertThatThrownBy(() -> cartService.checkout(customerId, idempotencyKey))
                    .isInstanceOf(CatalogUnavailableException.class);

            verify(checkoutRepository).releaseClaim(customerId, idempotencyKey);
            verifyNoInteractions(orderClient);
        }

        @Test
        @DisplayName("should propagate OrderServiceUnavailableException and NOT release claim when order service fails")
        void shouldPropagateOrderServiceUnavailableException() {
            // Arrange
            String idempotencyKey = UUID.randomUUID().toString();
            String rawJson = "{\"customerId\":\"" + customerId + "\"}";
            Cart cart = new Cart(
                    customerId,
                    List.of(new CartItem(productId1, 1, new BigDecimal("10.00"))),
                    OffsetDateTime.now()
            );
            CatalogProductResponse product = new CatalogProductResponse(
                    productId1, new BigDecimal("10.00"), "USD", ProductStatus.ACTIVE
            );

            when(checkoutRepository.findByCustomerIdAndKey(customerId, idempotencyKey)).thenReturn(Optional.empty());
            when(cartRepository.findRawCartByCustomerId(customerId)).thenReturn(Optional.of(rawJson));
            when(checkoutRepository.claimCheckout(customerId, idempotencyKey, rawJson)).thenReturn(true);
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
            when(catalogClient.getProduct(productId1)).thenReturn(product);
            when(orderClient.createOrder(eq(idempotencyKey), any(CreateOrderRequest.class)))
                    .thenThrow(new OrderServiceUnavailableException());

            // Act & Assert
            assertThatThrownBy(() -> cartService.checkout(customerId, idempotencyKey))
                    .isInstanceOf(OrderServiceUnavailableException.class);

            verify(checkoutRepository, never()).releaseClaim(customerId, idempotencyKey);
            verify(checkoutRepository, never()).completeCheckout(any(), any());
        }

        @Test
        @DisplayName("should throw CheckoutConflictException when completeCheckout returns false")
        void shouldThrowCheckoutConflictExceptionWhenCompleteFails() {
            // Arrange
            String idempotencyKey = UUID.randomUUID().toString();
            UUID orderId = UUID.randomUUID();
            String rawJson = "{\"customerId\":\"" + customerId + "\"}";

            Cart cart = new Cart(
                    customerId,
                    List.of(new CartItem(productId1, 1, new BigDecimal("10.00"))),
                    OffsetDateTime.now()
            );
            CatalogProductResponse product = new CatalogProductResponse(
                    productId1, new BigDecimal("10.00"), "USD", ProductStatus.ACTIVE
            );

            when(checkoutRepository.findByCustomerIdAndKey(customerId, idempotencyKey)).thenReturn(Optional.empty());
            when(cartRepository.findRawCartByCustomerId(customerId)).thenReturn(Optional.of(rawJson));
            when(checkoutRepository.claimCheckout(customerId, idempotencyKey, rawJson)).thenReturn(true);
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
            when(catalogClient.getProduct(productId1)).thenReturn(product);

            OrderResponse orderResponse = new OrderResponse(orderId, "PENDING");
            when(orderClient.createOrder(eq(idempotencyKey), any(CreateOrderRequest.class))).thenReturn(orderResponse);
            when(checkoutRepository.completeCheckout(any(CheckoutRecord.class), eq(rawJson))).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> cartService.checkout(customerId, idempotencyKey))
                    .isInstanceOf(CheckoutConflictException.class);
        }
    }
}
