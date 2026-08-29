-- =====================================================
-- Inventory items table
-- =====================================================

CREATE TABLE inventory_items (
                                 id UUID PRIMARY KEY,
                                 product_id UUID NOT NULL,
                                 available_quantity INTEGER NOT NULL DEFAULT 0,
                                 reserved_quantity INTEGER NOT NULL DEFAULT 0,
                                 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                                 updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

                                 CONSTRAINT uq_inventory_product
                                     UNIQUE (product_id),

                                 CONSTRAINT chk_inventory_available_quantity
                                     CHECK (available_quantity >= 0),

                                 CONSTRAINT chk_inventory_reserved_quantity
                                     CHECK (reserved_quantity >= 0)
);


-- =====================================================
-- Reservations table
-- =====================================================

CREATE TABLE reservations (
                              id UUID PRIMARY KEY,
                              order_id UUID NOT NULL,
                              status VARCHAR(30) NOT NULL,
                              created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                              updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

                              CONSTRAINT uq_reservation_order
                                  UNIQUE (order_id),

                              CONSTRAINT chk_reservations_status
                                  CHECK (status IN ('RESERVED', 'REJECTED', 'RELEASED'))
);


-- =====================================================
-- Reservation items table
-- =====================================================

CREATE TABLE reservation_items (
                                   id UUID PRIMARY KEY,
                                   reservation_id UUID NOT NULL,
                                   product_id UUID NOT NULL,
                                   quantity INTEGER NOT NULL,

                                   CONSTRAINT fk_reservation_items_reservation
                                       FOREIGN KEY (reservation_id)
                                           REFERENCES reservations(id)
                                           ON DELETE CASCADE,

                                   CONSTRAINT chk_reservation_items_quantity
                                       CHECK (quantity > 0),

                                   CONSTRAINT uq_reservation_product
                                       UNIQUE (reservation_id, product_id)
);


-- =====================================================
-- Indexes
-- =====================================================

CREATE INDEX idx_inventory_product_id
    ON inventory_items(product_id);

CREATE INDEX idx_reservations_status
    ON reservations(status);

CREATE INDEX idx_reservation_items_reservation_id
    ON reservation_items(reservation_id);

CREATE INDEX idx_reservation_items_product_id
    ON reservation_items(product_id);