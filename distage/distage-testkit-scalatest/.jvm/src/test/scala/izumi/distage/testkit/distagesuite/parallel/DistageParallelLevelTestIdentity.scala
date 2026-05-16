package izumi.distage.testkit.distagesuite.parallel

// JVM-only Identity tests — same constraint as `DistageSequentialSuitesTestIdentity.scala`:
// `Temporal2[IdentityBifunctorized]` is not provided (MiniBIO has no scheduler), so the
// parallel-level test cannot be run for Identity in the bifunctor world. The ZIO equivalents
// in `DistageParallelLevelTest.scala` exercise the same parallelism invariants.
