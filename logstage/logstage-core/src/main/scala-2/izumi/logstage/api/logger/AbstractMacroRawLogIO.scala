package izumi.logstage.api.logger

import izumi.functional.quasi.{QuasiIO, QuasiPrimitives}
import izumi.logstage.api.Log.Level
import izumi.logstage.macros.LogIOMacroMethods.Raw

import scala.language.experimental.macros

trait AbstractMacroRawLogIO[F[_]] { this: AbstractLogIO[F] =>

  /** Aliases for [[AbstractLogIO#log(entry:*]] that look better in Intellij */
  final def trace(message: String): F[Unit] = macro Raw.scTraceMacro[F]
  final def debug(message: String): F[Unit] = macro Raw.scDebugMacro[F]
  final def info(message: String): F[Unit] = macro Raw.scInfoMacro[F]
  final def warn(message: String): F[Unit] = macro Raw.scWarnMacro[F]
  final def error(message: String): F[Unit] = macro Raw.scErrorMacro[F]
  final def crit(message: String): F[Unit] = macro Raw.scCritMacro[F]

  final def logValues(level: Level)(values: Any*): F[Unit] = macro Raw.scLogValues[F]

  // format: off
  final def logMethod[G[x] >: F[x], A](level: Level)(function: => A)(implicit qp: QuasiIO[G]): G[A] = macro Raw.scLogMethod[G, A]
  final def logMethod[G[x] >: F[x], A](level: Level, printTypes: Boolean)(function: => A)(implicit qp: QuasiIO[G]): G[A] = macro Raw.scLogMethodPrintTypes[G, A]
  final def logMethod[G[x] >: F[x], A](level: Level, printTypes: Boolean, printImplicits: Boolean)(function: => A)(implicit qp: QuasiIO[G]): G[A] = macro Raw.scLogMethodPrintTypesImplicits[G, A]

  final def logMethodF[G[x] >: F[x], A](level: Level)(function: => G[A])(implicit qp: QuasiPrimitives[G]): G[A] = macro Raw.scLogMethodF[G, A]
  final def logMethodF[G[x] >: F[x], A](level: Level, printTypes: Boolean)(function: => G[A])(implicit qp: QuasiPrimitives[G]): G[A] = macro Raw.scLogMethodPrintTypesF[G, A]
  final def logMethodF[G[x] >: F[x], A](level: Level, printTypes: Boolean, printImplicits: Boolean)(function: => G[A])(implicit qp: QuasiPrimitives[G]): G[A] = macro Raw.scLogMethodPrintTypesImplicitsF[G, A]
  // format: on
}
