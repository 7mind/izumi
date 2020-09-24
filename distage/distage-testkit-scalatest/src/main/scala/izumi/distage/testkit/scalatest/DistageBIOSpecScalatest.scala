package izumi.distage.testkit.scalatest

import distage.TagKK
import izumi.distage.effect.DefaultModules2
import izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec
import izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec.{DSWordSpecStringWrapper, DSWordSpecStringWrapper2}
import org.scalatest.distage.DistageScalatestTestSuiteRunner

import scala.language.implicitConversions

abstract class DistageBIOSpecScalatest[F[+_, +_]: DefaultModules2](implicit val tagBIO: TagKK[F])
  extends DistageScalatestTestSuiteRunner[F[Throwable, ?]]
  with DistageAbstractScalatestSpec[F[Throwable, ?]] {

  protected implicit def convertToWordSpecStringWrapperDS2(s: String): DSWordSpecStringWrapper2[F] = {
    new DSWordSpecStringWrapper2(context, distageSuiteName, distageSuiteId, s, this, testEnv)
  }

  // disable single-parameter syntax by removing `implicit`
  override protected def convertToWordSpecStringWrapperDS(s: String): DSWordSpecStringWrapper[F[Throwable, ?]] = super.convertToWordSpecStringWrapperDS(s)
}
