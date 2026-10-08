# Published assertion consumer

This independent build expands macros from the locally published assertion
artifacts and executes plain/unary forms on JVM, JS, and Native, and the BIO
binary form on JVM and JS. It shares no
project class directories with the producer build. Set the required properties
to the compiler lane and the actual locally published izumi version.

From this directory:

```sh
direnv exec ../.. sh -c 'exec sbt --server -java-home "$JDK21" -batch -J-Xmx6G -Dizumi.fixture.scala-version=3.9.0 -Dizumi.fixture.version=1.3.0-SNAPSHOT "consumerJVM/runMain izumi.fixtures.assertions.PublishedAssertionConsumer; consumerJS/run; consumerNative/run; bioConsumerJVM/runMain izumi.fixtures.assertions.PublishedBIOAssertionConsumer; bioConsumerJS/run"'
```

Each plain/unary target must print `PUBLISHED_ASSERTION_CONSUMER_OK`; each BIO
target must print `PUBLISHED_BIO_ASSERTION_CONSUMER_OK`. The ZIO/reflect/time pins
match the producer's supported runtime versions. Run this command for
every supported Scala 3 consumer lane after publishing on the producer compiler;
the status ledger records the artifact version and compiler lanes actually run.
