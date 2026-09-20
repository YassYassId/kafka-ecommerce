package com.swe.catalogservice.repository;

import com.swe.catalogservice.entity.Product;
import com.swe.catalogservice.entity.ProductStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("should save and retrieve product with auto-generated UUID and timestamps")
    void shouldSaveAndRetrieveProduct() {
        // Arrange
        String uniqueSku = "SKU-REPO-" + UUID.randomUUID().toString().substring(0, 8);
        Product product = Product.builder()
                .sku(uniqueSku)
                .name("Mechanical Keyboard")
                .description("RGB mechanical gaming keyboard")
                .category("Peripherals")
                .price(new BigDecimal("129.99"))
                .currency("USD")
                .status(ProductStatus.ACTIVE)
                .build();

        // Act
        Product savedProduct = productRepository.saveAndFlush(product);

        // Assert
        assertThat(savedProduct.getId()).isNotNull();
        assertThat(savedProduct.getCreatedAt()).isNotNull();
        assertThat(savedProduct.getUpdatedAt()).isNotNull();
        assertThat(savedProduct.getSku()).isEqualTo(uniqueSku);
        assertThat(savedProduct.getName()).isEqualTo("Mechanical Keyboard");
        assertThat(savedProduct.getDescription()).isEqualTo("RGB mechanical gaming keyboard");
        assertThat(savedProduct.getCategory()).isEqualTo("Peripherals");
        assertThat(savedProduct.getPrice()).isEqualByComparingTo("129.99");
        assertThat(savedProduct.getCurrency()).isEqualTo("USD");
        assertThat(savedProduct.getStatus()).isEqualTo(ProductStatus.ACTIVE);

        entityManager.clear();

        Optional<Product> foundProduct = productRepository.findById(savedProduct.getId());
        assertThat(foundProduct).isPresent();
        assertThat(foundProduct.get().getSku()).isEqualTo(uniqueSku);
    }

    @Test
    @DisplayName("should find product by SKU when exists")
    void shouldFindProductBySku() {
        // Arrange
        String uniqueSku = "SKU-FIND-" + UUID.randomUUID().toString().substring(0, 8);
        Product product = Product.builder()
                .sku(uniqueSku)
                .name("USB-C Hub")
                .category("Accessories")
                .price(new BigDecimal("35.50"))
                .currency("USD")
                .status(ProductStatus.ACTIVE)
                .build();

        productRepository.saveAndFlush(product);
        entityManager.clear();

        // Act & Assert
        Optional<Product> found = productRepository.findBySku(uniqueSku);
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("USB-C Hub");

        Optional<Product> notFound = productRepository.findBySku("NON-EXISTENT-SKU");
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("should return true for existsBySku when product exists, and false otherwise")
    void shouldCheckExistsBySku() {
        // Arrange
        String uniqueSku = "SKU-EXISTS-" + UUID.randomUUID().toString().substring(0, 8);
        Product product = Product.builder()
                .sku(uniqueSku)
                .name("Webcam 1080p")
                .category("Peripherals")
                .price(new BigDecimal("79.00"))
                .currency("USD")
                .status(ProductStatus.ACTIVE)
                .build();

        productRepository.saveAndFlush(product);
        entityManager.clear();

        // Act & Assert
        assertThat(productRepository.existsBySku(uniqueSku)).isTrue();
        assertThat(productRepository.existsBySku("DOES-NOT-EXIST")).isFalse();
    }

    @Test
    @DisplayName("should throw DataIntegrityViolationException when inserting duplicate SKU")
    void shouldEnforceUniqueSkuConstraint() {
        // Arrange
        String duplicateSku = "SKU-DUP-" + UUID.randomUUID().toString().substring(0, 8);
        Product product1 = Product.builder()
                .sku(duplicateSku)
                .name("Product 1")
                .category("Category 1")
                .price(new BigDecimal("10.00"))
                .currency("USD")
                .status(ProductStatus.ACTIVE)
                .build();

        Product product2 = Product.builder()
                .sku(duplicateSku)
                .name("Product 2")
                .category("Category 2")
                .price(new BigDecimal("20.00"))
                .currency("USD")
                .status(ProductStatus.ACTIVE)
                .build();

        productRepository.saveAndFlush(product1);

        // Act & Assert
        assertThatThrownBy(() -> {
            productRepository.saveAndFlush(product2);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("should filter products by partial case-insensitive name")
    void shouldFindAllFilteredByName() {
        // Arrange
        String prefix = UUID.randomUUID().toString().substring(0, 6);
        Product p1 = Product.builder()
                .sku("SKU-" + prefix + "-1")
                .name("Pro Gaming Laptop 15")
                .category("Laptops")
                .price(new BigDecimal("1500.00"))
                .currency("USD")
                .status(ProductStatus.ACTIVE)
                .build();

        Product p2 = Product.builder()
                .sku("SKU-" + prefix + "-2")
                .name("Office Keyboard")
                .category("Accessories")
                .price(new BigDecimal("50.00"))
                .currency("USD")
                .status(ProductStatus.ACTIVE)
                .build();

        productRepository.saveAndFlush(p1);
        productRepository.saveAndFlush(p2);
        entityManager.clear();

        // Act
        Page<Product> result = productRepository.findAllFiltered(
                "%gaming laptop%",
                null,
                null,
                PageRequest.of(0, 10)
        );

        // Assert
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getName()).isEqualTo("Pro Gaming Laptop 15");
    }

    @Test
    @DisplayName("should filter products by category and status")
    void shouldFindAllFilteredByCategoryAndStatus() {
        // Arrange
        String prefix = UUID.randomUUID().toString().substring(0, 6);
        String category = "CAT-" + prefix;

        Product activeProduct = Product.builder()
                .sku("SKU-" + prefix + "-A")
                .name("Active Item")
                .category(category)
                .price(new BigDecimal("20.00"))
                .currency("USD")
                .status(ProductStatus.ACTIVE)
                .build();

        Product retiredProduct = Product.builder()
                .sku("SKU-" + prefix + "-D")
                .name("Retired Item")
                .category(category)
                .price(new BigDecimal("15.00"))
                .currency("USD")
                .status(ProductStatus.RETIRED)
                .build();

        productRepository.saveAndFlush(activeProduct);
        productRepository.saveAndFlush(retiredProduct);
        entityManager.clear();

        // Act
        Page<Product> activeResult = productRepository.findAllFiltered(
                null,
                category,
                ProductStatus.ACTIVE,
                PageRequest.of(0, 10)
        );

        // Assert
        assertThat(activeResult.getContent()).hasSize(1);
        assertThat(activeResult.getContent().getFirst().getSku()).isEqualTo("SKU-" + prefix + "-A");

        Page<Product> retiredResult = productRepository.findAllFiltered(
                null,
                category,
                ProductStatus.RETIRED,
                PageRequest.of(0, 10)
        );
        assertThat(retiredResult.getContent()).hasSize(1);
        assertThat(retiredResult.getContent().getFirst().getSku()).isEqualTo("SKU-" + prefix + "-D");
    }
}
