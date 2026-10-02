package izumi.distage.testkit.runner.spec

import distage.{Functoid, TagK}
import izumi.distage.modules.DefaultModule
import izumi.fundamentals.platform.language.SourceFilePositionMaterializer

import scala.language.implicitConversions

abstract class Spec1[F[_]: TagK: DefaultModule]() extends DistageSpec[F] {
  protected implicit final def wordSpecString(text: String)(implicit position: RegistrationPosition): WordSpecString[F] =
    new WordSpecString(this, text, position, path(text))
}

final class WordSpecString[F[_]] private[spec] (
  override protected val suite: DistageSpec[F],
  override protected val text: String,
  override protected val position: RegistrationPosition,
  override protected val testPath: Vector[String],
) extends WordSpecRegistration[F] {
  infix def in(function: Functoid[F[Unit]])(implicit pos: SourceFilePositionMaterializer): Unit = takeIO(function, pos.get)
  infix def in(value: => F[Unit])(implicit pos: SourceFilePositionMaterializer): Unit = takeIO(() => value, pos.get)
}
