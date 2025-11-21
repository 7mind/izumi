package izumi.logstage.api.logger

import izumi.functional.quasi.{QuasiIO, QuasiPrimitives}
import izumi.logstage.api.Log.Level
import izumi.logstage.api.logger.AbstractMacroLogIO.LogMethodF
import izumi.logstage.macros.LogIOMacroMethods.*

import scala.language.experimental.macros

trait AbstractMacroLogIO[F[_]] { this: AbstractLogIO[F] { type EncMode <: Singleton } =>

  /** Aliases for [[AbstractLogIO#log(entry:*]] that look better in Intellij */
  final def trace(message: String): F[Unit] = macro scTraceMacro[F]
  final def debug(message: String): F[Unit] = macro scDebugMacro[F]
  final def info(message: String): F[Unit] = macro scInfoMacro[F]
  final def warn(message: String): F[Unit] = macro scWarnMacro[F]
  final def error(message: String): F[Unit] = macro scErrorMacro[F]
  final def crit(message: String): F[Unit] = macro scCritMacro[F]
  final def audit(message: String): F[Unit] = macro scAuditMacro[F]

  final def logValues(level: Level)(values: Any*): F[Unit] = macro scLogValues[F]

  final def logMethodF(level: Level, printTypes: Boolean = false, printImplicits: Boolean = false): LogMethodF[F, EncMode] =
    new LogMethodF[F, EncMode](this, level, printTypes, printImplicits)
}

object AbstractMacroLogIO {

  implicit final class LogIO2LogMethodSyntax[F[+_, +_], Enc](private val self: AbstractLogIO[F[Nothing, _]] { type EncMode = Enc }) extends AnyVal {
    def logMethod(level: Level, printTypes: Boolean = false, printImplicits: Boolean = false): LogMethod[F[Nothing, _], F[Throwable, _], Enc] =
      new LogMethod[F[Nothing, _], F[Throwable, _], Enc](self, level, printTypes, printImplicits)
  }

  implicit final class LogIO1LogMethodSyntax[F[_], Enc](private val self: AbstractLogIO[F] { type EncMode = Enc }) extends AnyVal {
    def logMethod(level: Level, printTypes: Boolean = false, printImplicits: Boolean = false): LogMethod[F, F, Enc] =
      new LogMethod[F, F, Enc](self, level, printTypes, printImplicits)
  }

  final class LogMethod[XF[_], F[x] >: XF[x], En](val __getSelf: AbstractLogIO[XF], val __getSelfLevel: Level, val __printTypes: Boolean, val __printImplicits: Boolean) {
    def apply[A](function: => A)(implicit F: QuasiIO[F]): F[A] = macro scLogMethod[XF, F, A, En]
  }

  final class LogMethodF[XF[_], EncMode](val __getSelf: AbstractLogIO[XF], val __getSelfLevel: Level, val __printTypes: Boolean, val __printImplicits: Boolean) {
    def apply[F[x] >: XF[x], A](function: => F[A])(implicit F: QuasiPrimitives[F]): F[A] = macro scLogMethodF[F, A, EncMode]
  }

}
