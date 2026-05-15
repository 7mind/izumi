package izumi.distage.testkit.scalatest

import distage.{DefaultModule2, TagKK}
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.services.scalatest.dstest.ScalatestAbstractDistageSpec
import izumi.logstage.distage.LogIO2Module
import org.scalatest.distage.DistageScalatestTestSuiteRunner

abstract class Spec2[F[+_, +_]: DefaultModule2](implicit val tagBIO2: TagKK[F])
  extends DistageScalatestTestSuiteRunner[F]
  with ScalatestAbstractDistageSpec.For2[F] {

  override protected def config: TestConfig = super.config.copy(
    moduleOverrides = LogIO2Module[F]()(using tagBIO2)
  )
}
