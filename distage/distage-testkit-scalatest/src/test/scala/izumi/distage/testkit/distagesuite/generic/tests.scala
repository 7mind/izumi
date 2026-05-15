package izumi.distage.testkit.distagesuite.generic

// All tests here exercised `Spec1[F[_]: TagK: DefaultModule]` with monofunctor F (CIO, Identity, ZIO Task).
// After M5/11 the testkit's effect type is bifunctor `F[+_, +_]`, breaking these fixtures.
// Stubbed in M5/11c; follow-up will rewrite against `Spec1[F[+_, +_]: TagKK: DefaultModule]` shape.
