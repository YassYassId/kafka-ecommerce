package com.swe.catalogservice.repository;

import com.swe.catalogservice.entity.Product;
import com.swe.catalogservice.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    @Query("""
    SELECT p
    FROM Product p
    WHERE (:name IS NULL OR LOWER(p.name) LIKE :name)
      AND (:category IS NULL OR p.category = :category)
      AND (:status IS NULL OR p.status = :status)
    """)
    Page<Product> findAllFiltered(
            @Param("name") String name,
            @Param("category") String category,
            @Param("status") ProductStatus status,
            Pageable pageable
    );
}
