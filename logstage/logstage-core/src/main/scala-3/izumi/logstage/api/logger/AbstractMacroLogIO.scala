package izumi.logstage.api.logger

import izumi.functional.quasi.{QuasiIO, QuasiPrimitives}
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log.Level
import izumi.logstage.api.Log
import izumi.logstage.macros.{LogMessageMacro, LogMethodMacro, LogValuesMacro}

trait AbstractMacroLogIO[F[_]] { this: AbstractLogIO[F] { type EncMode <: Singleton } =>

  /** Aliases for [[AbstractLogIO#log(entry:*]] that look better in Intellij */
  transparent inline final def trace(inline message: String): F[Unit] = logImpl(Log.Level.Trace, message)
  transparent inline final def debug(inline message: String): F[Unit] = logImpl(Log.Level.Debug, message)
  transparent inline final def info(inline message: String): F[Unit] = logImpl(Log.Level.Info, message)
  transparent inline final def warn(inline message: String): F[Unit] = logImpl(Log.Level.Warn, message)
  transparent inline final def error(inline message: String): F[Unit] = logImpl(Log.Level.Error, message)
  transparent inline final def crit(inline message: String): F[Unit] = logImpl(Log.Level.Crit, message)
  transparent inline final def audit(inline message: String): F[Unit] = logImpl(Log.Level.Audit, message)

  transparent inline final def logValues(level: Log.Level)(inline values: Any*): F[Unit] = {
    ${ LogValuesMacro.logValuesIO[F, EncMode]('{ this }, '{ level }, '{ values }) }
  }

  transparent inline final def logMethod[G[x] >: F[x], A](
    level: Level,
    printTypes: Boolean = false,
    printImplicits: Boolean = false,
  )(inline function: => A
  )(using G: QuasiIO[G]
  ): G[A] = {
    ${ LogMethodMacro.logMethodIO[A, F, G, EncMode]('{ level }, '{ function }, '{ this }, '{ printTypes }, '{ printImplicits }, '{ G }) }
  }

  transparent inline final def logMethodF[G[x] >: F[x], A](
    level: Level,
    printTypes: Boolean = false,
    printImplicits: Boolean = false,
  )(inline function: => G[A]
  )(using G: QuasiPrimitives[G]
  ): G[A] = {
    ${ LogMethodMacro.logMethodIOF[A, F, G, EncMode]('{ level }, '{ function }, '{ function }, '{ this }, '{ printTypes }, '{ printImplicits }, '{ G }) }
  }

  private[AbstractMacroLogIO] transparent inline final def logImpl(inline level: Log.Level, inline message: String): F[Unit] = {
    this.log(level)(LogMessageMacro.createMessageWithMode[EncMode](message))(CodePositionMaterializer.materialize)
  }
}
