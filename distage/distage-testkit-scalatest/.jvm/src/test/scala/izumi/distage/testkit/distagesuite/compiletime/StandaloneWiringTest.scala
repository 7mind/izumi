package izumi.distage.testkit.distagesuite.compiletime

// Pre-existing compile-time wiring mismatch: StaticTestMain's makeRole binding wraps the role
// constructor in `G.pure` where G = `IdentityBifunctorized`, but the injector type for this
// role is `Bifunctorized[cats.effect.IO, +_, +_]`. The planner reports
//   "injector uses effect Bifunctorized[IO, +_, +_] but binding uses incompatible effect IdentityBifunctorized"
// at `SpecWiring.checkAgainAtRuntime()`. The mismatch comes from StaticTestMain.scala:24's
// generic `staticTestMainPlugin[F, G]` plumbing and predates this un-stub work; un-stubbing
// here would require fixing StaticTestMain (out of scope for M5-fix4).
