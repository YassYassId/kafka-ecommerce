package com.swe.cartservice.service;

import com.swe.cartservice.dto.AddCartItemRequest;
import com.swe.cartservice.exception.CartItemNotFoundException;
import com.swe.cartservice.exception.CartNotFoundException;
import com.swe.cartservice.model.Cart;
import com.swe.cartservice.model.CartItem;
import com.swe.cartservice.repository.CartRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartRepository cartRepository;

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
        }
    }

    @Nested
    @DisplayName("addItem")
    class AddItemTests {

        @Test
        @DisplayName("should add new item to empty cart and save")
        void shouldAddNewItemToEmptyCart() {
            // Arrange
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());
            AddCartItemRequest request = new AddCartItemRequest(productId1, 3, new BigDecimal("49.99"));

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
        }

        @Test
        @DisplayName("should update quantity and price when adding already existing product")
        void shouldUpdateQuantityWhenItemAlreadyInCart() {
            // Arrange
            Cart existingCart = new Cart(
                    customerId,
                    List.of(new CartItem(productId1, 2, new BigDecimal("40.00"))),
                    OffsetDateTime.now().minusHours(1)
            );
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(existingCart));
            AddCartItemRequest request = new AddCartItemRequest(productId1, 3, new BigDecimal("45.00"));

            // Act
            Cart result = cartService.addItem(customerId, request);

            // Assert
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).productId()).isEqualTo(productId1);
            assertThat(result.items().get(0).quantity()).isEqualTo(5); // 2 + 3
            assertThat(result.items().get(0).price()).isEqualByComparingTo("45.00");

            verify(cartRepository).save(any(Cart.class));
        }

        @Test
        @DisplayName("should append item when adding distinct product to existing non-empty cart")
        void shouldAppendItemToExistingNonEmptyCart() {
            // Arrange
            Cart existingCart = new Cart(
                    customerId,
                    List.of(new CartItem(productId1, 1, new BigDecimal("10.00"))),
                    OffsetDateTime.now()
            );
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(existingCart));
            AddCartItemRequest request = new AddCartItemRequest(productId2, 4, new BigDecimal("25.00"));

            // Act
            Cart result = cartService.addItem(customerId, request);

            // Assert
            assertThat(result.items()).hasSize(2);
            assertThat(result.items()).extracting(CartItem::productId).containsExactlyInAnyOrder(productId1, productId2);
            verify(cartRepository).save(any(Cart.class));
        }
    }

    @Nested
    @DisplayName("updateItemQuantity")
    class UpdateItemQuantityTests {

        @Test
        @DisplayName("should update item quantity successfully")
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
        }

        @Test
        @DisplayName("should throw CartNotFoundException when cart does not exist")
        void shouldThrowCartNotFoundExceptionWhenCartNotFound() {
            // Arrange
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> cartService.updateItemQuantity(customerId, productId1, 5))
                    .isInstanceOf(CartNotFoundException.class)
                    .hasMessageContaining(customerId.toString());

            verify(cartRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw CartItemNotFoundException when item is not in cart")
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
        }
    }

    @Nested
    @DisplayName("removeItem")
    class RemoveItemTests {

        @Test
        @DisplayName("should remove item and save cart when other items remain")
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
        }

        @Test
        @DisplayName("should delete cart and return empty cart when removing the last remaining item")
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
        }

        @Test
        @DisplayName("should throw CartNotFoundException when cart does not exist")
        void shouldThrowCartNotFoundExceptionWhenCartNotFound() {
            // Arrange
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> cartService.removeItem(customerId, productId1))
                    .isInstanceOf(CartNotFoundException.class);
            verify(cartRepository, never()).deleteByCustomerId(any());
            verify(cartRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw CartItemNotFoundException when item does not exist in cart")
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
        }
    }

    @Nested
    @DisplayName("clearCart")
    class ClearCartTests {

        @Test
        @DisplayName("should delete cart from repository by customer ID")
        void shouldDeleteCartFromRepository() {
            // Act
            cartService.clearCart(customerId);

            // Assert
            verify(cartRepository).deleteByCustomerId(customerId);
        }
    }
}
