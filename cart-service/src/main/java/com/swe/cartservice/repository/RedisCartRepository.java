package com.swe.cartservice.repository;

import com.swe.cartservice.model.Cart;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RedisCartRepository implements CartRepository {
    private static final String KEY_PREFIX = "cart:";
    private static final Duration CART_TTL = Duration.ofDays(7);

    private final RedisTemplate<String, Cart> redisTemplate;

    @Override
    public Optional<Cart> findByCustomerId(UUID customerId) {
        Cart cart = redisTemplate.opsForValue().get(key(customerId));

        return Optional.ofNullable(cart);
    }

    @Override
    public void save(Cart cart) {
        redisTemplate.opsForValue().set(key(cart.customerId()), cart, CART_TTL);
    }

    @Override
    public void deleteByCustomerId(UUID customerId) {
        redisTemplate.delete(key(customerId));
    }

    private String key(UUID customerId) {
        return KEY_PREFIX + customerId;
    }
}
