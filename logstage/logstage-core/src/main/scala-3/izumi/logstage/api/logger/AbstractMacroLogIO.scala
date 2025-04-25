package izumi.logstage.api.logger

import izumi.functional.quasi.{QuasiIO, QuasiPrimitives}
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log.{Level, Message}
import izumi.logstage.api.{Log, LogMethodMacro}

trait AbstractMacroLogIO[F[_]] { this: AbstractLogIO[F] =>

  /** Aliases for [[AbstractLogIO#log(entry:*]] that look better in Intellij */
  transparent inline final def trace(inline message: String): F[Unit] = log(Log.Level.Trace, message)
  transparent inline final def debug(inline message: String): F[Unit] = log(Log.Level.Debug, message)
  transparent inline final def info(inline message: String): F[Unit] = log(Log.Level.Info, message)
  transparent inline final def warn(inline message: String): F[Unit] = log(Log.Level.Warn, message)
  transparent inline final def error(inline message: String): F[Unit] = log(Log.Level.Error, message)
  transparent inline final def crit(inline message: String): F[Unit] = log(Log.Level.Crit, message)

  transparent inline final def logMethod[G[x] >: F[x], A](
    inline level: Level,
    inline logTypes: Boolean = true,
    inline logImplicits: Boolean = true,
  )(inline function: => A
  )(using qp: QuasiIO[G]
  ): G[A] = {
    ${ LogMethodMacro.logMethodIO[A, F, G]('level, 'function, 'this, 'logTypes, 'logImplicits, 'qp) }
  }

  transparent inline final def logMethodF[G[x] >: F[x], A](
    inline level: Level,
    inline logTypes: Boolean = true,
    inline logImplicits: Boolean = true,
  )(inline function: => G[A]
  )(using qp: QuasiPrimitives[G]
  ): G[A] = {
    ${ LogMethodMacro.logMethodIOF[A, G[A], F, G]('level, 'function, 'function, 'this, 'logTypes, 'logImplicits, 'qp) }
  }

  transparent inline final def log(inline level: Log.Level, inline message: String): F[Unit] = {
    log(level)(Message(message))(CodePositionMaterializer.materialize)
  }
}
