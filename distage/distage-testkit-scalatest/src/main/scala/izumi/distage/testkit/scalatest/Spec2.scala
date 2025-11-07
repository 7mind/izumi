package izumi.distage.testkit.scalatest

import distage.{DefaultModule2, TagKK}
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.services.scalatest.dstest.ScalatestAbstractDistageSpec
import izumi.distage.testkit.services.scalatest.dstest.ScalatestAbstractDistageSpec.DSWordSpecStringWrapper2
import izumi.logstage.distage.LogIO2Module
import org.scalatest.distage.DistageScalatestTestSuiteRunner

import scala.language.implicitConversions

abstract class Spec2[F[+_, +_]: DefaultModule2](implicit val tagBIO: TagKK[F])
  extends DistageScalatestTestSuiteRunner[F[Throwable, _]]
  with ScalatestAbstractDistageSpec[F[Throwable, _]] {

  protected implicit def convertToWordSpecStringWrapperDS2(s: String): DSWordSpecStringWrapper2[F] = {
    new DSWordSpecStringWrapper2(context, distageSuiteName, distageSuiteId, Seq(s), this, testEnv)
  }

  override protected def config: TestConfig = super.config.copy(
    moduleOverrides = LogIO2Module[F]()(using tagBIO)
  )
}
