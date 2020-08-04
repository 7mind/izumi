package logstage

import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log.{CustomContext, Entry, Message}

trait AbstractLogIO[F[_]] extends UnsafeLogIO[F] {
  type Self[f[_]] <: AbstractLogIO[f]

  def log(entry: Entry): F[Unit]
  def log(logLevel: Level)(messageThunk: => Message)(implicit pos: CodePositionMaterializer): F[Unit]
  def withCustomContext(context: CustomContext): Self[F]

}
