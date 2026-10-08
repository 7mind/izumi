# Published compiler-macro consumer

This separate build resolves locally published izumi artifacts, compiles an
application first, then expands `PlanCheckMaterializer` for a valid graph and a
missing dependency with `onlyWarn`, and materializes the consumer's `ScalaRelease`.
The warning case exercises the compiler-context reference in the plan checker.

After the repository's JVM `publishLocal`, run from this directory:

```sh
direnv exec ../.. sbt --server -java-home "$JDK21" -batch \
  -Dizumi.fixture.scala-version=3.9.0 -Dizumi.fixture.version=1.3.0-SNAPSHOT \
  'checks/runMain izumi.fixtures.compiler.CompilerConsumer 9 0'
```

Use each required stable Scala 3 consumer lane, with its matching minor/patch
arguments. No dependency on the repository's SBT projects is present.
