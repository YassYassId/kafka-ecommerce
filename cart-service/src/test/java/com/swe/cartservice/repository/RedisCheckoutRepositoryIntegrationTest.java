package com.swe.cartservice.repository;

import com.swe.cartservice.config.CheckoutRedisConfig;
import com.swe.cartservice.model.CheckoutClaim;
import com.swe.cartservice.model.CheckoutRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.swe.cartservice.model.CheckoutClaimResult.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Requires real Redis (default localhost:6379); never skips when Redis is unavailable.
 * Override with -Dtest.redis.host=... -Dtest.redis.port=... . No shared database flushes.
 */
class RedisCheckoutRepositoryIntegrationTest {

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;
    private static ObjectMapper mapper;
    private static RedisCheckoutRepository repository;
    private final List<CheckoutFixture> fixtures = new ArrayList<>();

    @BeforeAll
    static void connectToRedis() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                System.getProperty("test.redis.host", "localhost"),
                Integer.getInteger("test.redis.port", 6379));
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new StringRedisTemplate(connectionFactory);
        mapper = JsonMapper.builder().findAndAddModules().build();
        CheckoutRedisConfig scripts = new CheckoutRedisConfig();
        repository = new RedisCheckoutRepository(redis, mapper, scripts.completeCheckoutScript(),
                scripts.claimCheckoutScript(), scripts.releaseCheckoutClaimScript());
        try (var connection = connectionFactory.getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
        }
    }

    @AfterEach
    void cleanUpCustomerKeys() {
        fixtures.forEach(fixture -> redis.delete(fixture.keys()));
    }

    @AfterAll
    static void disconnect() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    @DisplayName("A: fresh acquisition writes durable snapshot and leased execution token")
    void shouldAcquireFreshCheckoutWithDurableClaimAndLease() {
        CheckoutFixture fixture = fixture();

        acquire(fixture);

        assertOwnedState(fixture, fixture.snapshot, fixture.tokenA);
        assertThat(redis.getExpire(fixture.claimKey(), TimeUnit.MILLISECONDS)).isEqualTo(-1L);
        assertThat(redis.getExpire(fixture.leaseKey(), TimeUnit.MILLISECONDS)).isBetween(1L, 60000L);
    }

    @Test
    @DisplayName("B: active lease returns BUSY without overwriting ownership")
    void shouldKeepOriginalOwnershipWhenActiveLeaseIsBusy() {
        CheckoutFixture fixture = acquiredFixture();
        String claimJson = value(fixture.claimKey());

        assertThat(repository.acquireCheckout(fixture.customerId, fixture.keyA, fixture.snapshot, fixture.tokenB))
                .isEqualTo(BUSY);

        assertThat(value(fixture.claimKey())).isEqualTo(claimJson);
        assertOwnedState(fixture, fixture.snapshot, fixture.tokenA);
    }

    @Test
    @DisplayName("C/E/I: real lease expiry preserves snapshot, rejects changed cart and fences stale release")
    void shouldResumeAfterRealExpiryAndProtectSuccessorAndChangedCart() throws InterruptedException {
        // Start all three production leases together so actual expiry costs one minute in total.
        CheckoutFixture resumed = acquiredFixture();
        CheckoutFixture changed = acquiredFixture();
        CheckoutFixture successor = acquiredFixture();
        String resumedClaim = value(resumed.claimKey());
        String changedClaim = value(changed.claimKey());
        String successorClaim = value(successor.claimKey());
        List<CheckoutFixture> expiring = List.of(resumed, changed, successor);
        long deadline = System.nanoTime() + Duration.ofSeconds(70).toNanos();
        while (expiring.stream().anyMatch(fixture -> redis.hasKey(fixture.leaseKey()))
                && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        expiring.forEach(fixture -> {
            assertThat(redis.hasKey(fixture.leaseKey())).isFalse();
            assertThat(redis.getExpire(fixture.leaseKey(), TimeUnit.MILLISECONDS)).isEqualTo(-2L);
        });

        // C: Redis, not a mock or a manually deleted/shortened lease, determines expiration.
        assertThat(repository.acquireCheckout(resumed.customerId, resumed.keyA, resumed.snapshot, resumed.tokenB))
                .isEqualTo(RESUMED);
        assertThat(value(resumed.claimKey())).isEqualTo(resumedClaim);
        assertOwnedState(resumed, resumed.snapshot, resumed.tokenB);

        // E: a durable snapshot cannot be replaced by the current, changed cart.
        String newerCart = changed.newerSnapshot();
        redis.opsForValue().set(changed.cartKey(), newerCart);
        assertThat(repository.acquireCheckout(changed.customerId, changed.keyA, newerCart, changed.tokenB))
                .isEqualTo(CART_CHANGED);
        assertThat(value(changed.claimKey())).isEqualTo(changedClaim);
        assertThat(repository.findClaim(changed.customerId)).contains(new CheckoutClaim(changed.keyA, changed.snapshot));
        assertThat(repository.completeCheckout(changed.record(changed.keyA), changed.snapshot, changed.tokenA)).isFalse();
        assertThat(value(changed.cartKey())).isEqualTo(newerCart);
        assertThat(value(changed.claimKey())).isEqualTo(changedClaim);
        assertThat(redis.hasKey(changed.leaseKey())).isFalse();
        assertNoCompletedRecord(changed);

        // I: old execution must not delete the successor's claim or lease.
        assertThat(repository.acquireCheckout(successor.customerId, successor.keyA, successor.snapshot, successor.tokenB))
                .isEqualTo(RESUMED);
        assertOwnedState(successor, successor.snapshot, successor.tokenB);
        assertThat(repository.releaseClaim(successor.customerId, successor.keyA, successor.tokenA)).isFalse();
        assertThat(value(successor.claimKey())).isEqualTo(successorClaim);
        assertOwnedState(successor, successor.snapshot, successor.tokenB);
        assertThat(repository.releaseClaim(successor.customerId, successor.keyA, successor.tokenB)).isTrue();
        assertReleasedState(successor);
    }

    @Test
    @DisplayName("D: different idempotency key cannot replace durable ownership")
    void shouldRejectDifferentKeyWithoutChangingState() {
        CheckoutFixture fixture = acquiredFixture();
        String claimJson = value(fixture.claimKey());

        assertThat(repository.acquireCheckout(fixture.customerId, fixture.keyB, fixture.snapshot, fixture.tokenB))
                .isEqualTo(CONFLICT);

        assertThat(value(fixture.claimKey())).isEqualTo(claimJson);
        assertOwnedState(fixture, fixture.snapshot, fixture.tokenA);
        assertNoCompletedRecord(fixture);
    }

    @Test
    @DisplayName("F: current owner releases claim and lease while retaining cart")
    void shouldReleaseOnlyClaimAndLeaseForCurrentOwner() {
        CheckoutFixture fixture = acquiredFixture();

        assertThat(repository.releaseClaim(fixture.customerId, fixture.keyA, fixture.tokenA)).isTrue();

        assertReleasedState(fixture);
    }

    @Test
    @DisplayName("G: wrong idempotency key cannot release claim or lease")
    void shouldRejectReleaseWithWrongKey() {
        CheckoutFixture fixture = acquiredFixture();
        String claimJson = value(fixture.claimKey());

        assertThat(repository.releaseClaim(fixture.customerId, fixture.keyB, fixture.tokenA)).isFalse();

        assertThat(value(fixture.claimKey())).isEqualTo(claimJson);
        assertOwnedState(fixture, fixture.snapshot, fixture.tokenA);
    }

    @Test
    @DisplayName("H: wrong lease token cannot release claim or lease")
    void shouldRejectReleaseWithWrongToken() {
        CheckoutFixture fixture = acquiredFixture();
        String claimJson = value(fixture.claimKey());

        assertThat(repository.releaseClaim(fixture.customerId, fixture.keyA, fixture.tokenB)).isFalse();

        assertThat(value(fixture.claimKey())).isEqualTo(claimJson);
        assertOwnedState(fixture, fixture.snapshot, fixture.tokenA);
    }

    @Test
    @DisplayName("J: completion deletes cart/claim/lease and writes exact completed record with TTL")
    void shouldCompleteCheckoutAndStoreRecordWithTtl() {
        CheckoutFixture fixture = acquiredFixture();
        CheckoutRecord record = fixture.record(fixture.keyA);

        assertThat(repository.completeCheckout(record, fixture.snapshot, fixture.tokenA)).isTrue();

        assertThat(redis.hasKey(fixture.cartKey())).isFalse();
        assertThat(redis.hasKey(fixture.claimKey())).isFalse();
        assertThat(redis.hasKey(fixture.leaseKey())).isFalse();
        String completedJson = value(fixture.completedKey(fixture.keyA));
        assertThat(completedJson).isNotNull();
        CheckoutRecord stored = mapper.readValue(completedJson, CheckoutRecord.class);
        assertThat(stored.customerId()).isEqualTo(fixture.customerId);
        assertThat(stored.idempotencyKey()).isEqualTo(fixture.keyA);
        assertThat(stored.orderId()).isEqualTo(fixture.orderId);
        assertThat(stored.status()).isEqualTo("PENDING");
        assertThat(repository.findByCustomerIdAndKey(fixture.customerId, fixture.keyA)).contains(record);
        assertThat(redis.getExpire(fixture.completedKey(fixture.keyA), TimeUnit.SECONDS)).isBetween(1L, 86400L);
        assertThat(redis.hasKey(fixture.completedKey(fixture.keyB))).isFalse();
    }

    @Test
    @DisplayName("K: wrong lease token cannot complete or partially mutate checkout")
    void shouldRejectCompletionWithWrongToken() {
        CheckoutFixture fixture = acquiredFixture();
        String claimJson = value(fixture.claimKey());

        assertThat(repository.completeCheckout(fixture.record(fixture.keyA), fixture.snapshot, fixture.tokenB)).isFalse();

        assertThat(value(fixture.claimKey())).isEqualTo(claimJson);
        assertOwnedState(fixture, fixture.snapshot, fixture.tokenA);
    }

    @Test
    @DisplayName("L: record for another idempotency key cannot complete checkout")
    void shouldRejectCompletionWithWrongKey() {
        CheckoutFixture fixture = acquiredFixture();
        String claimJson = value(fixture.claimKey());

        assertThat(repository.completeCheckout(fixture.record(fixture.keyB), fixture.snapshot, fixture.tokenA)).isFalse();

        assertThat(value(fixture.claimKey())).isEqualTo(claimJson);
        assertOwnedState(fixture, fixture.snapshot, fixture.tokenA);
    }

    @Test
    @DisplayName("M: stale completion cannot delete a newer cart")
    void shouldRejectCompletionWhenCartChanged() {
        CheckoutFixture fixture = acquiredFixture();
        String claimJson = value(fixture.claimKey());
        String newerCart = fixture.newerSnapshot();
        redis.opsForValue().set(fixture.cartKey(), newerCart);

        assertThat(repository.completeCheckout(fixture.record(fixture.keyA), fixture.snapshot, fixture.tokenA)).isFalse();

        assertThat(value(fixture.claimKey())).isEqualTo(claimJson);
        assertOwnedState(fixture, newerCart, fixture.tokenA);
    }

    @Test
    @DisplayName("N: missing cart prevents completion without deleting claim or lease")
    void shouldRejectCompletionWhenCartMissing() {
        CheckoutFixture fixture = acquiredFixture();
        String claimJson = value(fixture.claimKey());
        redis.delete(fixture.cartKey());

        assertThat(repository.completeCheckout(fixture.record(fixture.keyA), fixture.snapshot, fixture.tokenA)).isFalse();

        assertThat(value(fixture.claimKey())).isEqualTo(claimJson);
        assertOwnedState(fixture, null, fixture.tokenA);
    }

    private CheckoutFixture fixture() {
        CheckoutFixture fixture = new CheckoutFixture();
        fixtures.add(fixture);
        redis.delete(fixture.keys());
        redis.opsForValue().set(fixture.cartKey(), fixture.snapshot);
        return fixture;
    }

    private CheckoutFixture acquiredFixture() {
        CheckoutFixture fixture = fixture();
        acquire(fixture);
        return fixture;
    }

    private void acquire(CheckoutFixture fixture) {
        assertThat(repository.acquireCheckout(fixture.customerId, fixture.keyA, fixture.snapshot, fixture.tokenA))
                .isEqualTo(ACQUIRED);
    }

    private void assertOwnedState(CheckoutFixture fixture, String currentCart, String ownerToken) {
        assertThat(value(fixture.cartKey())).isEqualTo(currentCart);
        assertThat(repository.findClaim(fixture.customerId)).contains(new CheckoutClaim(fixture.keyA, fixture.snapshot));
        assertThat(value(fixture.leaseKey())).isEqualTo(ownerToken);
        assertThat(redis.getExpire(fixture.leaseKey(), TimeUnit.MILLISECONDS)).isBetween(1L, 60000L);
        assertNoCompletedRecord(fixture);
    }

    private void assertReleasedState(CheckoutFixture fixture) {
        assertThat(redis.hasKey(fixture.claimKey())).isFalse();
        assertThat(redis.hasKey(fixture.leaseKey())).isFalse();
        assertThat(value(fixture.cartKey())).isEqualTo(fixture.snapshot);
        assertNoCompletedRecord(fixture);
    }

    private void assertNoCompletedRecord(CheckoutFixture fixture) {
        assertThat(redis.hasKey(fixture.completedKey(fixture.keyA))).isFalse();
        assertThat(redis.hasKey(fixture.completedKey(fixture.keyB))).isFalse();
    }

    private static String value(String key) {
        return redis.opsForValue().get(key);
    }

    private static class CheckoutFixture {
        final UUID customerId = UUID.randomUUID();
        final String keyA = UUID.randomUUID().toString();
        final String keyB = UUID.randomUUID().toString();
        final String tokenA = UUID.randomUUID().toString();
        final String tokenB = UUID.randomUUID().toString();
        final UUID orderId = UUID.randomUUID();
        final UUID productId = UUID.randomUUID();
        final String snapshot = "{\"customerId\":\"" + customerId
                + "\",\"items\":[{\"productId\":\"" + productId + "\",\"quantity\":1,\"price\":10.00}]}";

        String newerSnapshot() { return snapshot.replace("\"quantity\":1", "\"quantity\":2"); }
        String cartKey() { return "cart:" + customerId; }
        String claimKey() { return "checkout-claim:" + customerId; }
        String leaseKey() { return "checkout-lease:" + customerId; }
        String completedKey(String key) { return "checkout:" + customerId + ":" + key; }
        CheckoutRecord record(String key) { return new CheckoutRecord(customerId, key, orderId, "PENDING"); }
        List<String> keys() {
            return List.of(cartKey(), claimKey(), leaseKey(), completedKey(keyA), completedKey(keyB));
        }
    }
}
