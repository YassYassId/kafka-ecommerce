package com.swe.catalogservice.service;

import com.swe.catalogservice.dto.CreateProductRequest;
import com.swe.catalogservice.dto.ProductResponse;
import com.swe.catalogservice.dto.UpdateProductRequest;
import com.swe.catalogservice.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProductService {

    ProductResponse createProduct(CreateProductRequest request);

    ProductResponse getProduct(UUID id);

    Page<ProductResponse> getProducts(String name, String category, ProductStatus status, Pageable pageable);

    ProductResponse updateProduct(UUID id, UpdateProductRequest request);

    ProductResponse retireProduct(UUID id);
}
