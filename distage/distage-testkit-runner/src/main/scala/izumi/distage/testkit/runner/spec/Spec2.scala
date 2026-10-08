package izumi.distage.testkit.runner.spec

import distage.{DefaultModule2, Functoid, TagKK}
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.spec.DISyntaxBIOBase
import izumi.fundamentals.platform.language.SourceFilePositionMaterializer
import izumi.logstage.distage.LogIO2Module

import scala.language.implicitConversions

abstract class Spec2[F[+_, +_]: DefaultModule2](implicit val tagBIO: TagKK[F]) extends DistageSpec[F[Throwable, _]] {
  override protected def config: TestConfig = super.config.copy(moduleOverrides = LogIO2Module[F]()(using tagBIO))

  protected implicit final def wordSpecString(text: String)(implicit position: RegistrationPosition): WordSpecString2[F] =
    new WordSpecString2(this, text, position, path(text), tagBIO)
}

final class WordSpecString2[F[+_, +_]] private[spec] (
  override protected val suite: DistageSpec[F[Throwable, _]],
  override protected val text: String,
  override protected val position: RegistrationPosition,
  override protected val testPath: Vector[String],
  override implicit val tagBIO: TagKK[F],
) extends WordSpecRegistration[F[Throwable, _]] with DISyntaxBIOBase[F] {
  infix def in(function: Functoid[F[Any, Unit]])(implicit pos: SourceFilePositionMaterializer): Unit = takeBIO(function, pos.get)
  infix def in(value: => F[Any, Unit])(implicit pos: SourceFilePositionMaterializer): Unit = takeBIO(() => value, pos.get)
}
