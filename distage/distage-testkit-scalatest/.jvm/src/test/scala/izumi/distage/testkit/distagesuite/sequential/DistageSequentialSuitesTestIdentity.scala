package izumi.distage.testkit.distagesuite.sequential

// JVM-only Identity tests — formerly used `DistageSequentialSuitesTest[Identity]` with
// `IO1[Identity].maybeSuspend` (the unlawful Identity QuasiIO instance). The bifunctor
// equivalent for Identity would need to route through `IdentityBifunctorized` which has
// `IO2[IdentityBifunctorized]` available, but `DistageSequentialSuitesTest[F[+_, +_]]`
// requires `Temporal2[F]` for the sleep operation. `Temporal2[IdentityBifunctorized]` is
// not provided (MiniBIO has no scheduler — Temporal2 sleep would have nothing to wait on).
// Adding a synthetic blocking-Temporal2 instance is out of scope.
