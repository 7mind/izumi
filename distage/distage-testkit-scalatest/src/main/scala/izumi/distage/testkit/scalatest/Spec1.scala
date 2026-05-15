package izumi.distage.testkit.scalatest

import distage.{DefaultModule2, TagKK}
import izumi.distage.testkit.services.scalatest.dstest.ScalatestAbstractDistageSpec
import org.scalatest.distage.DistageScalatestTestSuiteRunner

/**
  * `Spec1` was renamed and now takes a bifunctor `F[+_, +_]`. This is identical to [[Spec2]].
  *
  * Migration: tests that wrote `Spec1[CIO]` should write `Spec1[Bifunctorized[CIO, +_, +_]]`.
  * Tests that wrote `Spec1[Identity]` should write `Spec1[Bifunctorized.IdentityBifunctorized]`.
  * Tests that wrote `Spec1[zio.Task]` should write `Spec1[zio.IO]`.
  */
abstract class Spec1[F[+_, +_]: DefaultModule2]()(implicit val tagBIOAlias: TagKK[F])
  extends DistageScalatestTestSuiteRunner[F]
  with ScalatestAbstractDistageSpec.For2[F]
