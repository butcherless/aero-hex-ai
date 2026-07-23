ALTER TABLE airlines DROP COLUMN foundation_date;
ALTER TABLE airlines ADD COLUMN alias VARCHAR(100);
ALTER TABLE airlines ADD COLUMN callsign VARCHAR(100);
