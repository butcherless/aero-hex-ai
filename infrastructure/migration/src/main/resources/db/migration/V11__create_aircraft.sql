CREATE TABLE IF NOT EXISTS aircraft (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    registration VARCHAR(10)  NOT NULL UNIQUE,
    type_code    VARCHAR(10)  NOT NULL,
    description  VARCHAR(200) NOT NULL,
    airline_id   BIGINT       NOT NULL REFERENCES airlines (id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_aircraft_airline_id ON aircraft (airline_id);
