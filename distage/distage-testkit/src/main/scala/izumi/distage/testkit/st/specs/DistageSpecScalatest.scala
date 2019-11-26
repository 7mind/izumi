package izumi.distage.testkit.st.specs

import izumi.distage.testkit.services.st.dtest.DistageAbstractScalatestSpec
import distage.TagK
import org.scalatest.DistageScalatestTestSuiteRunner

abstract class DistageSpecScalatest[F[_]]()(implicit val tagMonoIO: TagK[F])
  extends DistageScalatestTestSuiteRunner[F] with DistageAbstractScalatestSpec[F]



