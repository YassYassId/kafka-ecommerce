package com.swe.cartservice.repository;

import com.swe.cartservice.model.Cart;
import com.swe.cartservice.model.CartItem;
import com.swe.cartservice.model.CartMutationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisCartRepositoryTest {

    @Mock
    private RedisTemplate<String, Cart> redisTemplate;

    @Mock
    private ValueOperations<String, Cart> valueOperations;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> stringValueOperations;

    @Mock
    private DefaultRedisScript<Long> mutateCartScript;

    @Mock
    private DefaultRedisScript<Long> deleteCartScript;

    @Mock
    private RedisSerializer<Cart> redisSerializer;

    private RedisCartRepository cartRepository;

    private UUID customerId;
    private Cart sampleCart;

    @BeforeEach
    void setUp() {
        cartRepository = new RedisCartRepository(redisTemplate, stringRedisTemplate, mutateCartScript, deleteCartScript);
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
    @DisplayName("findRawCartByCustomerId should return JSON string when key exists")
    void findRawCartByCustomerId_WhenKeyExists_ShouldReturnRawJson() {
        // Arrange
        String rawJson = "{\"customerId\":\"" + customerId + "\",\"items\":[]}";
        when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
        when(stringValueOperations.get("cart:" + customerId)).thenReturn(rawJson);

        // Act
        Optional<String> result = cartRepository.findRawCartByCustomerId(customerId);

        // Assert
        assertThat(result).contains(rawJson);
        verify(stringValueOperations).get("cart:" + customerId);
    }

    @Test
    @DisplayName("findRawCartByCustomerId should return empty Optional when key does not exist")
    void findRawCartByCustomerId_WhenKeyDoesNotExist_ShouldReturnEmptyOptional() {
        // Arrange
        when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
        when(stringValueOperations.get("cart:" + customerId)).thenReturn(null);

        // Act
        Optional<String> result = cartRepository.findRawCartByCustomerId(customerId);

        // Assert
        assertThat(result).isEmpty();
        verify(stringValueOperations).get("cart:" + customerId);
    }

    @Test
    @DisplayName("saveIfUnchanged should return SUCCESS when Redis script returns 1")
    void saveIfUnchanged_WhenScriptReturns1_ShouldReturnSuccess() {
        // Arrange
        String expectedCartJson = "";
        String updatedCartJson = "{\"customerId\":\"" + customerId + "\"}";
        byte[] serializedBytes = updatedCartJson.getBytes(StandardCharsets.UTF_8);

        doReturn(redisSerializer).when(redisTemplate).getValueSerializer();
        when(redisSerializer.serialize(sampleCart)).thenReturn(serializedBytes);
        when(stringRedisTemplate.execute(
                eq(mutateCartScript),
                eq(List.of("cart:" + customerId, "checkout-claim:" + customerId)),
                eq(expectedCartJson),
                eq(updatedCartJson),
                eq("604800")
        )).thenReturn(1L);

        // Act
        CartMutationResult result = cartRepository.saveIfUnchanged(sampleCart, expectedCartJson);

        // Assert
        assertThat(result).isEqualTo(CartMutationResult.SUCCESS);
    }

    @Test
    @DisplayName("saveIfUnchanged should return CHECKOUT_IN_PROGRESS when Redis script returns 0")
    void saveIfUnchanged_WhenScriptReturns0_ShouldReturnCheckoutInProgress() {
        // Arrange
        String expectedCartJson = "{\"customerId\":\"" + customerId + "\"}";
        String updatedCartJson = "{\"customerId\":\"" + customerId + "\",\"items\":[]}";
        byte[] serializedBytes = updatedCartJson.getBytes(StandardCharsets.UTF_8);

        doReturn(redisSerializer).when(redisTemplate).getValueSerializer();
        when(redisSerializer.serialize(sampleCart)).thenReturn(serializedBytes);
        when(stringRedisTemplate.execute(
                eq(mutateCartScript),
                eq(List.of("cart:" + customerId, "checkout-claim:" + customerId)),
                eq(expectedCartJson),
                eq(updatedCartJson),
                eq("604800")
        )).thenReturn(0L);

        // Act
        CartMutationResult result = cartRepository.saveIfUnchanged(sampleCart, expectedCartJson);

        // Assert
        assertThat(result).isEqualTo(CartMutationResult.CHECKOUT_IN_PROGRESS);
    }

    @Test
    @DisplayName("saveIfUnchanged should return STALE_CART when Redis script returns 2")
    void saveIfUnchanged_WhenScriptReturns2_ShouldReturnStaleCart() {
        // Arrange
        String expectedCartJson = "{\"customerId\":\"" + customerId + "\"}";
        String updatedCartJson = "{\"customerId\":\"" + customerId + "\",\"items\":[]}";
        byte[] serializedBytes = updatedCartJson.getBytes(StandardCharsets.UTF_8);

        doReturn(redisSerializer).when(redisTemplate).getValueSerializer();
        when(redisSerializer.serialize(sampleCart)).thenReturn(serializedBytes);
        when(stringRedisTemplate.execute(
                eq(mutateCartScript),
                eq(List.of("cart:" + customerId, "checkout-claim:" + customerId)),
                eq(expectedCartJson),
                eq(updatedCartJson),
                eq("604800")
        )).thenReturn(2L);

        // Act
        CartMutationResult result = cartRepository.saveIfUnchanged(sampleCart, expectedCartJson);

        // Assert
        assertThat(result).isEqualTo(CartMutationResult.STALE_CART);
    }

    @Test
    @DisplayName("saveIfUnchanged should throw IllegalStateException when serialization returns null")
    void saveIfUnchanged_WhenSerializationFails_ShouldThrowIllegalStateException() {
        // Arrange
        doReturn(redisSerializer).when(redisTemplate).getValueSerializer();
        when(redisSerializer.serialize(sampleCart)).thenReturn(null);

        // Act & Assert
        assertThatThrownBy(() -> cartRepository.saveIfUnchanged(sampleCart, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to serialize cart");
    }

    @Test
    @DisplayName("saveIfUnchanged should throw IllegalStateException when script returns null")
    void saveIfUnchanged_WhenScriptReturnsNull_ShouldThrowIllegalStateException() {
        // Arrange
        String updatedCartJson = "{}";
        doReturn(redisSerializer).when(redisTemplate).getValueSerializer();
        when(redisSerializer.serialize(sampleCart)).thenReturn(updatedCartJson.getBytes(StandardCharsets.UTF_8));
        when(stringRedisTemplate.execute(
                eq(mutateCartScript),
                eq(List.of("cart:" + customerId, "checkout-claim:" + customerId)),
                eq(""),
                eq(updatedCartJson),
                eq("604800")
        )).thenReturn(null);

        // Act & Assert
        assertThatThrownBy(() -> cartRepository.saveIfUnchanged(sampleCart, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis cart mutation returned null");
    }

    @Test
    @DisplayName("saveIfUnchanged should throw IllegalStateException when script returns unexpected value")
    void saveIfUnchanged_WhenScriptReturnsUnexpectedValue_ShouldThrowIllegalStateException() {
        // Arrange
        String updatedCartJson = "{}";
        doReturn(redisSerializer).when(redisTemplate).getValueSerializer();
        when(redisSerializer.serialize(sampleCart)).thenReturn(updatedCartJson.getBytes(StandardCharsets.UTF_8));
        when(stringRedisTemplate.execute(
                eq(mutateCartScript),
                eq(List.of("cart:" + customerId, "checkout-claim:" + customerId)),
                eq(""),
                eq(updatedCartJson),
                eq("604800")
        )).thenReturn(99L);

        // Act & Assert
        assertThatThrownBy(() -> cartRepository.saveIfUnchanged(sampleCart, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unexpected cart mutation result: 99");
    }

    @Test
    @DisplayName("deleteIfUnchanged should return SUCCESS when Redis script returns 1")
    void deleteIfUnchanged_WhenScriptReturns1_ShouldReturnSuccess() {
        // Arrange
        String expectedCartJson = "{\"customerId\":\"" + customerId + "\"}";
        when(stringRedisTemplate.execute(
                eq(deleteCartScript),
                eq(List.of("cart:" + customerId, "checkout-claim:" + customerId)),
                eq(expectedCartJson)
        )).thenReturn(1L);

        // Act
        CartMutationResult result = cartRepository.deleteIfUnchanged(customerId, expectedCartJson);

        // Assert
        assertThat(result).isEqualTo(CartMutationResult.SUCCESS);
    }

    @Test
    @DisplayName("deleteIfUnchanged should return CHECKOUT_IN_PROGRESS when Redis script returns 0")
    void deleteIfUnchanged_WhenScriptReturns0_ShouldReturnCheckoutInProgress() {
        // Arrange
        String expectedCartJson = "{\"customerId\":\"" + customerId + "\"}";
        when(stringRedisTemplate.execute(
                eq(deleteCartScript),
                eq(List.of("cart:" + customerId, "checkout-claim:" + customerId)),
                eq(expectedCartJson)
        )).thenReturn(0L);

        // Act
        CartMutationResult result = cartRepository.deleteIfUnchanged(customerId, expectedCartJson);

        // Assert
        assertThat(result).isEqualTo(CartMutationResult.CHECKOUT_IN_PROGRESS);
    }

    @Test
    @DisplayName("deleteIfUnchanged should return STALE_CART when Redis script returns 2")
    void deleteIfUnchanged_WhenScriptReturns2_ShouldReturnStaleCart() {
        // Arrange
        String expectedCartJson = "{\"customerId\":\"" + customerId + "\"}";
        when(stringRedisTemplate.execute(
                eq(deleteCartScript),
                eq(List.of("cart:" + customerId, "checkout-claim:" + customerId)),
                eq(expectedCartJson)
        )).thenReturn(2L);

        // Act
        CartMutationResult result = cartRepository.deleteIfUnchanged(customerId, expectedCartJson);

        // Assert
        assertThat(result).isEqualTo(CartMutationResult.STALE_CART);
    }

    @Test
    @DisplayName("deleteIfUnchanged should throw IllegalStateException when script returns null")
    void deleteIfUnchanged_WhenScriptReturnsNull_ShouldThrowIllegalStateException() {
        // Arrange
        when(stringRedisTemplate.execute(
                eq(deleteCartScript),
                eq(List.of("cart:" + customerId, "checkout-claim:" + customerId)),
                eq("")
        )).thenReturn(null);

        // Act & Assert
        assertThatThrownBy(() -> cartRepository.deleteIfUnchanged(customerId, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis cart mutation returned null");
    }

    @Test
    @DisplayName("deleteIfUnchanged should throw IllegalStateException when script returns unexpected value")
    void deleteIfUnchanged_WhenScriptReturnsUnexpectedValue_ShouldThrowIllegalStateException() {
        // Arrange
        when(stringRedisTemplate.execute(
                eq(deleteCartScript),
                eq(List.of("cart:" + customerId, "checkout-claim:" + customerId)),
                eq("")
        )).thenReturn(42L);

        // Act & Assert
        assertThatThrownBy(() -> cartRepository.deleteIfUnchanged(customerId, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unexpected cart mutation result: 42");
    }
}
