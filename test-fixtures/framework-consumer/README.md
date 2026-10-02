# Published JVM framework consumer

This independent build uses the published runner and its public test-interface
framework under SBT 1.13.0 and 2.0.9. Five no-argument suites have three tests
each, with the same display paths in every suite. Every body creates its own
execution record with `CREATE_NEW`; the verifier compares the exact record set
with the host's JUnit suite and test identities. Repeated execution starts with
fresh suite counters.

The matrix covers default scheduling, two explicit suites, repeated requests,
serial scheduling, a wildcard, a host task limit of one, and forked partial and
complete groups. It registers only the target framework, with explicit build,
target and catalogue arguments. It does not link repository source directories.

After publishing runner, protocol and assertions variants, run from the
repository root:

```sh
python3 test-fixtures/framework-consumer/verify-matrix.py \
  --artifact-version 1.3.0-SNAPSHOT \
  --sbt-version 1.13.0 2.0.9 \
  --scala-version 3.9.0 2.13.18 2.12.21 \
  --evidence-dir target/native-testkit-evidence
```

These are Behavioral-Active, Effectual, Good-Communication checks against local
host processes. The bootstrap SDK fixtures are Blackbox-Group checks. Plugin
invalidation, configuration selection, application streaming and DI sharing
remain separate integration requirements tracked in the status ledger.
