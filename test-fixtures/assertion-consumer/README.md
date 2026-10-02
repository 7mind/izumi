# Published assertion consumer

This independent build expands macros from the locally published assertion
artifact and executes the generated code on JVM, JS, and Native. It shares no
project class directories with the producer build. Set the required properties
to the compiler lane and the actual locally published izumi version.

From this directory:

```sh
direnv exec ../.. sh -c 'exec sbt --server -java-home "$JDK21" -batch -J-Xmx6G -Dizumi.fixture.scala-version=3.9.0 -Dizumi.fixture.version=1.3.0-SNAPSHOT "consumerJVM/runMain izumi.fixtures.assertions.PublishedAssertionConsumer; consumerJS/run; consumerNative/run"'
```

Each target must print `PUBLISHED_ASSERTION_CONSUMER_OK`. Run this command for
every supported Scala 3 consumer lane after publishing on the producer compiler;
the status ledger records the artifact version and compiler lanes actually run.
