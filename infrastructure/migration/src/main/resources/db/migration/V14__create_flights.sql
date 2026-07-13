CREATE TABLE IF NOT EXISTS flights (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                   VARCHAR(8)  NOT NULL UNIQUE,
    alias                  VARCHAR(8),
    sched_departure        TIME        NOT NULL,
    sched_arrival          TIME        NOT NULL,
    origin_airport_id      BIGINT      NOT NULL REFERENCES airports (id),
    destination_airport_id BIGINT      NOT NULL REFERENCES airports (id),
    airline_id             BIGINT      NOT NULL REFERENCES airlines (id),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_flights_origin_airport_id ON flights (origin_airport_id);
CREATE INDEX IF NOT EXISTS idx_flights_destination_airport_id ON flights (destination_airport_id);
CREATE INDEX IF NOT EXISTS idx_flights_airline_id ON flights (airline_id);
