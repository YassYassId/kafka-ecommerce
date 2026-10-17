package com.swe.cartservice.repository;

import com.swe.cartservice.model.Cart;
import com.swe.cartservice.model.CartMutationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RedisCartRepository implements CartRepository {
    private static final String KEY_PREFIX = "cart:";
    private static final String CLAIM_PREFIX = "checkout-claim:";
    private static final Duration CART_TTL = Duration.ofDays(7);

    private final RedisTemplate<String, Cart> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    private final DefaultRedisScript<Long> mutateCartScript;
    private final DefaultRedisScript<Long> deleteCartScript;

    public RedisCartRepository(RedisTemplate<String, Cart> redisTemplate, StringRedisTemplate stringRedisTemplate,
            @Qualifier("mutateCartScript")
            DefaultRedisScript<Long> mutateCartScript,
            @Qualifier("deleteCartScript")
            DefaultRedisScript<Long> deleteCartScript) {

        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.mutateCartScript = mutateCartScript;
        this.deleteCartScript = deleteCartScript;
    }

    @Override
    public Optional<Cart> findByCustomerId(UUID customerId) {
        Cart cart = redisTemplate.opsForValue().get(key(customerId));

        return Optional.ofNullable(cart);
    }

    @Override
    public Optional<String> findRawCartByCustomerId(UUID customerId) {
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(key(customerId)));
    }

    @Override
    public CartMutationResult saveIfUnchanged(Cart cart, String expectedCartJson) {

        @SuppressWarnings("unchecked")
        RedisSerializer<Cart> serializer = (RedisSerializer<Cart>) redisTemplate.getValueSerializer();

        byte[] serialized = serializer.serialize(cart);

        if (serialized == null) {
            throw new IllegalStateException("Failed to serialize cart");
        }

        String updatedCartJson = new String(serialized, java.nio.charset.StandardCharsets.UTF_8);

        Long result = stringRedisTemplate.execute(mutateCartScript,
                List.of(key(cart.customerId()), claimKey(cart.customerId())),
                expectedCartJson, updatedCartJson, String.valueOf(CART_TTL.toSeconds()));

        return mapResult(result);
    }

    @Override
    public CartMutationResult deleteIfUnchanged(UUID customerId, String expectedCartJson) {

        Long result = stringRedisTemplate.execute(deleteCartScript,
                List.of(key(customerId), claimKey(customerId)), expectedCartJson);

        return mapResult(result);
    }

    private CartMutationResult mapResult(Long result) {

        if (result == null) {
            throw new IllegalStateException("Redis cart mutation returned null");
        }

        return switch (result.intValue()) {
            case 0 -> CartMutationResult.CHECKOUT_IN_PROGRESS;
            case 1 -> CartMutationResult.SUCCESS;
            case 2 -> CartMutationResult.STALE_CART;
            default -> throw new IllegalStateException("Unexpected cart mutation result: " + result);
        };
    }

    private String key(UUID customerId) {
        return KEY_PREFIX + customerId;
    }

    private String claimKey(UUID customerId) {
        return CLAIM_PREFIX + customerId;
    }
}
