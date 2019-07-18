package com.github.pshirshov.izumi.distage.testkit.st.specs

import com.github.pshirshov.izumi.distage.testkit.services.st.dtest.DistageTestSuiteSyntax
import distage.{TagK, TagKK}
import org.scalatest.{DistageScalatestTestSuite, Finders}

@Finders(Array("org.scalatest.finders.WordSpecFinder"))
abstract class DistageSpecScalatest[F[_]]()(implicit val tagMonoIO: TagK[F])
  extends DistageScalatestTestSuite[F] with DistageTestSuiteSyntax[F]


@Finders(Array("org.scalatest.finders.WordSpecFinder"))
abstract class DistageBIOSpecScalatest[F[_, _]](implicit val tagMonoIO: TagK[F[Throwable, ?]], implicit val tagBIO: TagKK[F])
  extends  DistageScalatestTestSuite[F[Throwable, ?]] with DistageTestSuiteSyntax[F[Throwable, ?]] {

}
