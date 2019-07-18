package com.github.pshirshov.izumi.distage.testkit.st.specs

import com.github.pshirshov.izumi.distage.testkit.services.st.dtest.DistageTestSuiteSyntax
import com.github.pshirshov.izumi.distage.testkit.services.st.dtest.DistageTestSuiteSyntax.WordSpecStringWrapper2
import distage.{TagK, TagKK}
import org.scalatest.{DistageScalatestTestSuite, Finders}

@Finders(Array("org.scalatest.finders.WordSpecFinder"))
abstract class DistageBIOSpecScalatest[F[+_, +_]](implicit val tagMonoIO: TagK[F[Throwable, ?]], implicit val tagBIO: TagKK[F])
  extends  DistageScalatestTestSuite[F[Throwable, ?]] with DistageTestSuiteSyntax[F[Throwable, ?]] {

  protected implicit def convertToWordSpecStringWrapper2(s: String): WordSpecStringWrapper2[F] = {
    new WordSpecStringWrapper2(left, verb, distageSuiteName, distageSuiteId, s, this, env)
  }
}
