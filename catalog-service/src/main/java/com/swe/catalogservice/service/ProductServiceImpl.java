package com.swe.catalogservice.service;

import com.swe.catalogservice.dto.CreateProductRequest;
import com.swe.catalogservice.dto.ProductResponse;
import com.swe.catalogservice.dto.UpdateProductRequest;
import com.swe.catalogservice.entity.Product;
import com.swe.catalogservice.entity.ProductStatus;
import com.swe.catalogservice.exception.DuplicateSkuException;
import com.swe.catalogservice.exception.ProductNotFoundException;
import com.swe.catalogservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

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

        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setCategory(request.category().trim());
        product.setPrice(request.price());
        product.setCurrency(request.currency().trim().toUpperCase());

        return toResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse retireProduct(UUID id) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        if (product.getStatus() == ProductStatus.ACTIVE) {
            product.setStatus(ProductStatus.RETIRED);
        }

        return toResponse(product);
    }
}
