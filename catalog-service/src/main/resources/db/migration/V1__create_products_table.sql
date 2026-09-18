CREATE TABLE products (
                          id UUID PRIMARY KEY,

                          sku VARCHAR(100) NOT NULL,
                          name VARCHAR(255) NOT NULL,
                          description TEXT,
                          category VARCHAR(100) NOT NULL,

                          price NUMERIC(19, 2) NOT NULL,
                          currency VARCHAR(3) NOT NULL,

                          status VARCHAR(30) NOT NULL,

                          created_at TIMESTAMPTZ NOT NULL,
                          updated_at TIMESTAMPTZ NOT NULL,

                          CONSTRAINT uk_products_sku
                              UNIQUE (sku),

                          CONSTRAINT chk_products_price
                              CHECK (price >= 0),

                          CONSTRAINT chk_products_status
                              CHECK (status IN ('ACTIVE', 'RETIRED')),

                          CONSTRAINT chk_products_currency
                              CHECK (char_length(currency) = 3)
);

CREATE INDEX idx_products_category
    ON products(category);

CREATE INDEX idx_products_status
    ON products(status);