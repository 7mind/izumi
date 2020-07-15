package izumi.distage.testkit.scalatest

import distage.TagK
import izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec
import org.scalatest.distage.DistageScalatestTestSuiteRunner

abstract class DistageSpecScalatest[F[_]: TagK] extends DistageScalatestTestSuiteRunner[F] with DistageAbstractScalatestSpec[F]
