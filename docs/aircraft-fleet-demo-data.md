# Aircraft fleet demo data (OpenSky)

`Aircraft` has no sync module, unlike Country, Airport, Airline and Route (see `master-data-sync`). This is a manual recipe for pulling realistic fleet data from the OpenSky Network and inserting it through the live API. Re-run it by hand when you need more or fresher demo aircraft.

**Source:** `https://s3.opensky-network.org/data-samples/metadata/aircraftDatabase.csv`, a free crowdsourced aircraft registry (~413k rows as of 2026-07-28). OpenSky provides it as-is, with no licence and no freshness or completeness guarantee, so treat counts as a lower bound on a real fleet.

## Steps

1. Download the CSV: `curl -o aircraftDatabase.csv <url>`.
2. Filter rows where `operatoricao` equals the airline's `AirlineIcaoCode` (e.g. `AEA` for Air Europa). The airline must already exist in `airlines`; create it with `POST /api/v1/airlines` if it's newer than OpenFlights' static data (e.g. ITA Airways/`ITY`).
3. Skip rows with a blank `registration`/`typecode`/`model`, or a `registration` over 10 characters (BR-15 in `docs/analysis/01-domain-model.md`).
4. Map columns onto `CreateAircraftRequest`: `registration`→`registration`, `typecode`→`typeCode`, `model`→`description`, plus `airlineIcao`.
5. `POST /api/v1/aircraft` with a bearer token for each row.

## Script

Set `ICAO` and `TOKEN` first:

```bash
python3 -c "
import csv, json
with open('aircraftDatabase.csv', newline='', encoding='utf-8', errors='replace') as f:
    for row in csv.DictReader(f):
        if row['operatoricao'].strip() != '$ICAO':
            continue
        reg, tc, model = row['registration'].strip(), row['typecode'].strip(), row['model'].strip()
        if reg and tc and model and len(reg) <= 10:
            print(json.dumps({'registration': reg, 'typeCode': tc, 'description': model, 'airlineIcao': '$ICAO'}))
" | while IFS= read -r payload; do
  curl -s -X POST http://localhost:8080/api/v1/aircraft \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$payload"
done
```
