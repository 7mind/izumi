package izumi.distage.testkit.st.specs

import izumi.distage.testkit.services.st.dtest.DistageAbstractScalatestSpec
import izumi.distage.testkit.services.st.dtest.DistageAbstractScalatestSpec.DSWordSpecStringWrapper2
import distage.{TagK, TagKK}
import org.scalatest.DistageScalatestTestSuiteRunner

import scala.language.implicitConversions

abstract class DistageBIOSpecScalatest[F[+_, +_]](implicit val tagMonoIO: TagK[F[Throwable, ?]], implicit val tagBIO: TagKK[F])
  extends DistageScalatestTestSuiteRunner[F[Throwable, ?]]
    with DistageAbstractScalatestSpec[F[Throwable, ?]] {

  protected implicit def convertToWordSpecStringWrapper2(s: String): DSWordSpecStringWrapper2[F] = {
    new DSWordSpecStringWrapper2(context, distageSuiteName, distageSuiteId, s, this, testEnv)
  }
}
