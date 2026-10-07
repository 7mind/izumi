# Existing-source compatibility fixture

`manifest.json` holds the migrated repository source set by relative path and
content hash, together with its exact pre-migration Git blobs. It also records
inherited concrete-suite candidates. Candidates need actual compiled-class and
selected-outcome bindings; source presence alone does not establish execution.

Run `python3 test-fixtures/source-compatibility/verify.py --capture <new-directory>`
from the repository. The capture records each complete migration diff and checks
the preserved original declarations. Runtime qualification is recorded separately
in the native-testkit status ledger.

Run `python3 test-fixtures/source-compatibility/bind-ci.py --ci <completed-lane-directory> ... --capture <new-directory>`
to bind the held source references to a successful frozen CI capture. The output
records generated project platforms, exact compiler input hashes, class-file
`SourceFile` metadata and raw XML case identities. It keeps excluded source sets,
abstract support types and compiled classes without direct reports visible;
their presence does not establish an independent test execution.

`sources/jvm/IzResourcesTest.scala` changes only the original WordSpec import.
Its historical slash-delimited resource filename is plain data. Runtime checks
supply fixture-owned properties in a separate JAR at that filename, with no
ScalaTest dependency or implementation, and run all seven original test bodies.

`sources/shared/generic/tests.scala` preserves the eligible declarations and
companion support from the mixed original source, omitting unrelated declarations
that use additional ScalaTest facilities. Declaration spans remain byte-identical;
imports select the new runner. This is an explicit extraction, not an imports-only
claim about the whole mixed compilation unit. Its original project supported JVM
and JS. The repository's added Native port separately guards unavailable package
scanning while preserving the test bodies.
