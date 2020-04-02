package izumi.logstage.api.logger

import izumi.logstage.macros.LogIOMacroMethods._
import logstage.strict.LogIOStrict

import scala.language.experimental.macros

trait AbstractMacroStrictLoggerF[F[_]] {
  this: LogIOStrict[F] =>

  /** Aliases for [[logstage.strict.LogIOStrict#log]] that look better in Intellij */
  final def trace(message: String): F[Unit] = macro scTraceMacroStrict[F]
  final def debug(message: String): F[Unit] = macro scDebugMacroStrict[F]
  final def info(message: String): F[Unit] = macro scInfoMacroStrict[F]
  final def warn(message: String): F[Unit] = macro scWarnMacroStrict[F]
  final def error(message: String): F[Unit] = macro scErrorMacroStrict[F]
  final def crit(message: String): F[Unit] = macro scCritMacroStrict[F]
}
