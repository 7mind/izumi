package logstage

import izumi.functional.bio.SyncSafe1
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log.{Context, CustomContext, Entry, Message}

import scala.annotation.unused
import scala.language.implicitConversions

trait LogCreateIO[F[_]] {
  def createEntry(logLevel: Level, message: Message)(implicit pos: CodePositionMaterializer): F[Entry]
  def createContext(logLevel: Level, customContext: CustomContext)(implicit pos: CodePositionMaterializer): F[Context]

  def widen[G[_]](implicit @unused ev: F[AnyRef] <:< G[AnyRef]): LogCreateIO[G] = this.asInstanceOf[LogCreateIO[G]]
}

object LogCreateIO extends LowPriorityLogCreateIOInstances {
  @inline def apply[F[_]: LogCreateIO]: LogCreateIO[F] = implicitly

  implicit def logCreateIOSyncSafeInstance[F[_]: SyncSafe1]: LogCreateIO[F] = new LogCreateIOSyncSafeInstance[F](SyncSafe1[F])

  class LogCreateIOSyncSafeInstance[F[_]](protected val F: SyncSafe1[F]) extends LogCreateIO[F] {
    override def createEntry(logLevel: Level, message: Message)(implicit pos: CodePositionMaterializer): F[Entry] = {
      F.syncSafe(Entry.create(logLevel, message)(pos))
    }

    override def createContext(logLevel: Level, customContext: CustomContext)(implicit pos: CodePositionMaterializer): F[Context] = {
      F.syncSafe(Context.recordContext(logLevel, customContext)(pos))
    }
  }

  implicit def covarianceConversion[G[_], F[_]](log: LogCreateIO[F])(implicit ev: F[AnyRef] <:< G[AnyRef]): LogCreateIO[G] = log.widen
}

sealed trait LowPriorityLogCreateIOInstances {
  /**
    * Emulate covariance. We're forced to employ these because
    * we can't make LogIO covariant, because covariant implicits
    * are broken (see scalac bug)
    *
    * Safe because `F` appears only in a covariant position
    *
    * @see https://github.com/scala/bug/issues/11427
    */
  implicit def limitedCovariance2[F[+_, _], E](implicit log: LogCreateIO2[F]): LogCreateIO[F[E, _]] = log.widen
  implicit def limitedCovariance3[F[-_, +_, _], R, E](implicit log: LogCreateIO3[F]): LogCreateIO[F[R, E, _]] = log.widen
}

object LogCreateIO2 {
  @inline def apply[F[_, _]: LogCreateIO2]: LogCreateIO2[F] = implicitly
}

object LogCreateIO3 {
  @inline def apply[F[_, _, _]: LogCreateIO3]: LogCreateIO3[F] = implicitly
}
