# Plan: Business Analysis Documentation for aero-hex-ai

> **Purpose:** This file is a task plan to be handed to Claude Code CLI inside the
> `aero-hex-ai` repository. It instructs the creation of 3 lightweight analysis
> documents — a **static** business picture (glossary + domain model + rules), a
> **dynamic** use case catalog, and an **ADR** linking both to the hexagonal
> implementation.
>
> **Repo:** https://github.com/butcherless/aero-hex-ai
> **Approach:** DDD Ubiquitous Language + Domain Model (static) → ICONIX-style Use
> Case text (dynamic) → ADR (traceability to code)

---

## How to use this file

Copy this file into the repo root (e.g. `docs/PLAN.md`) and run:

```bash
claude
> Read docs/PLAN.md and execute it step by step. Ask me before overwriting any existing file.
```

Or paste the relevant section directly into a Claude Code prompt.

---

## Repo-specific notes (adapted from the generic plan above)

The generic instructions above were written before inspecting this repo. Actual structure:

- **Package root:** `domain/src/main/scala/dev/cmartin/aerohex/domain/` (not `domain/src/main/scala/domain/`).
  Subpackages: `model/`, `port/in/`, `port/out/`, `service/`, `error/`.
- **Domain entities/VOs found (8, not 4):** `Country`, `Airport`, `Airline`, `Route`, `Aircraft`, `Flight`,
  `FlightInstance`, `OutboxEvent` — all in `domain/model/`.
- **`application/service/`** implements only some `port/in` use cases so far: Country (Create/Update/Delete/Find),
  Airport (Create/Update/Find/FindByCountry), Route (Create), Aircraft/Airline/Flight/FlightInstance (Find only —
  read stubs). Cross-reference against the REST API table in `CLAUDE.md` (`## REST API`), which marks Airlines,
  Aircraft, Flights, Flight Instances, and Routes-POST as **stub** (not wired end-to-end at the HTTP layer even
  where an application service exists).
- **Persistence policy:** only `Country` and `Airport` are backed by real persistence (Quill, wired). Everything
  else is an in-memory stub. This matters for Task 2's "Postconditions" — a use case's persistence may not be
  durable yet, which should be called out rather than assumed.
- Root-level empty directories `aircraft/`, `airline/`, `country/`, `flight/`, `journey/`, `persistence/` are stray
  leftovers, not SBT modules — ignore them; the real module list is in `CLAUDE.md`'s dependency graph.

These corrections apply to all three tasks below; the section text itself is left as originally drafted for
traceability to the source plan.

---

## Target output structure

```
aero-hex-ai/
└── docs/
    └── analysis/
        ├── 01-domain-model.md          # STATIC — glossary, entities, relationships, rules
        ├── 02-use-cases.md             # DYNAMIC — use case catalog, flows
        └── 03-adr-analysis-to-code.md  # traceability doc → hexagonal modules
```

---

## Task 1 — Generate `docs/analysis/01-domain-model.md` (STATIC)

**Instruction to Claude Code:**

> Inspect the `domain/` module of this repository (model classes, value objects,
> ports). Based on the existing entities (`Country`, `Airport`, `Airline`, `Route`)
> and any others found in `domain/src/main/scala`, generate a business analysis
> document with the following sections. Do not invent business rules that
> contradict the code — extract constraints from validation logic, smart
> constructors, and `require`/`Either` checks found in the domain model. Where a
> rule is not explicit in code, mark it as `[ASSUMPTION]` for the team to confirm.

**Required sections in the generated file:**

```markdown
# aero-hex-ai — Domain Model (Static View)

## 1. Ubiquitous Language / Glossary
| Term | Definition |
|---|---|
| Airport | ... |
| Route | ... |
| IATA Code | ... |
(extract every noun used as a domain concept, one row each)

## 2. Bounded Context
- Name of the context (e.g. "Aviation Network")
- What is explicitly OUT of scope (e.g. ticketing, pricing, scheduling)

## 3. Conceptual Domain Model
- A Mermaid class diagram (entities + relationships + cardinalities only,
  NO methods, NO types beyond identifying the attribute's role)
- Example shape:
  \`\`\`mermaid
  classDiagram
    Country "1" --> "many" Airport : contains
    Airport "1" --> "many" Route : origin of
    Airport "1" --> "many" Route : destination of
    Airline "1" --> "many" Route : operates
  \`\`\`

## 4. Entities and Value Objects
For each domain type found in code, document:
- Name, kind (Entity / Value Object / Aggregate Root)
- Identity (if Entity)
- Attributes and their invariants
- Which module/file it lives in (traceability)

## 5. Business Rules
Numbered, testable rules (BR-01, BR-02...), each one traced to where it's
enforced in code (or marked [ASSUMPTION] / [MISSING] if not yet enforced).

## 6. Constraints
Format, encoding, and external standard constraints (ISO codes, IATA codes,
numeric ranges, etc.)

## 7. Open Questions
Anything ambiguous that should be clarified with a domain expert before
building further use cases.
```

