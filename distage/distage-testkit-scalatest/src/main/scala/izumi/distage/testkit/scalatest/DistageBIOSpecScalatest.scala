package izumi.distage.testkit.scalatest

import distage.{TagK, TagKK}
import izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec
import izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec.DSWordSpecStringWrapper2
import org.scalatest.DistageScalatestTestSuiteRunner

import scala.language.implicitConversions

abstract class DistageBIOSpecScalatest[F[+_, +_]](implicit val tagBIO: TagKK[F])
  extends DistageScalatestTestSuiteRunner[F[Throwable, ?]]
    with DistageAbstractScalatestSpec[F[Throwable, ?]] {

  override val tagMonoIO: TagK[F[Throwable, ?]] = TagK[F[Throwable, ?]]

  protected implicit def convertToWordSpecStringWrapper2(s: String): DSWordSpecStringWrapper2[F] = {
    new DSWordSpecStringWrapper2(context, distageSuiteName, distageSuiteId, s, this, testEnv)
  }
}
