package com.swe.cartservice.repository;

import com.swe.cartservice.model.Cart;
import com.swe.cartservice.model.CartItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisCartRepositoryTest {

    @Mock
    private RedisTemplate<String, Cart> redisTemplate;

    @Mock
    private ValueOperations<String, Cart> valueOperations;

    private RedisCartRepository cartRepository;

    private UUID customerId;
    private Cart sampleCart;

    @BeforeEach
    void setUp() {
        cartRepository = new RedisCartRepository(redisTemplate);
        customerId = UUID.randomUUID();
        sampleCart = new Cart(
                customerId,
                List.of(new CartItem(UUID.randomUUID(), 2, new BigDecimal("29.99"))),
                OffsetDateTime.now()
        );
    }

    @Test
    @DisplayName("findByCustomerId should return Cart when key exists in Redis")
    void findByCustomerId_WhenKeyExists_ShouldReturnCart() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("cart:" + customerId)).thenReturn(sampleCart);

        // Act
        Optional<Cart> result = cartRepository.findByCustomerId(customerId);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(sampleCart);
        assertThat(result.get().customerId()).isEqualTo(customerId);
        assertThat(result.get().items()).hasSize(1);
        verify(valueOperations).get("cart:" + customerId);
    }

    @Test
    @DisplayName("findByCustomerId should return empty Optional when key does not exist in Redis")
    void findByCustomerId_WhenKeyDoesNotExist_ShouldReturnEmptyOptional() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("cart:" + customerId)).thenReturn(null);

        // Act
        Optional<Cart> result = cartRepository.findByCustomerId(customerId);

        // Assert
        assertThat(result).isEmpty();
        verify(valueOperations).get("cart:" + customerId);
    }

    @Test
    @DisplayName("save should store cart with 7 days TTL")
    void save_ShouldStoreCartWithSevenDaysTtl() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Act
        cartRepository.save(sampleCart);

        // Assert
        verify(valueOperations).set("cart:" + customerId, sampleCart, Duration.ofDays(7));
    }

    @Test
    @DisplayName("deleteByCustomerId should delete key from Redis")
    void deleteByCustomerId_ShouldDeleteKey() {
        // Act
        cartRepository.deleteByCustomerId(customerId);

        // Assert
        verify(redisTemplate).delete("cart:" + customerId);
    }
}
