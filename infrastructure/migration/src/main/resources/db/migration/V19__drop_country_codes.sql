-- ISO 3166-1 alpha-2 membership checking moved into the domain layer
-- (CountryCode.isRecognized/validateIso, a pure in-memory Set) -- see V12's original
-- comment for why this table existed. Its only consumer (CountryRepository.validateCode)
-- is gone, so the table itself is retired.
DROP TABLE IF EXISTS country_codes;
