package com.swe.cartservice.controller;

import com.swe.cartservice.dto.AddCartItemRequest;
import com.swe.cartservice.dto.UpdateCartItemRequest;
import com.swe.cartservice.model.Cart;
import com.swe.cartservice.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/carts")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping("/{customerId}")
    public ResponseEntity<Cart> getCart(@PathVariable UUID customerId) {

        return ResponseEntity.ok(cartService.getCart(customerId));
    }

    @PostMapping("/{customerId}/items")
    public ResponseEntity<Cart> addItem(@PathVariable UUID customerId, @Valid @RequestBody AddCartItemRequest request) {

        return ResponseEntity.ok(cartService.addItem(customerId, request));
    }

    @PatchMapping("/{customerId}/items/{productId}")
    public ResponseEntity<Cart> updateItem(@PathVariable UUID customerId, @PathVariable UUID productId, @Valid @RequestBody UpdateCartItemRequest request) {

        return ResponseEntity.ok(
                cartService.updateItemQuantity(customerId, productId, request.quantity())
        );
    }

    @DeleteMapping("/{customerId}/items/{productId}")
    public ResponseEntity<Cart> removeItem(@PathVariable UUID customerId, @PathVariable UUID productId) {

        return ResponseEntity.ok(cartService.removeItem(customerId, productId));
    }

    @DeleteMapping("/{customerId}")
    public ResponseEntity<Void> clearCart(
            @PathVariable UUID customerId) {

        cartService.clearCart(customerId);

        return ResponseEntity.noContent().build();
    }
}
