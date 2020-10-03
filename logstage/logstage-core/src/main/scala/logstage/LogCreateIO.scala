package logstage

import izumi.functional.mono.SyncSafe
import izumi.fundamentals.platform.language.{CodePositionMaterializer, unused}
import izumi.logstage.api.Log.{Context, CustomContext, Entry, Message}

import scala.language.implicitConversions

trait LogCreateIO[F[_]] {
  def createEntry(logLevel: Level, message: Message)(implicit pos: CodePositionMaterializer): F[Entry]
  def createContext(logLevel: Level, customContext: CustomContext)(implicit pos: CodePositionMaterializer): F[Context]

  def widen[G[_]](implicit @unused ev: F[_] <:< G[_]): LogCreateIO[G] = this.asInstanceOf[LogCreateIO[G]]
}

object LogCreateIO {
  def apply[F[_]: LogCreateIO]: LogCreateIO[F] = implicitly

  implicit def logCreateIOSyncSafeInstance[F[_]: SyncSafe]: LogCreateIO[F] = new LogCreateIOSyncSafeInstance[F](SyncSafe[F])

  class LogCreateIOSyncSafeInstance[F[_]](protected val F: SyncSafe[F]) extends LogCreateIO[F] {
    override def createEntry(logLevel: Level, message: Message)(implicit pos: CodePositionMaterializer): F[Entry] = {
      F.syncSafe(Entry.create(logLevel, message)(pos))
    }

    override def createContext(logLevel: Level, customContext: CustomContext)(implicit pos: CodePositionMaterializer): F[Context] = {
      F.syncSafe(Context.recordContext(logLevel, customContext)(pos))
    }
  }

  /**
    * Emulate covariance. We're forced to employ these because
    * we can't make LogIO covariant, because covariant implicits
    * are broken (see scalac bug)
    *
    * Safe because `F` appears only in a covariant position
    *
    * @see https://github.com/scala/bug/issues/11427
    */
  implicit def limitedCovariance[F[+_, _], E](implicit log: LogCreateBIO[F]): LogCreateIO[F[E, ?]] = log.widen
  implicit def covarianceConversion[G[_], F[_]](log: LogCreateIO[F])(implicit @unused ev: F[_] <:< G[_]): LogCreateIO[G] = log.widen
}
