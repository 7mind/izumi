package logstage

import izumi.logstage.api
import zio.{Has, ZIO}

package object strict extends LogstageStrict {
  type LogIO2Strict[F[_, _]] = LogIOStrict[F[Nothing, _]]
  type LogIO3Strict[F[_, _, _]] = LogIOStrict[F[Any, Nothing, _]]
  type LogIO3AskStrict[F[_, _, _]] = LogIOStrict[F[Has[LogIO3Strict[F]], Nothing, _]]

  type LogZIOStrict = Has[LogIO3Strict[ZIO]]

  override type IzStrictLogger = api.strict.IzStrictLogger
  override final val IzStrictLogger: api.strict.IzStrictLogger.type = api.strict.IzStrictLogger
}
