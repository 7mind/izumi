package izumi.distage.testkit.scalatest

import distage.{DefaultModule2, TagKK}
import izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec
import izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec.DSWordSpecStringWrapper2
import org.scalatest.distage.DistageScalatestTestSuiteRunner

import scala.language.implicitConversions

abstract class Spec2[F[+_, +_]: DefaultModule2](implicit val tagBIO: TagKK[F])
  extends DistageScalatestTestSuiteRunner[F[Throwable, ?]]
  with DistageAbstractScalatestSpec[F[Throwable, ?]] {

  protected implicit def convertToWordSpecStringWrapperDS2(s: String): DSWordSpecStringWrapper2[F] = {
    new DSWordSpecStringWrapper2(context, distageSuiteName, distageSuiteId, s, this, testEnv)
  }

}
