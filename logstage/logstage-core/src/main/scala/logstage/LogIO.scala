package logstage

import izumi.functional.bio.{Error2, MonadAsk3, Panic2, SyncSafe2, SyncSafe3}
import izumi.functional.mono.SyncSafe
import izumi.fundamentals.platform.language.{CodePositionMaterializer, unused}
import izumi.logstage.api.Log._
import izumi.logstage.api.logger
import izumi.logstage.api.logger.{AbstractLogger, AbstractMacroLoggerF}
import izumi.logstage.api.rendering.{AnyEncoded, RenderingPolicy}
import izumi.reflect.Tag
import logstage.LogIO3Ask.LogIO3AskImpl
import logstage.UnsafeLogIO.UnsafeLogIOSyncSafeInstance

import scala.language.implicitConversions

trait LogIO[F[_]] extends logger.EncodingAwareAbstractLogIO[F, AnyEncoded] with AbstractMacroLoggerF[F] {
  override type Self[f[_]] = LogIO[f]

  final def raw: LogIORaw[F, AnyEncoded] = new logger.LogIORaw(this)

  override def widen[G[_]](implicit @unused ev: F[_] <:< G[_]): LogIO[G] = this.asInstanceOf[LogIO[G]]
}

object LogIO {
  @inline def apply[F[_]](implicit l: LogIO[F]): l.type = l

  /**
    * Lets you refer to an implicit logger's methods without naming a variable
    *
    * {{{
    *   import logstage.LogIO.log
    *
    *   def fn[F[_]: LogIO]: F[Unit] = {
    *     log.info(s"I'm logging with ${log}stage!")
    *   }
    * }}}
    */
  @inline def log[F[_]](implicit l: LogIO[F]): l.type = l

  def fromLogger[F[_]: SyncSafe](logger: AbstractLogger): LogIO[F] = {
    new UnsafeLogIOSyncSafeInstance[F](logger)(SyncSafe[F]) with LogIO[F] {
      override def log(entry: Entry): F[Unit] = {
        F.syncSafe(logger.log(entry))
      }

      override def log(logLevel: Level)(messageThunk: => Message)(implicit pos: CodePositionMaterializer): F[Unit] = {
        F.syncSafe(logger.log(logLevel)(messageThunk))
      }

      override def withCustomContext(context: CustomContext): LogIO[F] = {
        fromLogger[F](logger.withCustomContext(context))
      }
    }
  }

  implicit def fromBIOMonadAsk[F[-_, +_, +_]: MonadAsk3](implicit t: Tag[LogIO3[F]]): LogIO3Ask[F] = new LogIO3AskImpl[F](_.get[LogIO3[F]](implicitly, t))

  /**
    * Emulate covariance. We're forced to employ these because
    * we can't make LogIO covariant, because covariant implicits
    * are broken (see scalac bug)
    *
    * Safe because `F` appears only in a covariant position
    *
    * @see https://github.com/scala/bug/issues/11427
    */
  implicit def limitedCovariance[F[+_, _], E](implicit log: LogIO2[F]): LogIO[F[E, ?]] = log.widen
  implicit def covarianceConversion[G[_], F[_]](log: LogIO[F])(implicit ev: F[_] <:< G[_]): LogIO[G] = log.widen

  implicit final class LogIO2Syntax[F[+_, +_]](private val log: LogIO2[F]) extends AnyVal {
    def fail(msg: Message)(implicit F: Error2[F], pos: CodePositionMaterializer): F[RuntimeException, Nothing] = {
      val renderingPolicy = RenderingPolicy.colorlessPolicy()
      log.createEntry(Log.Level.Crit, msg).flatMap {
        entry =>
          log.log(entry) *>
          F.fail(new RuntimeException(renderingPolicy.render(entry)))
      }
    }

    def terminate(msg: Message)(implicit F: Panic2[F], pos: CodePositionMaterializer): F[Nothing, Nothing] = {
      val renderingPolicy = RenderingPolicy.colorlessPolicy()
      log.createEntry(Log.Level.Crit, msg).flatMap {
        entry =>
          log.log(entry) *>
          F.terminate(new RuntimeException(renderingPolicy.render(entry)))
      }
    }
  }

}

object LogIO2 {
  @inline def apply[F[_, _]: LogIO2]: LogIO2[F] = implicitly

  @inline def fromLogger[F[_, _]: SyncSafe2](logger: AbstractLogger): LogIO2[F] = LogIO.fromLogger(logger)

  /**
    * Lets you refer to an implicit logger's methods without naming a variable
    *
    * {{{
    *   import logstage.LogIO2.log
    *
    *   def fn[F[_, _]: LogIO2]: F[Nothing, Unit] = {
    *     log.info(s"I'm logging with ${log}stage!")
    *   }
    * }}}
    */
  @inline def log[F[_, _]](implicit l: LogIO2[F]): l.type = l
}

object LogIO3 {
  @inline def apply[F[_, _, _]: LogIO3]: LogIO3[F] = implicitly

  @inline def fromLogger[F[_, _, _]: SyncSafe3](logger: AbstractLogger): LogIO3[F] = LogIO.fromLogger(logger)

  /**
    * Lets you refer to an implicit logger's methods without naming a variable
    *
    * {{{
    *   import logstage.LogIO3.log
    *
    *   def fn[F[_, _, _]: LogIO3]: F[Any, Nothing, Unit] = {
    *     log.info(s"I'm logging with ${log}stage!")
    *   }
    * }}}
    */
  @inline def log[F[_, _, _]](implicit l: LogIO3[F]): l.type = l
}
