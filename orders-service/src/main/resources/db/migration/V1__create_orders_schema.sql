-- =====================================================
-- Orders table
-- =====================================================

CREATE TABLE orders (
                        id UUID PRIMARY KEY,
                        customer_id UUID NOT NULL,
                        status VARCHAR(30) NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

                        CONSTRAINT chk_orders_status
                            CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED'))
);


-- =====================================================
-- Order items table
-- =====================================================

CREATE TABLE order_items (
                             id UUID PRIMARY KEY,
                             order_id UUID NOT NULL,
                             product_id UUID NOT NULL,
                             quantity INTEGER NOT NULL,

                             CONSTRAINT fk_order_items_order
                                 FOREIGN KEY (order_id)
                                     REFERENCES orders(id)
                                     ON DELETE CASCADE,

                             CONSTRAINT chk_order_items_quantity
                                 CHECK (quantity > 0),

                             CONSTRAINT uq_order_product
                                 UNIQUE (order_id, product_id)
);


-- =====================================================
-- Indexes
-- =====================================================

CREATE INDEX idx_orders_customer_id
    ON orders(customer_id);

CREATE INDEX idx_orders_status
    ON orders(status);

CREATE INDEX idx_order_items_order_id
    ON order_items(order_id);