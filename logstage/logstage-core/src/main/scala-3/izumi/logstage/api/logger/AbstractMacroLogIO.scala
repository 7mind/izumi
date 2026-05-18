package izumi.logstage.api.logger

import izumi.functional.bio.{Error2, IO2}
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log
import izumi.logstage.api.Log.Level
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

  transparent inline final def traceTo(sinkKey: String)(inline message: String): F[Unit] = logToImpl(sinkKey, Log.Level.Trace, message)
  transparent inline final def debugTo(sinkKey: String)(inline message: String): F[Unit] = logToImpl(sinkKey, Log.Level.Debug, message)
  transparent inline final def infoTo(sinkKey: String)(inline message: String): F[Unit] = logToImpl(sinkKey, Log.Level.Info, message)
  transparent inline final def warnTo(sinkKey: String)(inline message: String): F[Unit] = logToImpl(sinkKey, Log.Level.Warn, message)
  transparent inline final def errorTo(sinkKey: String)(inline message: String): F[Unit] = logToImpl(sinkKey, Log.Level.Error, message)
  transparent inline final def critTo(sinkKey: String)(inline message: String): F[Unit] = logToImpl(sinkKey, Log.Level.Crit, message)
  transparent inline final def auditTo(sinkKey: String)(inline message: String): F[Unit] = logToImpl(sinkKey, Log.Level.Audit, message)

  transparent inline final def logValues(level: Log.Level)(inline values: Any*): F[Unit] = {
    ${ LogValuesMacro.logValuesIO[F, EncMode]('{ this }, '{ level }, '{ values }) }
  }

  private[AbstractMacroLogIO] transparent inline final def logImpl(inline level: Log.Level, inline message: String): F[Unit] = {
    this.log(level)(LogMessageMacro.createMessageWithMode[EncMode](message))(CodePositionMaterializer.materialize)
  }

  private[AbstractMacroLogIO] transparent inline final def logToImpl(inline sinkKey: String, inline level: Log.Level, inline message: String): F[Unit] = {
    this.logTo(sinkKey)(level)(LogMessageMacro.createMessageWithMode[EncMode](message))(CodePositionMaterializer.materialize)
  }
}

object AbstractMacroLogIO {

  /**
    * Bifunctor-shaped `logMethod` / `logMethodF` extension methods, provided for any `LogIO`-style
    * receiver whose effect channel projects from a BIO2-shaped bifunctor `F[+_, +_]`. Matches both
    * `LogIO[F[Nothing, _]]` (the default `LogIO2[F]` shape) and `LogIO[F[E, _]]` for any `E` (e.g.
    * after `widenError[Throwable]`). The result of `logMethod` is always `F[Throwable, A]` because
    * the by-name body may throw synchronously; `logMethodF` preserves the typed error channel.
    */
  implicit final class LogIO2LogMethodSyntax[F[+_, +_], E, Enc](
    val self: AbstractLogIO[F[E, _]] { type EncMode = Enc }
  ) extends AnyVal {
    transparent inline def logMethod[A](
      level: Level,
      printTypes: Boolean = false,
      printImplicits: Boolean = false,
    )(inline function: => A
    )(using F: IO2[F]
    ): F[Throwable, A] = {
      ${
        LogMethodMacro.logMethodIO[F, A, Enc](
          '{ level },
          '{ function },
          '{ self.asInstanceOf[AbstractLogIO[F[Nothing, _]]] },
          '{ printTypes },
          '{ printImplicits },
          '{ F },
        )
      }
    }

    transparent inline def logMethodF[E1, A](
      level: Level,
      printTypes: Boolean = false,
      printImplicits: Boolean = false,
    )(inline function: => F[E1, A]
    )(using F: Error2[F]
    ): F[E1, A] = {
      ${
        LogMethodMacro.logMethodIOF[F, E1, A, Enc](
          '{ level },
          '{ function },
          '{ function },
          '{ self.asInstanceOf[AbstractLogIO[F[Nothing, _]]] },
          '{ printTypes },
          '{ printImplicits },
          '{ F },
        )
      }
    }
  }
}
