package izumi.logstage.api.logger

import izumi.fundamentals.platform.language.{CodePositionMaterializer, unused}
import izumi.logstage.api.Log.{CustomContext, Entry, Level, Message}
import logstage.UnsafeLogIO

trait AbstractLogIO[F[_]] extends UnsafeLogIO[F] {
  type Self[f[_]] <: AbstractLogIO[f]

  def log(entry: Entry): F[Unit]
  def log(logLevel: Level)(messageThunk: => Message)(implicit pos: CodePositionMaterializer): F[Unit]

  def withCustomContext(context: CustomContext): Self[F]
  final def apply(context: CustomContext): Self[F] = withCustomContext(context)

  override def widen[G[_]](implicit @unused ev: F[_] <:< G[_]): AbstractLogIO[G] = this.asInstanceOf[AbstractLogIO[G]]
}