---

## Task 2 — Generate `docs/analysis/02-use-cases.md` (DYNAMIC)

**Instruction to Claude Code:**

> Using `docs/analysis/01-domain-model.md` as the single source of truth for
> entities and business rules, inspect `application/` and `port.in` /
> `port.out` interfaces to identify existing and missing use cases. For every
> use case, reference the exact Business Rule IDs (BR-xx) from Task 1 instead
> of restating them. Do not introduce new business concepts not present in the
> domain model — if a use case requires one, flag it under "Gaps" instead of
> inventing it.

**Required sections in the generated file:**

```markdown
# aero-hex-ai — Use Case Catalog (Dynamic View)

## 1. Actors
List of actors (human or system) that trigger use cases.

## 2. Use Case Index
| ID | Name | Primary Actor | Status |
|---|---|---|---|
| UC-01 | Create Route | Operations Manager | Implemented / Planned |
(one row per use case, cross-reference to application services if implemented)

## 3. Use Case Detail (one block per use case)
### UC-01: Create Route
- **Primary Actor:**
- **Preconditions:** (reference entities from domain model doc)
- **Postconditions:** (include domain events published, if any)
- **Main Flow:** numbered steps
- **Alternative Flows:** numbered, each tied to a Business Rule ID (e.g. "2a.
  Origin equals destination → violates BR-01")
- **Related Ports:** `port.in.CreateRouteUseCase`, `port.out.RouteRepository`
- **Related Domain Events:** e.g. `RouteCreated`

## 4. Traceability Matrix
| Use Case | Business Rules Enforced | Domain Entities Involved | Application Service |
|---|---|---|---|

## 5. Gaps
Use cases implied by the domain model but not yet implemented, or use cases
that require domain concepts not yet modeled.
```

---

## Task 3 — Generate `docs/analysis/03-adr-analysis-to-code.md` (TRACEABILITY)

**Instruction to Claude Code:**

> Write a short Architecture Decision Record explaining how the two analysis
> documents map onto the hexagonal module structure of this repository. Keep
> it concise — this is a traceability index, not a new design document.

**Required sections in the generated file:**

```markdown
# ADR: Business Analysis → Hexagonal Architecture Mapping

## Status
Accepted

## Context
This project uses a 2-document analysis approach before implementation:
a static Domain Model (docs/analysis/01-domain-model.md) and a dynamic
Use Case Catalog (docs/analysis/02-use-cases.md), following a lightweight
DDD + ICONIX-inspired process appropriate for this project's size.

## Decision
| Analysis Artifact | Maps to Module |
|---|---|
| Entities / Value Objects (Doc 1, §4) | `domain/model/` |
| Business Rules (Doc 1, §5) | `domain/service/` (validators) or smart constructors in `domain/model/` |
| Use Cases (Doc 2, §3) | `port.in/` (interfaces) + `application/service/` (implementations) |
| Domain Events (Doc 2, §3) | `domain/model/event/` |
| Repositories implied by Use Cases | `port.out/` (interfaces) + `infrastructure/*` (adapters) |

## Consequences
- Any new business rule must first be added to Doc 1 before being coded.
- Any new use case must first be added to Doc 2, referencing existing BR-xx
  IDs, before creating a new `application` service.
- Docs 1 and 2 are living documents — regenerate/update them when the domain
  model or application services change materially.

## Review Cadence
Revisit this mapping whenever a new bounded context or aggregate is
introduced.
```

---

## Execution order (for Claude Code CLI)

Run sequentially, reviewing each output before moving to the next:

```bash
# Step 1 — static picture first (source of truth)
claude "Execute Task 1 from docs/PLAN.md: generate docs/analysis/01-domain-model.md"

# Step 2 — dynamic flows, built on top of Step 1
claude "Execute Task 2 from docs/PLAN.md: generate docs/analysis/02-use-cases.md"

# Step 3 — traceability index
claude "Execute Task 3 from docs/PLAN.md: generate docs/analysis/03-adr-analysis-to-code.md"
```

## Acceptance criteria

- [ ] Doc 1 contains no business rule that contradicts existing domain code
- [ ] Doc 1 marks any inferred/unconfirmed rule as `[ASSUMPTION]`
- [ ] Doc 2 references Doc 1's BR-xx IDs instead of restating rules
- [ ] Doc 2 flags any use case requiring an undefined domain concept under "Gaps"
- [ ] Doc 3 correctly maps each artifact to an existing module path in the repo
- [ ] All 3 docs live under `docs/analysis/` and are committed together
