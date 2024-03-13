package izumi.distage.testkit.scalatest

import distage.TagK
import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec
import izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec.DSWordSpecStringWrapper
import org.scalatest.distage.DistageScalatestTestSuiteRunner

import scala.language.implicitConversions

abstract class Spec1[F[_]: TagK: DefaultModule] extends DistageScalatestTestSuiteRunner[F] with DistageAbstractScalatestSpec[F] {

  protected implicit def convertToWordSpecStringWrapperDS(s: String): DSWordSpecStringWrapper[F] = {
    new DSWordSpecStringWrapper(context, distageSuiteName, distageSuiteId, Seq(s), this, testEnv)
  }

}
