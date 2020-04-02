package logstage.strict

import izumi.logstage.api
import zio.Has

trait Strict {
  type IzStrictLogger = api.strict.IzStrictLogger
  val IzStrictLogger: api.strict.IzStrictLogger.type = api.strict.IzStrictLogger

  type LogBIOStrict[F[_, _]] = LogIOStrict[F[Nothing, ?]]
  type LogBIO3Strict[F[_, _, _]] = LogIOStrict[F[Any, Nothing, ?]]
  type LogBIOEnvStrict[F[_, _, _]] = LogBIOStrict[F[Has[LogBIO3Strict[F]], ?, ?]]
}
