package izumi.logstage.api.logger

import izumi.functional.quasi.{QuasiIO, QuasiPrimitives}
import izumi.logstage.api.Log.Level
import izumi.logstage.macros.LogIOMacroMethods.*

import scala.language.experimental.macros

trait AbstractMacroLogIO[F[_]] { this: AbstractLogIO[F] =>

  /** Aliases for [[AbstractLogIO#log(entry:*]] that look better in Intellij */
  final def trace(message: String): F[Unit] = macro scTraceMacro[F]
  final def debug(message: String): F[Unit] = macro scDebugMacro[F]
  final def info(message: String): F[Unit] = macro scInfoMacro[F]
  final def warn(message: String): F[Unit] = macro scWarnMacro[F]
  final def error(message: String): F[Unit] = macro scErrorMacro[F]
  final def crit(message: String): F[Unit] = macro scCritMacro[F]

  final def logValues(level: Level)(values: Any*): F[Unit] = macro scLogValues[F]

  final def logMethod[G[x] >: F[x], A](level: Level)(function: => A)(implicit qp: QuasiIO[G]): G[A] = macro scLogMethod[G, A]
  final def logMethod[G[x] >: F[x], A](
    level: Level,
    printTypes: Boolean,
  )(function: => A
  )(implicit qp: QuasiIO[G]
  ): G[A] = macro scLogMethodPrintTypes[G, A]
  final def logMethod[G[x] >: F[x], A](
    level: Level,
    printTypes: Boolean,
    printImplicits: Boolean,
  )(function: => A
  )(implicit qp: QuasiIO[G]
  ): G[A] = macro scLogMethodPrintTypesImplicits[G, A]

  final def logMethodF[G[x] >: F[x], A](level: Level)(function: => G[A])(implicit qp: QuasiPrimitives[G]): G[A] = macro scLogMethodF[G, A]
  final def logMethodF[G[x] >: F[x], A](
    level: Level,
    printTypes: Boolean,
  )(function: => G[A]
  )(implicit qp: QuasiPrimitives[G]
  ): G[A] = macro scLogMethodFPrintTypes[G, A]
  final def logMethodF[G[x] >: F[x], A](
    level: Level,
    printTypes: Boolean,
    printImplicits: Boolean,
  )(function: => G[A]
  )(implicit qp: QuasiPrimitives[G]
  ): G[A] = macro scLogMethodFPrintTypesImplicits[G, A]
}
