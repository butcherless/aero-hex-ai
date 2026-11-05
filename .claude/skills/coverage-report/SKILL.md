---
name: coverage-report
description: Produces scoverage coverage reports for this project, per module or aggregated across all modules, and fixes the local missing-.coverage-data-dir failure (ExceptionInInitializerError). Use when the user asks for coverage, a coverage report, coverageAggregate, or scoverage numbers.
---

# Coverage report

`coverageEnabled` and `coverageDataDir` (`.coverage-data`) apply to every module. The data dir is
outside `target/` so `sbt clean` never deletes the statement catalog. **Never `rm -rf` an existing
`.coverage-data/`.**

```bash
sbt adapterHttp/test && sbt adapterHttp/coverageReport   # per module
sbt compile
sbt adapterHttp/test                                     # repeat per module with tests
sbt coverageAggregate                                    # → target/scala-<version>/scoverage-report/index.html
```

**Missing-dir gotcha (local only):** if a module's tests run without it recompiling, scoverage never
writes its `.coverage-data/` dir, and the run fails with `ExceptionInInitializerError`. Create the
dirs first:

```bash
for m in $(sbt -batch -no-colors printCoverageProjects | grep -v '^\['); do mkdir -p "$m/.coverage-data/scoverage-data"; done
```
