package izumi.distage.testkit.distagesuite.generic

// Concrete test classes live alongside in tests.scala. This file used to provide the
// per-effect specialisations of the pre-M5 monofunctor `DistageTestExampleBase[F[_]]` /
// `OverloadingTest[F[_]]` / `ActivationTest[F[_]]` / `ForcedRootTest[F[_]]` abstract bases.
// After M5 those generic abstractions cannot be re-expressed without a `*1` monofunctor
// typeclass (forbidden by bifunctorization.md Goal 6). The concrete classes that were
// generated from those bases are now declared directly in tests.scala (one per effect).
