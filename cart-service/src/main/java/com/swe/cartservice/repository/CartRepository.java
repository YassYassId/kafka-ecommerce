package com.swe.cartservice.repository;

import com.swe.cartservice.model.Cart;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository {

    Optional<Cart> findByCustomerId(UUID customerId);

    void save(Cart cart);

    void deleteByCustomerId(UUID customerId);
}
