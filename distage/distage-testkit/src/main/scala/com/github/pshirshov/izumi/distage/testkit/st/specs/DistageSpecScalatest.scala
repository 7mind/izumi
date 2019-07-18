package com.github.pshirshov.izumi.distage.testkit.st.specs

import com.github.pshirshov.izumi.distage.testkit.services.st.dtest.DistageTestSuiteSyntax
import distage.TagK
import org.scalatest.{DistageScalatestTestSuite, Finders}

import scala.language.implicitConversions

@Finders(Array("org.scalatest.finders.WordSpecFinder"))
abstract class DistageSpecScalatest[F[_]]()(implicit val tagMonoIO: TagK[F])
  extends DistageScalatestTestSuite[F] with DistageTestSuiteSyntax[F]



