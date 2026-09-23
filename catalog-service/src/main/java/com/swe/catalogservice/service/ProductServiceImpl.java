package com.swe.catalogservice.service;

import com.swe.catalogservice.dto.CreateProductRequest;
import com.swe.catalogservice.dto.ProductResponse;
import com.swe.catalogservice.dto.UpdateProductRequest;
import com.swe.catalogservice.entity.Product;
import com.swe.catalogservice.entity.ProductStatus;
import com.swe.catalogservice.event.PriceChangedEvent;
import com.swe.catalogservice.event.ProductCreatedEvent;
import com.swe.catalogservice.event.ProductRetiredEvent;
import com.swe.catalogservice.event.ProductUpdatedEvent;
import com.swe.catalogservice.exception.DuplicateSkuException;
import com.swe.catalogservice.exception.ProductNotFoundException;
import com.swe.catalogservice.outbox.OutboxService;
import com.swe.catalogservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final OutboxService outboxService;

    @Override
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        String sku = request.sku().trim().toUpperCase();
        String currency = request.currency().trim().toUpperCase();
        if (productRepository.existsBySku(sku)) {
            throw new DuplicateSkuException(sku);
        }

        Product product = Product.builder()
                .sku(sku)
                .name(request.name().trim())
                .description(request.description())
                .category(request.category().trim())
                .price(request.price())
                .currency(currency)
                .status(ProductStatus.ACTIVE)
                .build();

        Product savedProduct = productRepository.save(product);

        UUID eventId = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.now();

        ProductCreatedEvent event = new ProductCreatedEvent(eventId, savedProduct.getId(), savedProduct.getSku(), savedProduct.getName(),
                savedProduct.getDescription(), savedProduct.getCategory(), savedProduct.getPrice(), savedProduct.getCurrency(), occurredAt,
                1);

        outboxService.saveEvent(eventId, savedProduct.getId(), "ProductCreated", occurredAt, event);

        return toResponse(savedProduct);
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getCategory(),
                product.getPrice(),
                product.getCurrency(),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID id) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getProducts(String name, String category, ProductStatus status, Pageable pageable) {

        String nameFilter = name == null || name.isBlank() ? null : "%" + name.trim().toLowerCase() + "%";

        String normalizedCategory = category == null || category.isBlank() ? null : category.trim();

        return productRepository.findAllFiltered(nameFilter, normalizedCategory, status, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(UUID id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        String newName = request.name().trim();
        String newDescription = request.description();
        String newCategory = request.category().trim();
        BigDecimal newPrice = request.price();
        String newCurrency = request.currency().trim().toUpperCase();

        boolean productDetailsChanged = !Objects.equals(product.getName(), newName)
                        || !Objects.equals(product.getDescription(), newDescription)
                        || !Objects.equals(product.getCategory(), newCategory)
                        || !Objects.equals(product.getCurrency(), newCurrency);

        boolean priceChanged = product.getPrice().compareTo(newPrice) != 0;

        BigDecimal oldPrice = product.getPrice();

        product.setName(newName);
        product.setDescription(newDescription);
        product.setCategory(newCategory);
        product.setPrice(newPrice);
        product.setCurrency(newCurrency);

        if (productDetailsChanged) {
            UUID eventId = UUID.randomUUID();
            OffsetDateTime occurredAt = OffsetDateTime.now();

            ProductUpdatedEvent event = new ProductUpdatedEvent(
                    eventId,
                    product.getId(),
                    product.getSku(),
                    product.getName(),
                    product.getDescription(),
                    product.getCategory(),
                    product.getCurrency(),
                    occurredAt,
                    1
            );

            outboxService.saveEvent(eventId, product.getId(), "ProductUpdated", occurredAt, event);
        }

        if (priceChanged) {

            UUID eventId = UUID.randomUUID();
            OffsetDateTime occurredAt = OffsetDateTime.now();

            PriceChangedEvent event = new PriceChangedEvent(
                    eventId,
                    product.getId(),
                    oldPrice,
                    newPrice,
                    product.getCurrency(),
                    occurredAt,
                    1
            );

            outboxService.saveEvent(eventId, product.getId(), "PriceChanged", occurredAt, event);
        }

        return toResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse retireProduct(UUID id) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        if (product.getStatus() == ProductStatus.ACTIVE) {
            product.setStatus(ProductStatus.RETIRED);

            UUID eventId = UUID.randomUUID();
            OffsetDateTime occurredAt = OffsetDateTime.now();

            ProductRetiredEvent event = new ProductRetiredEvent(
                    eventId,
                    product.getId(),
                    product.getSku(),
                    occurredAt,
                    1
            );

            outboxService.saveEvent(eventId, product.getId(), "ProductRetired", occurredAt, event);
        }

        return toResponse(product);
    }
}
