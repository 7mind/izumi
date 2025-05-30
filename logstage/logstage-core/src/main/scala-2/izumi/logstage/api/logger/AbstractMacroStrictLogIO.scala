package izumi.logstage.api.logger

import izumi.functional.quasi.{QuasiIO, QuasiPrimitives}
import izumi.logstage.api.Log.Level
import izumi.logstage.macros.LogIOMacroMethods.Strict

import scala.language.experimental.macros

trait AbstractMacroStrictLogIO[F[_]] { this: AbstractLogIO[F] =>

  /** Aliases for [[AbstractLogIO#log(entry:*]] that look better in Intellij */
  final def trace(message: String): F[Unit] = macro Strict.scTraceMacro[F]
  final def debug(message: String): F[Unit] = macro Strict.scDebugMacro[F]
  final def info(message: String): F[Unit] = macro Strict.scInfoMacro[F]
  final def warn(message: String): F[Unit] = macro Strict.scWarnMacro[F]
  final def error(message: String): F[Unit] = macro Strict.scErrorMacro[F]
  final def crit(message: String): F[Unit] = macro Strict.scCritMacro[F]

  final def logValues(level: Level)(values: Any*): F[Unit] = macro Strict.scLogValues[F]

  // format: off
  final def logMethod[G[x] >: F[x], A](level: Level)(function: => A)(implicit qp: QuasiIO[G]): G[A] = macro Strict.scLogMethod[G, A]
  final def logMethod[G[x] >: F[x], A](level: Level, printTypes: Boolean)(function: => A)(implicit qp: QuasiIO[G]): G[A] = macro Strict.scLogMethodPrintTypes[G, A]
  final def logMethod[G[x] >: F[x], A](level: Level, printTypes: Boolean, printImplicits: Boolean)(function: => A)(implicit qp: QuasiIO[G]): G[A] = macro Strict.scLogMethodPrintTypesImplicits[G, A]

  final def logMethodF[G[x] >: F[x], A](level: Level)(function: => G[A])(implicit qp: QuasiPrimitives[G]): G[A] = macro Strict.scLogMethodF[G, A]
  final def logMethodF[G[x] >: F[x], A](level: Level, printTypes: Boolean)(function: => G[A])(implicit qp: QuasiPrimitives[G]): G[A] = macro Strict.scLogMethodPrintTypesF[G, A]
  final def logMethodF[G[x] >: F[x], A](level: Level, printTypes: Boolean, printImplicits: Boolean)(function: => G[A])(implicit qp: QuasiPrimitives[G]): G[A] = macro Strict.scLogMethodPrintTypesImplicitsF[G, A]
  // format: on
}
