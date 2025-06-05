package izumi.logstage.api.logger

import izumi.functional.quasi.{QuasiIO, QuasiPrimitives}
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log.{Level, Message}
import izumi.logstage.api.{Log, LogMessageMacro, LogMethodMacro, LogValuesMacro}

trait AbstractMacroLogIO[F[_]] { this: AbstractLogIO[F] { type EncMode <: Singleton } =>

  /** Aliases for [[AbstractLogIO#log(entry:*]] that look better in Intellij */
  transparent inline final def trace(inline message: String): F[Unit] = logImpl(Log.Level.Trace, message)
  transparent inline final def debug(inline message: String): F[Unit] = logImpl(Log.Level.Debug, message)
  transparent inline final def info(inline message: String): F[Unit] = logImpl(Log.Level.Info, message)
  transparent inline final def warn(inline message: String): F[Unit] = logImpl(Log.Level.Warn, message)
  transparent inline final def error(inline message: String): F[Unit] = logImpl(Log.Level.Error, message)
  transparent inline final def crit(inline message: String): F[Unit] = logImpl(Log.Level.Crit, message)

  transparent inline final def logValues(level: Log.Level)(values: Any*): F[Unit] = {
    ${ LogValuesMacro.logValuesIO[F, EncMode]('{ this }, '{ level }, '{ values }) }
  }

  transparent inline final def logMethod[G[x] >: F[x], A](
    level: Level,
    printTypes: Boolean = false,
    printImplicits: Boolean = false,
  )(inline function: => A
  )(using qp: QuasiIO[G]
  ): G[A] = {
    ${ LogMethodMacro.logMethodIO[A, F, G, EncMode]('{ level }, '{ function }, '{ this }, '{ printTypes }, '{ printImplicits }, '{ qp }) }
  }

  transparent inline final def logMethodF[G[x] >: F[x], A](
    level: Level,
    printTypes: Boolean = true,
    printImplicits: Boolean = true,
  )(inline function: => G[A]
  )(using qp: QuasiPrimitives[G]
  ): G[A] = {
    ${ LogMethodMacro.logMethodIOF[A, F, G, EncMode]('{ level }, '{ function }, '{ function }, '{ this }, '{ printTypes }, '{ printImplicits }, '{ qp }) }
  }

  private[AbstractMacroLogIO] transparent inline final def logImpl(inline level: Log.Level, inline message: String): F[Unit] = {
    this.log(level)(LogMessageMacro.createMessageWithMode[EncMode](message))(CodePositionMaterializer.materialize)
  }
}
