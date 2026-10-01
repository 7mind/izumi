# Distage testkit experiments

Keep the experimental sources, build definitions, drivers, closure inventory, and
reports in Git. Reports record the methodology, observed results, and limits of
each claim. Captured outputs in `logs/` and `evidence/` are ignored local files:
compiler logs, execution ledgers, parsed JSON results, XML snapshots, and downloaded
metadata. Existing captures remain available locally; reruns produce new outputs.
Generated builds, binaries, and compiler caches are also ignored.

| Project | Methodology and results | Rerun |
| --- | --- | --- |
| JVM SBT | [Report](sbt/REPORT.md) | Run `python3 verify.py` from `sbt/` with Java and SBT on PATH |
| Portability | [Report](portability/REPORT.md) | Generate the Native fixture with `generate-fixture.py`, then build and run it with `-Dspike.native05=true` for the second-round Native run; follow the report for separate producer/consumer and repository compiler experiments |
| JS/Native transport | [Report](transport/REPORT.md) | Run `python3 verify.py` from `transport/` with Java, SBT, Node, and the Native toolchain (`TRANSPORT_LLVM_PATH`) on PATH |

The JVM driver creates its output directory, checks actual body identities and
fresh JUnit reports, and restores the fixture inputs it changes even if a case
fails. Individual case scripts deliberately change inputs; use the complete
driver for a run that restores them. Repository compiler experiments use an
isolated worktree because their configuration script rewrites its build definition.

Per-project reports specify the compiler/plugin versions, toolchain setup,
commands, expected counts, and unmet acceptance gates. Paths to captured outputs
refer to optional local files and are not dependencies of the experimental builds.
