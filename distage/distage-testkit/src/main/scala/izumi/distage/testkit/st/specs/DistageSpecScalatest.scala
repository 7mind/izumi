package izumi.distage.testkit.st.specs

import izumi.distage.testkit.services.st.dtest.DistageTestSuiteSyntax
import distage.TagK
import org.scalatest.DistageScalatestTestSuite

abstract class DistageSpecScalatest[F[_]]()(implicit val tagMonoIO: TagK[F])
  extends DistageScalatestTestSuite[F] with DistageTestSuiteSyntax[F]



