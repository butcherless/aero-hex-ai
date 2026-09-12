<!--
Sync Impact Report
==================
Version change: (template, unversioned) → 1.0.0
Rationale: Initial ratification. First concrete constitution derived from the
existing project's CLAUDE.md, README.md, and docs/ analysis material.

Modified principles:
  - [PRINCIPLE_1_NAME] → I. Hexagonal Architecture — Ports & Adapters
  - [PRINCIPLE_2_NAME] → II. Pure Domain in a Ubiquitous Language
  - [PRINCIPLE_3_NAME] → III. Analysis & Design Before Implementation
  - [PRINCIPLE_4_NAME] → IV. Make Illegal States Unrepresentable
  - [PRINCIPLE_5_NAME] → V. Contract-First External Interfaces
  - (added) VI. Layered, Independent Verification
  - (added) VII. Conservative Evolution

Added sections:
  - Architectural Constraints (was [SECTION_2_NAME])
  - Development Workflow (was [SECTION_3_NAME])

Removed sections: none

Deferred / TODO items: none

Follow-up: dependent Spec Kit templates (plan, spec, tasks, checklist) read this
file at runtime and were not modified by this amendment.
-->

# Aviation Hexagonal Constitution

## Core Principles

### I. Hexagonal Architecture — Ports & Adapters

The system MUST be organized as a business core surrounded by interchangeable adapters.

- Dependencies MUST point inward only. An inner layer MUST NOT reference, import, or
  know about any outer layer. The business core MUST NOT depend on any delivery
  mechanism, persistence technology, messaging system, or other infrastructure.
- Every interaction between the core and the outside world MUST cross an explicit
  port: a plain interface owned by the core. This applies to both directions —
  interfaces that drive the core (use cases) and interfaces the core drives
  (repositories, publishers, external services).
- Adapters implement ports and MUST be replaceable without changing the core. Any
  adapter MUST be substitutable by an in-memory or test double for the purpose of
  isolated testing.
- Assembly of the concrete system — choosing which adapter satisfies each port —
  MUST happen in exactly one composition root, not scattered across modules.

**Rationale:** Keeping business logic independent of frameworks and infrastructure
keeps it testable in isolation, comprehensible on its own terms, and free to outlive
any particular technology choice.

### II. Pure Domain in a Ubiquitous Language

The business core MUST be pure and MUST speak the language of the domain.

- Core logic MUST be free of I/O, side effects, and framework imports. Behaviour
  that needs the outside world MUST be expressed as a port and supplied from outside.
- The core MUST be organized by business concept, not by technical role. Each concept
  groups its model, its ports, and its pure domain services together.
- Names in the core MUST come from an agreed glossary grounded in the domain's own
  standard terminology. A single analysis document MUST hold that glossary and the
  conceptual model, and MUST remain the source of truth for concept definitions.
- The core changes for business reasons. A change driven purely by infrastructure or
  tooling MUST NOT require editing core logic.

**Rationale:** A domain model that reads like the business — and only like the
business — can be reviewed by domain experts, ported between technologies, and
trusted as the definition of correct behaviour.

### III. Analysis & Design Before Implementation

Non-trivial change MUST be reasoned about in writing before it is built.

- Any change that introduces a new capability, alters a contract, or migrates
  persistence/state MUST have a short design document first, covering: the goal, the
  decision with its rationale, the alternatives considered and why they were
  rejected, and the surface area affected.
- Business rules MUST be captured as numbered, individually testable statements, each
  traced to where it is enforced. A rule that is intended but not yet enforced MUST
  be marked as such rather than omitted.
- Design and analysis documents MUST be kept after the work ships, as the record of
  *why*. When a later change revises an earlier decision, the existing document MUST
  be updated in place rather than duplicated. Once a decision has shipped, its
  options comparison MUST be collapsed to the decision plus a bare list of rejected
  alternatives.

**Rationale:** The expensive part of a decision is the reasoning, not the typing.
Recording it once, in one place, prevents re-litigation and preserves institutional
memory.

### IV. Make Illegal States Unrepresentable

Invalid data MUST be rejected at the earliest boundary that can judge it, and the
type system MUST carry the guarantee thereafter.

- Domain identifiers and constrained values MUST be distinct types with their
  invariants enforced at construction. Once constructed, a value of that type MUST be
  trustworthy without re-checking.
- Validation MUST be classified and placed accordingly: shape/format checks belong in
  pure construction; existence/referential checks require the outside world and MUST
  sit behind a port; a static reference check MAY live in the core as pure data.
- Whether validation stops at the first failure or accumulates every failure MUST be
  a deliberate choice per use case. Where a caller submits a whole request, the
  response MUST report every violated rule in one round trip.
- A single rule MAY be enforced in more than one layer as defense in depth, but the
  layers MUST agree on the bound, and one layer MUST be named as the source of truth.

**Rationale:** Pushing validation to the boundary and encoding the result in types
means the rest of the system cannot be handed data that violates its assumptions.

### V. Contract-First External Interfaces

The published interface to the outside world MUST be derived from the implementation,
never maintained by hand.

- The interface contract (its schema, its operations, its error shapes) MUST be
  generated from the same definitions that serve requests. A hand-written contract
  artifact that can drift from behaviour is prohibited.
