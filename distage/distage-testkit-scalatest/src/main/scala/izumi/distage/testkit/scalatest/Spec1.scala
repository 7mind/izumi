package izumi.distage.testkit.scalatest

import distage.{TagK, TagKK}
import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.services.scalatest.dstest.ScalatestAbstractDistageSpec
import izumi.functional.bio.{Bifunctorize, Bifunctorized}
import org.scalatest.distage.DistageScalatestTestSuiteRunner

/**
  * Monofunctor-flavoured test class. The user supplies a monofunctor effect type `F[_]`
  * (e.g. `cats.effect.IO`) and writes test bodies as `F[A]` directly. The framework lifts
  * each body to the bifunctorized runtime type `Bifunctorized[F, Throwable, A]` via the
  * [[Bifunctorize]] typeclass and hands off to the bifunctor `Spec2` machinery.
  *
  * For Identity effect type, prefer [[SpecIdentity]] (which runs on the MiniBIO-carrier
  * `IdentityBifunctorized` rather than the zero-cost `Bifunctorized[Identity, +_, +_]`).
  *
  * The `Bifunctorize[F]` typeclass drives the lift: the identity instance is used by default
  * (zero-cost reinterpret cast for real bifunctors and any `F` without a higher-priority
  * instance), and `import izumi.functional.bio.CatsToBIOConversions.*` brings the cats-mediated
  * instance that submerges the raw Throwable channel into a typed BIO error channel.
  */
abstract class Spec1[F[_]]()(
  implicit val tagMonoIO: TagK[F],
  val tagBIOAlias: TagKK[Bifunctorized[F, +_, +_]],
  val defaultModulesBIOAlias: DefaultModule[Bifunctorized[F, +_, +_]],
  val bifunctorize1: Bifunctorize[F],
) extends DistageScalatestTestSuiteRunner[Bifunctorized[F, +_, +_]]
  with ScalatestAbstractDistageSpec.For1[F]
