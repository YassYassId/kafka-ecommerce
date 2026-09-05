CREATE TABLE inventory_items (
                                 id UUID PRIMARY KEY,
                                 product_id UUID NOT NULL,
                                 available_quantity INTEGER NOT NULL DEFAULT 0,
                                 reserved_quantity INTEGER NOT NULL DEFAULT 0,
                                 version BIGINT NOT NULL DEFAULT 0,

                                 CONSTRAINT uq_inventory_product
                                     UNIQUE (product_id),

                                 CONSTRAINT chk_available_quantity_non_negative
                                     CHECK (available_quantity >= 0),

                                 CONSTRAINT chk_reserved_quantity_non_negative
                                     CHECK (reserved_quantity >= 0)
);

CREATE INDEX idx_inventory_product_id
    ON inventory_items (product_id);