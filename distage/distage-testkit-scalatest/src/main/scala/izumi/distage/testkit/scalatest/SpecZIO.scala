package izumi.distage.testkit.scalatest

import distage.{DefaultModule3, TagK3, TagKK}
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.services.scalatest.dstest.ScalatestAbstractDistageSpec
import izumi.logstage.distage.LogIO2Module
import org.scalatest.distage.DistageScalatestTestSuiteRunner
import zio.ZIO

/**
  * Allows summoning objects from DI in tests via ZIO environment intersection types.
  */
abstract class SpecZIO(implicit val defaultModule3: DefaultModule3[ZIO], val tagBIO3: TagK3[ZIO], val tagBIOZIO: TagKK[ZIO[Any, +_, +_]])
  extends DistageScalatestTestSuiteRunner[ZIO[Any, +_, +_]]
  with ScalatestAbstractDistageSpec.ForZIO {

  override protected def config: TestConfig = super.config.copy(
    moduleOverrides = LogIO2Module[ZIO[Any, +_, +_]]()(using tagBIOZIO)
  )

}
