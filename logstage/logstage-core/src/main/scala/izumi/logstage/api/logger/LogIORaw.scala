package izumi.logstage.api.logger

import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log
import izumi.logstage.api.Log.Level
import izumi.logstage.api.rendering.AnyEncoded

final class LogIORaw[F[_], E <: AnyEncoded](
  delegate: AbstractLogIO[F]
) extends EncodingAwareAbstractLogIO[F, E]
  with AbstractMacroRawLogIO[F] {
  override type Self[f[_]] = LogIORaw[f, E]

  override def log(entry: Log.Entry): F[Unit] = delegate.log(entry)
  override def log(logLevel: Level)(messageThunk: => Log.Message)(implicit pos: CodePositionMaterializer): F[Unit] = delegate.log(logLevel)(messageThunk)
  override def withCustomContext(context: Log.CustomContext): Self[F] = new LogIORaw(delegate.withCustomContext(context))
  override def unsafeLog(entry: Log.Entry): F[Unit] = delegate.unsafeLog(entry)
  override def acceptable(loggerId: Log.LoggerId, logLevel: Level): F[Boolean] = delegate.acceptable(loggerId, logLevel)
  override def acceptable(logLevel: Level)(implicit pos: CodePositionMaterializer): F[Boolean] = delegate.acceptable(logLevel)
  override def createEntry(logLevel: Level, message: Log.Message)(implicit pos: CodePositionMaterializer): F[Log.Entry] = delegate.createEntry(logLevel, message)
  override def createContext(logLevel: Level, customContext: Log.CustomContext)(implicit pos: CodePositionMaterializer): F[Log.Context] =
    delegate.createContext(logLevel, customContext)

  override def widen[G[_]](implicit ev: F[AnyRef] <:< G[AnyRef]): LogIORaw[G, E] = this.asInstanceOf[LogIORaw[G, E]]
}
