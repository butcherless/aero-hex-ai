CREATE INDEX idx_airlines_name_trgm ON airlines USING GIN (name gin_trgm_ops);
