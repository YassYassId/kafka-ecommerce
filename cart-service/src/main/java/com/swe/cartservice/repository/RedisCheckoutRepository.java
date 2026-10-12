package com.swe.cartservice.repository;

import com.swe.cartservice.model.CheckoutRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RedisCheckoutRepository implements CheckoutRepository {

    private static final Duration CHECKOUT_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final DefaultRedisScript<Long> completeCheckoutScript;
    private final DefaultRedisScript<Long> claimCheckoutScript;
    private final DefaultRedisScript<Long> releaseCheckoutClaimScript;

    public RedisCheckoutRepository(
            StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
            @Qualifier("completeCheckoutScript") DefaultRedisScript<Long> completeCheckoutScript,
            @Qualifier("claimCheckoutScript") DefaultRedisScript<Long> claimCheckoutScript,
            @Qualifier("releaseCheckoutClaimScript") DefaultRedisScript<Long> releaseCheckoutClaimScript) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.completeCheckoutScript = completeCheckoutScript;
        this.claimCheckoutScript = claimCheckoutScript;
        this.releaseCheckoutClaimScript = releaseCheckoutClaimScript;
    }

    @Override
    public Optional<CheckoutRecord> findByCustomerIdAndKey(UUID customerId, String idempotencyKey) {
        String json = redisTemplate.opsForValue().get(checkoutKey(customerId, idempotencyKey));

        if (json == null) {
            return Optional.empty();
        }

        return Optional.of(objectMapper.readValue(json, CheckoutRecord.class));
    }

    @Override
    public boolean claimCheckout(UUID customerId, String idempotencyKey, String expectedCartJson) {
        Long result = redisTemplate.execute(claimCheckoutScript,
                List.of(cartKey(customerId), claimKey(customerId)), expectedCartJson, idempotencyKey);

        return Long.valueOf(1L).equals(result);
    }

    @Override
    public boolean releaseClaim(UUID customerId, String idempotencyKey) {
        Long result = redisTemplate.execute(releaseCheckoutClaimScript, List.of(claimKey(customerId)), idempotencyKey);

        return Long.valueOf(1L).equals(result);
    }

    @Override
    public boolean completeCheckout(CheckoutRecord record, String expectedCartJson) {
        String json = objectMapper.writeValueAsString(record);

        Long result = redisTemplate.execute(completeCheckoutScript,
                List.of(
                        cartKey(record.customerId()),
                        checkoutKey(record.customerId(), record.idempotencyKey()),
                        claimKey(record.customerId())
                ), expectedCartJson, json, String.valueOf(CHECKOUT_TTL.toSeconds()), record.idempotencyKey());

        return Long.valueOf(1L).equals(result);
    }

    private String cartKey(UUID customerId) {
        return "cart:" + customerId;
    }

    private String checkoutKey(UUID customerId, String idempotencyKey) {
        return "checkout:" + customerId + ":" + idempotencyKey;
    }

    private String claimKey(UUID customerId) {
        return "checkout-claim:" + customerId;
    }
}