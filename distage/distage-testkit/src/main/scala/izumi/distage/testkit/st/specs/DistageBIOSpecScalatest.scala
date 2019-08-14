package izumi.distage.testkit.st.specs

import izumi.distage.testkit.services.st.dtest.DistageTestSuiteSyntax
import izumi.distage.testkit.services.st.dtest.DistageTestSuiteSyntax.DSWordSpecStringWrapper2
import distage.{TagK, TagKK}
import org.scalatest.DistageScalatestTestSuite

import scala.language.implicitConversions

abstract class DistageBIOSpecScalatest[F[+_, +_]](implicit val tagMonoIO: TagK[F[Throwable, ?]], implicit val tagBIO: TagKK[F])
  extends  DistageScalatestTestSuite[F[Throwable, ?]] with DistageTestSuiteSyntax[F[Throwable, ?]] {

  protected implicit def convertToWordSpecStringWrapper2(s: String): DSWordSpecStringWrapper2[F] = {
    new DSWordSpecStringWrapper2(context, distageSuiteName, distageSuiteId, s, this, env)
  }
}
