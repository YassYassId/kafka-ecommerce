package com.swe.cartservice.repository;

import com.swe.cartservice.model.Cart;
import com.swe.cartservice.model.CartMutationResult;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository {

    Optional<Cart> findByCustomerId(UUID customerId);

    Optional<String> findRawCartByCustomerId(UUID customerId);

    CartMutationResult saveIfUnchanged(Cart cart, String expectedCartJson);

    CartMutationResult deleteIfUnchanged(UUID customerId, String expectedCartJson);
}
