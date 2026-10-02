# Published base runner consumer

This independent build expands the published registration and assertion macros,
discovers plain and async suites without invoking their bodies, then executes
synchronous and Future tests. Async fixtures retain their constructor context
and override it explicitly; the plain fixture uses its caller's implicit context.
Every structured event round-trips through the published protocol. Publish both
runner and protocol variants before running it.

From this directory, with compiler 3.9.0, 2.13.18 or 2.12.21:

```sh
direnv exec ../.. sh -c 'exec sbt --server -java-home "$JDK21" -batch -J-Xmx6G -Dizumi.fixture.scala-version=3.9.0 -Dizumi.fixture.version=1.3.0-SNAPSHOT "consumerJVM/run; consumerJS/run; consumerNative/run"'
```

Each target prints
`PUBLISHED_BASE_RUNNER_CONSUMER_OK bodies=4 positions=known context=constructor+override`.
The status ledger records cleaned builds on all three compilers/platforms.
This is a published-library fixture, not the pending framework or transport host.
