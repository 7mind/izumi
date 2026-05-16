package izumi.distage.testkit.scalatest

import izumi.distage.testkit.services.scalatest.dstest.ScalatestAbstractDistageSpec
import izumi.functional.bio.Bifunctorized
import org.scalatest.distage.DistageScalatestTestSuiteRunner

/**
  * Identity-effect test class. Users write test bodies as plain `A` values (or `Identity[A] = A`).
  * The framework lifts each body to `IdentityBifunctorized[Throwable, A]` via
  * [[Bifunctorized.bifunctorizeIdentity]] (the MiniBIO-carrier route) and hands off to the
  * bifunctor `Spec2`-style machinery.
  *
  * UNLIKE [[Spec1]]`[Identity]` (which would erase to the zero-cost generic carrier with no
  * error channel), `SpecIdentity` runs on the [[Bifunctorized.IdentityBifunctorized]] MiniBIO
  * carrier so that synchronous Throwables thrown in test bodies are routed into the typed
  * error channel and observed by the test reporter.
  */
abstract class SpecIdentity
  extends DistageScalatestTestSuiteRunner[Bifunctorized.IdentityBifunctorized]
  with ScalatestAbstractDistageSpec.ForIdentity
