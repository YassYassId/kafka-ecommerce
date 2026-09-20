package com.swe.catalogservice.controller;

import tools.jackson.databind.ObjectMapper;
import com.swe.catalogservice.dto.CreateProductRequest;
import com.swe.catalogservice.dto.ProductResponse;
import com.swe.catalogservice.entity.ProductStatus;
import com.swe.catalogservice.exception.DuplicateSkuException;
import com.swe.catalogservice.exception.GlobalExceptionHandler;
import com.swe.catalogservice.exception.ProductNotFoundException;
import com.swe.catalogservice.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import(GlobalExceptionHandler.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String BASE_URL = "/api/v1/products";

    @Nested
    @DisplayName("POST /api/v1/products")
    class CreateProductTests {

        @Test
        @DisplayName("should return 201 Created and Location header when request is valid")
        void createProduct_WhenValidRequest_ShouldReturn201Created() throws Exception {
            // Arrange
            UUID productId = UUID.randomUUID();
            CreateProductRequest request = new CreateProductRequest(
                    "LAPTOP-001",
                    "Gaming Laptop",
                    "High performance laptop",
                    "Electronics",
                    new BigDecimal("1299.99"),
                    "USD"
            );

            ProductResponse response = new ProductResponse(
                    productId,
                    "LAPTOP-001",
                    "Gaming Laptop",
                    "High performance laptop",
                    "Electronics",
                    new BigDecimal("1299.99"),
                    "USD",
                    ProductStatus.ACTIVE,
                    OffsetDateTime.now(),
                    OffsetDateTime.now()
            );

            when(productService.createProduct(any(CreateProductRequest.class))).thenReturn(response);

            // Act & Assert
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/products/" + productId))
                    .andExpect(jsonPath("$.id").value(productId.toString()))
                    .andExpect(jsonPath("$.sku").value("LAPTOP-001"))
                    .andExpect(jsonPath("$.name").value("Gaming Laptop"))
                    .andExpect(jsonPath("$.description").value("High performance laptop"))
                    .andExpect(jsonPath("$.category").value("Electronics"))
                    .andExpect(jsonPath("$.price").value(1299.99))
                    .andExpect(jsonPath("$.currency").value("USD"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.createdAt").exists())
                    .andExpect(jsonPath("$.updatedAt").exists());

            verify(productService).createProduct(any(CreateProductRequest.class));
        }

        @Test
        @DisplayName("should return 400 Bad Request when sku is blank")
        void createProduct_WhenSkuIsBlank_ShouldReturn400BadRequest() throws Exception {
            // Arrange
            CreateProductRequest request = new CreateProductRequest(
                    "",
                    "Gaming Laptop",
                    "Description",
                    "Electronics",
                    new BigDecimal("99.99"),
                    "USD"
            );

            // Act & Assert
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("sku")));

            verifyNoInteractions(productService);
        }

        @Test
        @DisplayName("should return 400 Bad Request when name is blank")
        void createProduct_WhenNameIsBlank_ShouldReturn400BadRequest() throws Exception {
            // Arrange
            CreateProductRequest request = new CreateProductRequest(
                    "SKU-123",
                    " ",
                    "Description",
                    "Electronics",
                    new BigDecimal("99.99"),
                    "USD"
            );

            // Act & Assert
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("name")));

            verifyNoInteractions(productService);
        }

        @Test
        @DisplayName("should return 400 Bad Request when category is blank")
        void createProduct_WhenCategoryIsBlank_ShouldReturn400BadRequest() throws Exception {
            // Arrange
            CreateProductRequest request = new CreateProductRequest(
                    "SKU-123",
                    "Gaming Laptop",
                    "Description",
                    "",
                    new BigDecimal("99.99"),
                    "USD"
            );

            // Act & Assert
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("category")));

            verifyNoInteractions(productService);
        }

        @Test
        @DisplayName("should return 400 Bad Request when price is null")
        void createProduct_WhenPriceIsNull_ShouldReturn400BadRequest() throws Exception {
            // Arrange
            CreateProductRequest request = new CreateProductRequest(
                    "SKU-123",
                    "Gaming Laptop",
                    "Description",
                    "Electronics",
                    null,
                    "USD"
            );

            // Act & Assert
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("price")));

            verifyNoInteractions(productService);
        }

        @Test
        @DisplayName("should return 400 Bad Request when price is negative")
        void createProduct_WhenPriceIsNegative_ShouldReturn400BadRequest() throws Exception {
            // Arrange
            CreateProductRequest request = new CreateProductRequest(
                    "SKU-123",
                    "Gaming Laptop",
                    "Description",
                    "Electronics",
                    new BigDecimal("-1.00"),
                    "USD"
            );

            // Act & Assert
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("price")));

            verifyNoInteractions(productService);
        }

        @Test
        @DisplayName("should return 400 Bad Request when currency is invalid format")
        void createProduct_WhenCurrencyIsInvalid_ShouldReturn400BadRequest() throws Exception {
            // Arrange
            CreateProductRequest request = new CreateProductRequest(
                    "SKU-123",
                    "Gaming Laptop",
                    "Description",
                    "Electronics",
                    new BigDecimal("99.99"),
                    "us"
            );

            // Act & Assert
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("currency")));

            verifyNoInteractions(productService);
        }

        @Test
        @DisplayName("should return 409 Conflict when SKU already exists")
        void createProduct_WhenDuplicateSkuExceptionThrown_ShouldReturn409Conflict() throws Exception {
            // Arrange
            CreateProductRequest request = new CreateProductRequest(
                    "EXISTING-SKU",
                    "Gaming Laptop",
                    "Description",
                    "Electronics",
                    new BigDecimal("99.99"),
                    "USD"
            );

            when(productService.createProduct(any(CreateProductRequest.class)))
                    .thenThrow(new DuplicateSkuException("EXISTING-SKU"));

            // Act & Assert
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.error").value("Conflict"))
                    .andExpect(jsonPath("$.message").value("Product with SKU 'EXISTING-SKU' already exists"))
                    .andExpect(jsonPath("$.path").value(BASE_URL));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/products/{id}")
    class GetProductTests {

        @Test
        @DisplayName("should return 200 OK and product details when product exists")
        void getProduct_WhenProductExists_ShouldReturn200Ok() throws Exception {
            // Arrange
            UUID productId = UUID.randomUUID();
            ProductResponse response = new ProductResponse(
                    productId,
                    "LAPTOP-001",
                    "Gaming Laptop",
                    "High performance laptop",
                    "Electronics",
                    new BigDecimal("1299.99"),
                    "USD",
                    ProductStatus.ACTIVE,
                    OffsetDateTime.now(),
                    OffsetDateTime.now()
            );

            when(productService.getProduct(productId)).thenReturn(response);

            // Act & Assert
            mockMvc.perform(get(BASE_URL + "/{id}", productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(productId.toString()))
                    .andExpect(jsonPath("$.sku").value("LAPTOP-001"))
                    .andExpect(jsonPath("$.name").value("Gaming Laptop"))
                    .andExpect(jsonPath("$.description").value("High performance laptop"))
                    .andExpect(jsonPath("$.category").value("Electronics"))
                    .andExpect(jsonPath("$.price").value(1299.99))
                    .andExpect(jsonPath("$.currency").value("USD"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.createdAt").exists())
                    .andExpect(jsonPath("$.updatedAt").exists());

            verify(productService).getProduct(productId);
        }

        @Test
        @DisplayName("should return 404 Not Found when product does not exist")
        void getProduct_WhenProductNotFound_ShouldReturn404NotFound() throws Exception {
            // Arrange
            UUID productId = UUID.randomUUID();
            when(productService.getProduct(productId)).thenThrow(new ProductNotFoundException(productId));

            // Act & Assert
            mockMvc.perform(get(BASE_URL + "/{id}", productId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("Product with ID: '" + productId + "' was not found"))
                    .andExpect(jsonPath("$.path").value(BASE_URL + "/" + productId));

            verify(productService).getProduct(productId);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/products")
    class GetProductsTests {

        @Test
        @DisplayName("should return 200 OK with default pagination when no filters are provided")
        void getProducts_WhenNoFilters_ShouldReturn200OkWithDefaultPagination() throws Exception {
            // Arrange
            UUID productId = UUID.randomUUID();
            ProductResponse product = new ProductResponse(
                    productId,
                    "SKU-001",
                    "Desk Lamp",
                    "LED lamp",
                    "Home",
                    new BigDecimal("29.99"),
                    "USD",
                    ProductStatus.ACTIVE,
                    OffsetDateTime.now(),
                    OffsetDateTime.now()
            );

            Page<ProductResponse> page = new PageImpl<>(List.of(product), PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")), 1);

            when(productService.getProducts(isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);

            // Act & Assert
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(productId.toString()))
                    .andExpect(jsonPath("$.content[0].sku").value("SKU-001"))
                    .andExpect(jsonPath("$.content[0].name").value("Desk Lamp"))
                    .andExpect(jsonPath("$.content[0].category").value("Home"))
                    .andExpect(jsonPath("$.content[0].price").value(29.99))
                    .andExpect(jsonPath("$.content[0].currency").value("USD"))
                    .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                    .andExpect(jsonPath("$.totalElements").value(1));

            verify(productService).getProducts(isNull(), isNull(), isNull(), any(Pageable.class));
        }

        @Test
        @DisplayName("should pass query filters and pagination params to service")
        void getProducts_WhenFiltersProvided_ShouldPassToService() throws Exception {
            // Arrange
            Page<ProductResponse> emptyPage = new PageImpl<>(List.of(), PageRequest.of(1, 10), 0);

            when(productService.getProducts(eq("keyboard"), eq("Electronics"), eq(ProductStatus.ACTIVE), any(Pageable.class)))
                    .thenReturn(emptyPage);

            // Act & Assert
            mockMvc.perform(get(BASE_URL)
                            .param("name", "keyboard")
                            .param("category", "Electronics")
                            .param("status", "ACTIVE")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0));

            verify(productService).getProducts(eq("keyboard"), eq("Electronics"), eq(ProductStatus.ACTIVE), any(Pageable.class));
        }
    }
}
