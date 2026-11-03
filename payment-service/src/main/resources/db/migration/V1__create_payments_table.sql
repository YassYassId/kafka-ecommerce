CREATE TABLE payments (
                          id UUID PRIMARY KEY,

                          order_id UUID NOT NULL,

                          business_key VARCHAR(255) NOT NULL,

                          amount NUMERIC(19, 2) NOT NULL,

                          currency VARCHAR(3) NOT NULL,

                          status VARCHAR(30) NOT NULL,

                          provider_reference VARCHAR(255),

                          failure_code VARCHAR(100),

                          created_at TIMESTAMP WITH TIME ZONE NOT NULL,

                          updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

                          CONSTRAINT uq_payments_business_key
                              UNIQUE (business_key),

                          CONSTRAINT chk_payments_amount_positive
                              CHECK (amount > 0)
);