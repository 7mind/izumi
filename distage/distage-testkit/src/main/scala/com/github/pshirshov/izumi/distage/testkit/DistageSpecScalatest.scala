package com.github.pshirshov.izumi.distage.testkit

import distage.TagK
import org.scalatest.DistageScalatestTestSuite

abstract class DistageSpecScalatest[F[_]]()(implicit val tagMonoIO: TagK[F]) extends DistageTestSuiteSyntax[F] with DistageScalatestTestSuite[F]


