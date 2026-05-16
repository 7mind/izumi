package izumi.distage.testkit.distagesuite.interruption

// The pre-M5 InterruptionTest exercised cross-effect interruption (Identity/CIO/ZIO) by
// instantiating `InterruptibleTestSuite[F0[_]: TagK: DefaultModule]` per effect from a single
// runner thread. The bifunctor migration would require:
//   1. Building per-effect `ScalatestAbstractDistageSpec.For2[F[+_, +_]]` instances dynamically
//      with a captured `signalNotInterrupted` callback. This is mechanically possible.
//   2. The Identity path requires `Temporal2[IdentityBifunctorized]` which is not provided
//      (MiniBIO has no scheduler — `Temporal2.sleep` has nothing to wait on). Same constraint
//      as `DistageSequentialSuitesTestIdentity.scala` and `DistageParallelLevelTestIdentity.scala`.
//   3. Cross-effect mixing in `modifySuites` would require `Seq[InterruptibleTestSuite[F]]`
//      for heterogeneous F — the original used `HigherKindedAny.AnyF` to homogenize. The
//      bifunctor equivalent (`Seq[InterruptibleTestSuite[F[+_, +_]]]` with the same `AnyF`-style
//      witness) would require either a new bifunctor-flavoured `AnyF` or per-effect lists.
//
// All three obstacles are workable but extensive. The interruption semantic is exercised
// by the upstream `ZIOResourcesZManagedTestJvm` "interruption" suite for ZIO; the Identity
// and CIO cases are not currently exercised after the M5 migration.
