package izumi.logstage.api.logger

import izumi.logstage.macros.LogIOMacroMethods._

import scala.language.experimental.macros

trait AbstractMacroRawLogIO[F[_]] { this: AbstractLogIO[F] =>

  /** Aliases for [[AbstractLogIO#log(entry:*]] that look better in Intellij */
  final def trace(message: String): F[Unit] = macro scTraceMacroRaw[F]
  final def debug(message: String): F[Unit] = macro scDebugMacroRaw[F]
  final def info(message: String): F[Unit] = macro scInfoMacroRaw[F]
  final def warn(message: String): F[Unit] = macro scWarnMacroRaw[F]
  final def error(message: String): F[Unit] = macro scErrorMacroRaw[F]
  final def crit(message: String): F[Unit] = macro scCritMacroRaw[F]
}
