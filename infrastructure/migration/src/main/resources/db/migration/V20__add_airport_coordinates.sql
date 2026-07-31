-- Airport coordinates, needed to compute inter-airport distance (Haversine) for the Route sync
-- job (see plans/masterdata/route-sync.md). Default 0/0 lets existing rows migrate cleanly;
-- real values arrive by re-running the Airport sync once AirportCsvParser starts capturing
-- OurAirports' latitude_deg/longitude_deg columns.
ALTER TABLE airports
    ADD COLUMN latitude  DOUBLE PRECISION NOT NULL DEFAULT 0,
    ADD COLUMN longitude DOUBLE PRECISION NOT NULL DEFAULT 0;
