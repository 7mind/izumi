package izumi.logstage.api.logger

import izumi.functional.quasi.{QuasiIO, QuasiPrimitives}
import izumi.logstage.api.Log.Level
import izumi.logstage.macros.LogIOMacroMethods.NonStrict

import scala.language.experimental.macros

trait AbstractMacroLogIO[F[_]] { this: AbstractLogIO[F] =>

  /** Aliases for [[AbstractLogIO#log(entry:*]] that look better in Intellij */
  final def trace(message: String): F[Unit] = macro NonStrict.scTraceMacro[F]
  final def debug(message: String): F[Unit] = macro NonStrict.scDebugMacro[F]
  final def info(message: String): F[Unit] = macro NonStrict.scInfoMacro[F]
  final def warn(message: String): F[Unit] = macro NonStrict.scWarnMacro[F]
  final def error(message: String): F[Unit] = macro NonStrict.scErrorMacro[F]
  final def crit(message: String): F[Unit] = macro NonStrict.scCritMacro[F]

  final def logValues(level: Level)(values: Any*): F[Unit] = macro NonStrict.scLogValues[F]

  // format: off
  final def logMethod[G[x] >: F[x], A](level: Level)(function: => A)(implicit qp: QuasiIO[G]): G[A] = macro NonStrict.scLogMethod[G, A]
  final def logMethod[G[x] >: F[x], A](level: Level, printTypes: Boolean)(function: => A)(implicit qp: QuasiIO[G]): G[A] = macro NonStrict.scLogMethodPrintTypes[G, A]
  final def logMethod[G[x] >: F[x], A](level: Level, printTypes: Boolean, printImplicits: Boolean)(function: => A)(implicit qp: QuasiIO[G]): G[A] = macro NonStrict.scLogMethodPrintTypesImplicits[G, A]

  final def logMethodF[G[x] >: F[x], A](level: Level)(function: => G[A])(implicit qp: QuasiPrimitives[G]): G[A] = macro NonStrict.scLogMethodF[G, A]
  final def logMethodF[G[x] >: F[x], A](level: Level, printTypes: Boolean)(function: => G[A])(implicit qp: QuasiPrimitives[G]): G[A] = macro NonStrict.scLogMethodPrintTypesF[G, A]
  final def logMethodF[G[x] >: F[x], A](level: Level, printTypes: Boolean, printImplicits: Boolean)(function: => G[A])(implicit qp: QuasiPrimitives[G]): G[A] = macro NonStrict.scLogMethodPrintTypesImplicitsF[G, A]
  // format: on
}
