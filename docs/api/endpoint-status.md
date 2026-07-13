# REST API

**Code-first OpenAPI.** Tapir endpoint definitions are the single source of truth — types,
validators, descriptions, and examples are declared in Scala. `OpenApiGenerator` (in
`bootstrap/`) calls Tapir's `OpenAPIDocsInterpreter` and writes the spec to stdout as YAML.
Running the fat JAR with `java -jar` executes the generator; `java -cp` runs the server.
Never maintain a hand-written spec file — always regenerate from code.

Swagger UI: `http://localhost:8080/docs`

| Resource | Method | Path | Status |
|---|---|---|---|
| Countries | GET | `/api/v1/countries` (optional `name` filter, ≥3 chars) | ✓ implemented |
| Countries | POST | `/api/v1/countries` | ✓ implemented |
| Countries | GET | `/api/v1/countries/{code}` | ✓ implemented |
| Countries | PUT | `/api/v1/countries/{code}` | ✓ implemented |
| Countries | DELETE | `/api/v1/countries/{code}` | ✓ implemented |
| Airports | GET | `/api/v1/airports` | ✓ implemented |
| Airports | GET | `/api/v1/airports/search` (name filter, ≥3 chars) | ✓ implemented |
| Airports | GET | `/api/v1/airports/{iata}` | ✓ implemented |
| Airports | GET | `/api/v1/airports/{iata}/country` | ✓ implemented |
| Airports | POST | `/api/v1/airports` | ✓ implemented |
| Airports | PUT | `/api/v1/airports/{iata}` | ✓ implemented |
| Airports | DELETE | `/api/v1/airports/{iata}` | ✓ implemented |
| Airports | GET | `/api/v1/countries/{code}/airports` | ✓ implemented |
| Airlines | GET | `/api/v1/airlines` | ✓ implemented |
| Airlines | GET | `/api/v1/airlines/{icao}` | ✓ implemented |
| Airlines | POST | `/api/v1/airlines` | ✓ implemented |
| Airlines | PUT | `/api/v1/airlines/{icao}` | ✓ implemented |
| Airlines | DELETE | `/api/v1/airlines/{icao}` | ✓ implemented |
| Airlines | GET | `/api/v1/countries/{code}/airlines` | ✓ implemented |
| Airlines | GET | `/api/v1/routes/{origin}/{destination}/airlines` | stub |
| Aircraft | GET | `/api/v1/aircraft` | ✓ implemented |
| Aircraft | GET | `/api/v1/aircraft/{registration}` | ✓ implemented |
| Aircraft | POST | `/api/v1/aircraft` | ✓ implemented |
| Aircraft | PUT | `/api/v1/aircraft/{registration}` | ✓ implemented |
| Aircraft | DELETE | `/api/v1/aircraft/{registration}` | ✓ implemented |
| Flights | GET | `/api/v1/flights` | ✓ implemented |
| Flights | GET | `/api/v1/flights/{code}` | ✓ implemented |
| Flights | POST | `/api/v1/flights` | ✓ implemented |
| Flights | PUT | `/api/v1/flights/{code}` | ✓ implemented |
| Flights | DELETE | `/api/v1/flights/{code}` | ✓ implemented |
| Flight Instances | GET | `/api/v1/flight-instances` | stub |
| Flight Instances | GET | `/api/v1/flight-instances/{id}` | stub |
| Routes | POST | `/api/v1/routes` | stub |
| Routes | POST | `/api/v1/routes/{origin}/{destination}/airlines/{icao}` | stub |
| Routes | DELETE | `/api/v1/routes/{origin}/{destination}/airlines/{icao}` | stub |
| Routes | GET | `/api/v1/airlines/{icao}/routes` | stub |
