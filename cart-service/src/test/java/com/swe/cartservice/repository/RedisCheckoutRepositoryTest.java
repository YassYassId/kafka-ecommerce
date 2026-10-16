package com.swe.cartservice.repository;

import com.swe.cartservice.model.CheckoutClaim;
import com.swe.cartservice.model.CheckoutClaimResult;
import com.swe.cartservice.model.CheckoutRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisCheckoutRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private DefaultRedisScript<Long> completeCheckoutScript;

    @Mock
    private DefaultRedisScript<Long> claimCheckoutScript;

    @Mock
    private DefaultRedisScript<Long> releaseCheckoutClaimScript;

    private RedisCheckoutRepository repository;

    private UUID customerId;
    private String idempotencyKey;
    private String leaseToken;
    private UUID orderId;
    private CheckoutRecord sampleRecord;

    @BeforeEach
    void setUp() {
        repository = new RedisCheckoutRepository(
                redisTemplate,
                objectMapper,
                completeCheckoutScript,
                claimCheckoutScript,
                releaseCheckoutClaimScript
        );

        customerId = UUID.randomUUID();
        idempotencyKey = UUID.randomUUID().toString();
        leaseToken = UUID.randomUUID().toString();
        orderId = UUID.randomUUID();
        sampleRecord = new CheckoutRecord(customerId, idempotencyKey, orderId, "PENDING");
    }

    @Test
    @DisplayName("findByCustomerIdAndKey should return CheckoutRecord when key exists in Redis")
    void findByCustomerIdAndKey_WhenKeyExists_ShouldReturnRecord() {
        // Arrange
        String key = "checkout:" + customerId + ":" + idempotencyKey;
        String json = "{\"customerId\":\"" + customerId + "\"}";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(json);
        when(objectMapper.readValue(json, CheckoutRecord.class)).thenReturn(sampleRecord);

        // Act
        Optional<CheckoutRecord> result = repository.findByCustomerIdAndKey(customerId, idempotencyKey);

        // Assert
        assertThat(result).contains(sampleRecord);
        verify(valueOperations).get(key);
        verify(objectMapper).readValue(json, CheckoutRecord.class);
    }

    @Test
    @DisplayName("findByCustomerIdAndKey should return empty Optional when key does not exist")
    void findByCustomerIdAndKey_WhenKeyDoesNotExist_ShouldReturnEmpty() {
        // Arrange
        String key = "checkout:" + customerId + ":" + idempotencyKey;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(null);

        // Act
        Optional<CheckoutRecord> result = repository.findByCustomerIdAndKey(customerId, idempotencyKey);

        // Assert
        assertThat(result).isEmpty();
        verify(valueOperations).get(key);
    }

    @Test
    @DisplayName("acquireCheckout should return ACQUIRED when script returns 1")
    void acquireCheckout_WhenScriptReturns1_ShouldReturnAcquired() {
        // Arrange
        String expectedCartJson = "{\"customerId\":\"" + customerId + "\"}";
        List<String> keys = List.of(
                "cart:" + customerId,
                "checkout-claim:" + customerId,
                "checkout-lease:" + customerId
        );

        when(redisTemplate.execute(claimCheckoutScript, keys, expectedCartJson, idempotencyKey, leaseToken, "60000"))
                .thenReturn(1L);

        // Act
        CheckoutClaimResult result = repository.acquireCheckout(customerId, idempotencyKey, expectedCartJson, leaseToken);

        // Assert
        assertThat(result).isEqualTo(CheckoutClaimResult.ACQUIRED);
        verify(redisTemplate).execute(claimCheckoutScript, keys, expectedCartJson, idempotencyKey, leaseToken, "60000");
    }

    @Test
    @DisplayName("acquireCheckout should return RESUMED when script returns 2")
    void acquireCheckout_WhenScriptReturns2_ShouldReturnResumed() {
        // Arrange
        String expectedCartJson = "{\"customerId\":\"" + customerId + "\"}";
        List<String> keys = List.of(
                "cart:" + customerId,
                "checkout-claim:" + customerId,
                "checkout-lease:" + customerId
        );

        when(redisTemplate.execute(claimCheckoutScript, keys, expectedCartJson, idempotencyKey, leaseToken, "60000"))
                .thenReturn(2L);

        // Act
        CheckoutClaimResult result = repository.acquireCheckout(customerId, idempotencyKey, expectedCartJson, leaseToken);

        // Assert
        assertThat(result).isEqualTo(CheckoutClaimResult.RESUMED);
        verify(redisTemplate).execute(claimCheckoutScript, keys, expectedCartJson, idempotencyKey, leaseToken, "60000");
    }

    @Test
    @DisplayName("acquireCheckout should return BUSY when script returns 3")
    void acquireCheckout_WhenScriptReturns3_ShouldReturnBusy() {
        // Arrange
        String expectedCartJson = "{\"customerId\":\"" + customerId + "\"}";
        List<String> keys = List.of(
                "cart:" + customerId,
                "checkout-claim:" + customerId,
                "checkout-lease:" + customerId
        );

        when(redisTemplate.execute(claimCheckoutScript, keys, expectedCartJson, idempotencyKey, leaseToken, "60000"))
                .thenReturn(3L);

        // Act
        CheckoutClaimResult result = repository.acquireCheckout(customerId, idempotencyKey, expectedCartJson, leaseToken);

        // Assert
        assertThat(result).isEqualTo(CheckoutClaimResult.BUSY);
        verify(redisTemplate).execute(claimCheckoutScript, keys, expectedCartJson, idempotencyKey, leaseToken, "60000");
    }

    @Test
    @DisplayName("acquireCheckout should return CONFLICT when script returns 4")
    void acquireCheckout_WhenScriptReturns4_ShouldReturnConflict() {
        // Arrange
        String expectedCartJson = "{\"customerId\":\"" + customerId + "\"}";
        List<String> keys = List.of(
                "cart:" + customerId,
                "checkout-claim:" + customerId,
                "checkout-lease:" + customerId
        );

        when(redisTemplate.execute(claimCheckoutScript, keys, expectedCartJson, idempotencyKey, leaseToken, "60000"))
                .thenReturn(4L);

        // Act
        CheckoutClaimResult result = repository.acquireCheckout(customerId, idempotencyKey, expectedCartJson, leaseToken);

        // Assert
        assertThat(result).isEqualTo(CheckoutClaimResult.CONFLICT);
        verify(redisTemplate).execute(claimCheckoutScript, keys, expectedCartJson, idempotencyKey, leaseToken, "60000");
    }

    @Test
    @DisplayName("acquireCheckout should return CART_CHANGED when script returns 5")
    void acquireCheckout_WhenScriptReturns5_ShouldReturnCartChanged() {
        // Arrange
        String expectedCartJson = "{\"customerId\":\"" + customerId + "\"}";
        List<String> keys = List.of(
                "cart:" + customerId,
                "checkout-claim:" + customerId,
                "checkout-lease:" + customerId
        );

        when(redisTemplate.execute(claimCheckoutScript, keys, expectedCartJson, idempotencyKey, leaseToken, "60000"))
                .thenReturn(5L);

        // Act
        CheckoutClaimResult result = repository.acquireCheckout(customerId, idempotencyKey, expectedCartJson, leaseToken);

        // Assert
        assertThat(result).isEqualTo(CheckoutClaimResult.CART_CHANGED);
        verify(redisTemplate).execute(claimCheckoutScript, keys, expectedCartJson, idempotencyKey, leaseToken, "60000");
    }

    @Test
    @DisplayName("acquireCheckout should throw IllegalStateException when script returns null")
    void acquireCheckout_WhenScriptReturnsNull_ShouldThrowException() {
        // Arrange
        String expectedCartJson = "{\"customerId\":\"" + customerId + "\"}";
        List<String> keys = List.of(
                "cart:" + customerId,
                "checkout-claim:" + customerId,
                "checkout-lease:" + customerId
        );

        when(redisTemplate.execute(claimCheckoutScript, keys, expectedCartJson, idempotencyKey, leaseToken, "60000"))
                .thenReturn(null);

        // Act & Assert
        assertThatThrownBy(() -> repository.acquireCheckout(customerId, idempotencyKey, expectedCartJson, leaseToken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis checkout acquisition returned null");
    }

    @Test
    @DisplayName("acquireCheckout should throw IllegalStateException when script returns unknown code")
    void acquireCheckout_WhenScriptReturnsUnknownCode_ShouldThrowException() {
        // Arrange
        String expectedCartJson = "{\"customerId\":\"" + customerId + "\"}";
        List<String> keys = List.of(
                "cart:" + customerId,
                "checkout-claim:" + customerId,
                "checkout-lease:" + customerId
        );

        when(redisTemplate.execute(claimCheckoutScript, keys, expectedCartJson, idempotencyKey, leaseToken, "60000"))
                .thenReturn(99L);

        // Act & Assert
        assertThatThrownBy(() -> repository.acquireCheckout(customerId, idempotencyKey, expectedCartJson, leaseToken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unknown checkout acquisition result: 99");
    }

    @Test
    @DisplayName("findClaim should return CheckoutClaim when key exists in Redis")
    void findClaim_WhenClaimExists_ShouldReturnClaim() {
        // Arrange
        String key = "checkout-claim:" + customerId;
        String json = "{\"idempotencyKey\":\"" + idempotencyKey + "\",\"cartSnapshot\":\"{}\"}";
        CheckoutClaim claim = new CheckoutClaim(idempotencyKey, "{}");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(json);
        when(objectMapper.readValue(json, CheckoutClaim.class)).thenReturn(claim);

        // Act
        Optional<CheckoutClaim> result = repository.findClaim(customerId);

        // Assert
        assertThat(result).contains(claim);
        verify(valueOperations).get(key);
        verify(objectMapper).readValue(json, CheckoutClaim.class);
    }

    @Test
    @DisplayName("findClaim should return empty Optional when key does not exist")
    void findClaim_WhenClaimDoesNotExist_ShouldReturnEmpty() {
        // Arrange
        String key = "checkout-claim:" + customerId;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(null);

        // Act
        Optional<CheckoutClaim> result = repository.findClaim(customerId);

        // Assert
        assertThat(result).isEmpty();
        verify(valueOperations).get(key);
    }

    @Test
    @DisplayName("releaseClaim should return true when script returns 1")
    void releaseClaim_WhenScriptReturns1_ShouldReturnTrue() {
        // Arrange
        List<String> keys = List.of(
                "checkout-claim:" + customerId,
                "checkout-lease:" + customerId
        );

        when(redisTemplate.execute(releaseCheckoutClaimScript, keys, idempotencyKey, leaseToken))
                .thenReturn(1L);

        // Act
        boolean result = repository.releaseClaim(customerId, idempotencyKey, leaseToken);

        // Assert
        assertThat(result).isTrue();
        verify(redisTemplate).execute(releaseCheckoutClaimScript, keys, idempotencyKey, leaseToken);
    }

    @Test
    @DisplayName("releaseClaim should return false when script returns 0")
    void releaseClaim_WhenScriptReturns0_ShouldReturnFalse() {
        // Arrange
        List<String> keys = List.of(
                "checkout-claim:" + customerId,
                "checkout-lease:" + customerId
        );

        when(redisTemplate.execute(releaseCheckoutClaimScript, keys, idempotencyKey, leaseToken))
                .thenReturn(0L);

        // Act
        boolean result = repository.releaseClaim(customerId, idempotencyKey, leaseToken);

        // Assert
        assertThat(result).isFalse();
        verify(redisTemplate).execute(releaseCheckoutClaimScript, keys, idempotencyKey, leaseToken);
    }

    @Test
    @DisplayName("completeCheckout should return true when script returns 1")
    void completeCheckout_WhenScriptReturns1_ShouldReturnTrue() {
        // Arrange
        String expectedCartJson = "{\"customerId\":\"" + customerId + "\"}";
        String recordJson = "{\"customerId\":\"" + customerId + "\",\"orderId\":\"" + orderId + "\"}";
        List<String> keys = List.of(
                "cart:" + customerId,
                "checkout:" + customerId + ":" + idempotencyKey,
                "checkout-claim:" + customerId,
                "checkout-lease:" + customerId
        );

        when(objectMapper.writeValueAsString(sampleRecord)).thenReturn(recordJson);
        when(redisTemplate.execute(completeCheckoutScript, keys, expectedCartJson, recordJson, "86400", idempotencyKey, leaseToken))
                .thenReturn(1L);

        // Act
        boolean result = repository.completeCheckout(sampleRecord, expectedCartJson, leaseToken);

        // Assert
        assertThat(result).isTrue();
        verify(objectMapper).writeValueAsString(sampleRecord);
        verify(redisTemplate).execute(completeCheckoutScript, keys, expectedCartJson, recordJson, "86400", idempotencyKey, leaseToken);
    }

    @Test
    @DisplayName("completeCheckout should return false when script returns 0")
    void completeCheckout_WhenScriptReturns0_ShouldReturnFalse() {
        // Arrange
        String expectedCartJson = "{\"customerId\":\"" + customerId + "\"}";
        String recordJson = "{\"customerId\":\"" + customerId + "\",\"orderId\":\"" + orderId + "\"}";
        List<String> keys = List.of(
                "cart:" + customerId,
                "checkout:" + customerId + ":" + idempotencyKey,
                "checkout-claim:" + customerId,
                "checkout-lease:" + customerId
        );

        when(objectMapper.writeValueAsString(sampleRecord)).thenReturn(recordJson);
        when(redisTemplate.execute(completeCheckoutScript, keys, expectedCartJson, recordJson, "86400", idempotencyKey, leaseToken))
                .thenReturn(0L);

        // Act
        boolean result = repository.completeCheckout(sampleRecord, expectedCartJson, leaseToken);

        // Assert
        assertThat(result).isFalse();
        verify(objectMapper).writeValueAsString(sampleRecord);
        verify(redisTemplate).execute(completeCheckoutScript, keys, expectedCartJson, recordJson, "86400", idempotencyKey, leaseToken);
    }
}
