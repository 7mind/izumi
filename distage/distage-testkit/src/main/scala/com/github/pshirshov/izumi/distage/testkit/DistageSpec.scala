package com.github.pshirshov.izumi.distage.testkit

import distage.TagK
import org.scalatest.DistageScalatestTestSuite

abstract class DistageSpec[F[_]]()(implicit val tagMonoIO: TagK[F]) extends DistageTestSuiteSyntax[F]

abstract class DistageSpecScalatest[F[_]]()(implicit val tagMonoIO: TagK[F]) extends DistageTestSuiteSyntax[F] with DistageScalatestTestSuite[F]


