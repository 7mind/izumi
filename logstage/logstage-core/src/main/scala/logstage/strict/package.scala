package logstage

import izumi.logstage.api
import zio.Has

package object strict extends Strict {
  override type IzStrictLogger = api.strict.IzStrictLogger
  override final val IzStrictLogger: api.strict.IzStrictLogger.type = api.strict.IzStrictLogger

  override type LogBIOStrict[F[_, _]] = LogIOStrict[F[Nothing, ?]]
  override type LogBIO3Strict[F[_, _, _]] = LogIOStrict[F[Any, Nothing, ?]]
  override type LogBIOEnvStrict[F[_, _, _]] = LogBIOStrict[F[Has[LogBIO3Strict[F]], ?, ?]]
}