- Interaction shapes that recur across the interface — pagination, error responses,
  identifiers — MUST be uniform, defined once and reused.
- Every operation that exposes a business capability MUST require authenticated
  access. Only explicitly designated infrastructure probes and the authentication
  entry point itself may be unauthenticated.

**Rationale:** A generated contract cannot lie about what the system does. Uniform
shapes and a single auth rule make the interface predictable for its consumers.

### VI. Layered, Independent Verification

Confidence MUST come from several independent checks, each catching a different class
of failure.

- The verification layers, cheapest and narrowest first: fast checks against test
  doubles; checks against real infrastructure in isolation; end-to-end checks against
  a live instance; contract-conformance checks; and reproduction of all of the above
  in continuous integration.
- The default, always-run test suite MUST NOT touch external systems. Tests that
  require real infrastructure MUST be opt-in and MUST be excluded from the default
  aggregate.
- For any given change, the layers relevant to what changed MUST be run; running all
  of them is not required when the change cannot affect them.
- A change is not "done" until the formatting check and a warning-free build both
  pass. Reporting work as complete before that is prohibited.

**Rationale:** No single kind of test gives both fast feedback and high-confidence
guarantees. Layering them, and keeping the fast layer pure, provides both.

### VII. Conservative Evolution

The toolchain and dependency set MUST favour stability and predictability over
novelty.

- The language runtime and platform MUST track long-term-support lines only.
  Non-LTS versions MUST NOT be adopted, even transiently — an unsupported runtime has
  silently broken tooling before.
- New dependencies MUST be chosen in a fixed order of preference: the effect
  ecosystem's own libraries first, then the standard library, then third parties —
  and a third party only when neither of the first two clears the project's stability
  bar or provides the needed capability. The comparison MUST be recorded while the
  decision is open (per Principle III).
- Direct dependencies MUST be stable general-availability releases, save for
  explicitly documented exceptions. Patch and minor upgrades are routine; a major
  upgrade requires migration review and passing verification.
- Transitive dependencies MUST be left to the build's resolution; an override is
  justified only by a known vulnerability or incompatibility.
- The runnable environment MUST be reproducible without manual steps: schema and
  state migrations run automatically and are themselves the source of truth for the
  schema; the development environment MUST be produced by those migrations, not hand
  assembled.

**Rationale:** This is a long-lived demonstration project. Its value is in being a
stable, correct reference, which a churn of bleeding-edge upgrades would undermine.

## Architectural Constraints

- **Dependency direction is absolute.** Shared kernel ← domain ← application and
  infrastructure adapters ← composition root. No arrow points the other way. The
  domain has zero framework dependencies.
- **Infrastructure choices are all-or-nothing.** Where a family of ports has a
  chosen implementation technology, every port in that family MUST use it. Switching
  technology is a single, complete change across all of them, never a partial mix.
- **One composition root.** Exactly one module wires ports to adapters and starts the
  system. Other modules MUST NOT construct concrete adapters.
- **Derived artifacts are never hand-edited.** Generated contracts, generated client
  collections, and migration-produced databases are outputs. They MUST be
  regenerated from their source, not patched.
- **Bounded contexts stay separated.** Concerns outside the core business domain
  (for example, authentication) MUST be modeled as their own context with their own
  model and ports, not folded into a business aggregate.

## Development Workflow

- **Design gate.** Before implementation of a non-trivial change, its design
  document exists and states goal, decision, rejected alternatives, and affected
  files (Principle III).
- **Traceability.** Every new or changed business rule is reflected in the analysis
  documents, with its enforcement point identified, before or with the code that
  enforces it.
- **Verification per change.** The contributor runs the verification layers relevant
  to the change (Principle VI) and reports results honestly — a failing or skipped
  layer is stated as such.
- **Completion bar.** Formatting is applied and the build compiles with zero errors
  and zero warnings before the work is reported complete.
- **Change control.** Actions that are hard to reverse or outward-facing —
  publishing, pushing shared history, deleting or overwriting state — require
  explicit confirmation each time; approval in one context does not carry to the
  next.
- **Documentation upkeep.** When work lands, its design document is updated to the
  shipped reality and its options comparisons are collapsed (Principle III).

## Governance

- This constitution supersedes ad-hoc practice. Where a specific instruction and a
  principle here conflict, the conflict MUST be raised and resolved before
  proceeding, not silently decided.
- **Amendment procedure.** A change to this document requires a written rationale, a
  version bump chosen per the policy below, and an updated Sync Impact Report at the
  top of the file. Dependent templates and workflows that reference the constitution
  MUST be reviewed for consistency as part of the same change.
- **Versioning policy.** Semantic versioning applies to the constitution itself:
  - MAJOR — a principle is removed or redefined in a backward-incompatible way, or
    governance is materially restructured.
  - MINOR — a new principle or section is added, or existing guidance is materially
    expanded.
  - PATCH — clarifications, wording, and non-semantic refinements.
- **Compliance review.** Every change proposal MUST be checked against these
  principles before it is accepted. Added complexity MUST be justified against the
  simpler alternative it displaces. Runtime development guidance that elaborates on
  these principles lives in the project's `CLAUDE.md`.

**Version**: 1.0.0 | **Ratified**: 2026-09-10 | **Last Amended**: 2026-09-10
