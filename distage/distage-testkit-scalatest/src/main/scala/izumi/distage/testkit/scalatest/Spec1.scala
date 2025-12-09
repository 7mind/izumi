package izumi.distage.testkit.scalatest

import distage.TagK
import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.services.scalatest.dstest.ScalatestAbstractDistageSpec
import org.scalatest.distage.DistageScalatestTestSuiteRunner

abstract class Spec1[F[_]: TagK: DefaultModule]() extends DistageScalatestTestSuiteRunner[F] with ScalatestAbstractDistageSpec.For1[F]
