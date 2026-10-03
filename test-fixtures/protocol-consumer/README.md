# Published protocol consumer

This independent build consumes the locally published protocol on JVM, JS and
Native. Its required compiler and artifact-version properties keep it separate
from producer class directories. From this directory:

```sh
direnv exec ../.. sh -c 'exec sbt --server -java-home "$JDK21" -batch -J-Xmx6G -Dizumi.fixture.scala-version=3.8.4 -Dizumi.fixture.version=1.3.0-SNAPSHOT "consumerJVM/runMain izumi.fixtures.protocol.PublishedProtocolConsumer; consumerJVM/checkClassloaders; consumerJS/run; consumerNative/run"'
```

Each target must print `PUBLISHED_PROTOCOL_CONSUMER_OK schema=4`. The JVM
classloader task must also print
`PROTOCOL_CLASSLOADER_CONSUMER_OK isolated=2 exchange=String`. It constructs two
independent parentless classloaders from the consumer's resolved classpath and
passes only a string frame through their reflective boundary.
The exchanged frames retain assertion diagnostics, separate ordered suppressed
exceptions, explicit field-accessor errors, resolved selections and nested plan
scopes with distinct dependency identities and Planning failures. Schema 3 is rejected.

The producer's Scala 3 artifacts use 3.8.4. Run the consumer with 3.8.4 to verify
the SBT 2 host baseline, with 3.9.0 to verify the testkit baseline, and with the
repository's Scala 2 versions for their corresponding artifacts. The status
ledger records the actual commands and compiler versions.
