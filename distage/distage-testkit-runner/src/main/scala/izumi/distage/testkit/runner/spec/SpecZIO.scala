package izumi.distage.testkit.runner.spec

import distage.{DefaultModule3, Functoid, TagK3, TagKK}
import izumi.distage.constructors.ZEnvConstructor
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.spec.DISyntaxBIOBase
import izumi.fundamentals.platform.language.SourceFilePositionMaterializer
import izumi.logstage.distage.LogIO2Module
import zio.ZIO

import scala.language.implicitConversions

abstract class SpecZIO(implicit val defaultModule3: DefaultModule3[ZIO], val tagBIO3: TagK3[ZIO], val tagBIO: TagKK[ZIO[Any, _, _]])
  extends DistageSpec[ZIO[Any, Throwable, _]] {
  override protected def config: TestConfig = super.config.copy(moduleOverrides = LogIO2Module[ZIO[Any, _, _]]()(using tagBIO))

  protected implicit final def wordSpecString(text: String)(implicit position: RegistrationPosition): WordSpecStringZIO =
    new WordSpecStringZIO(this, text, position, path(text), tagBIO)
}

final class WordSpecStringZIO private[spec] (
  override protected val suite: DistageSpec[ZIO[Any, Throwable, _]],
  override protected val text: String,
  override protected val position: RegistrationPosition,
  override protected val testPath: Vector[String],
  override implicit val tagBIO: TagKK[ZIO[Any, _, _]],
) extends WordSpecRegistration[ZIO[Any, Throwable, _]] with DISyntaxBIOBase[ZIO[Any, +_, +_]] {
  infix def in[R: ZEnvConstructor](function: Functoid[ZIO[R, Any, Unit]])(implicit pos: SourceFilePositionMaterializer): Unit =
    takeBIO(function.map2(ZEnvConstructor[R])((effect, environment) => effect.provideEnvironment(environment)), pos.get)

  infix def in[R: ZEnvConstructor](value: => ZIO[R, Any, Unit])(implicit pos: SourceFilePositionMaterializer): Unit =
    takeBIO(ZEnvConstructor[R].map(value.provideEnvironment(_)), pos.get)
}
