---
name: choose-library
description: How this project picks a library for a new capability — ZIO ecosystem first, then the Scala/JDK standard library, then third-party — and how to record the comparison. Use whenever adding a new dependency, choosing between libraries, or asking "which library should we use for X".
---

# Choosing a library for a new capability

Check options in this order, against current docs (Context7 first, per CLAUDE.md's
`## Documentation sources`) rather than memory, and record the comparison (goal, options table,
verdict):

1. ZIO core or an official ZIO-ecosystem library. This project prefers ecosystem fit over maturity
   (`zio-nio` over `better-files`/`os-lib`, `zio-http` over `sttp-client4`).
2. The Scala/JDK standard library, checking `scala.*` as well as `java.*`.
3. Other third-party libraries, only if neither of the above meets CLAUDE.md's
   `## Versioning policy` or the required capability.

Once implemented, collapse the comparison to a one-line decision plus a bare list of rejected
alternatives (see `docs/todo/master-data/analysis.md` §4.2–§4.5).
