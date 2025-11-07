package izumi.distage.testkit.scalatest

import distage.TagK
import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.services.scalatest.dstest.ScalatestAbstractDistageSpec
import izumi.distage.testkit.services.scalatest.dstest.ScalatestAbstractDistageSpec.DSWordSpecStringWrapper
import org.scalatest.distage.DistageScalatestTestSuiteRunner

import scala.language.implicitConversions

abstract class Spec1[F[_]: TagK: DefaultModule]() extends DistageScalatestTestSuiteRunner[F] with ScalatestAbstractDistageSpec[F] {

  protected implicit def convertToWordSpecStringWrapperDS(s: String): DSWordSpecStringWrapper[F] = {
    new DSWordSpecStringWrapper(context, distageSuiteName, distageSuiteId, Seq(s), this, testEnv)
  }

}
