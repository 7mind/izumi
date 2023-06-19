package logstage.strict

import izumi.logstage.api
import zio.ZIO

trait LogstageStrict {
  type LogIO2Strict[F[_, _]] = LogIOStrict[F[Nothing, _]]
  type LogIO3Strict[F[_, _, _]] = LogIOStrict[F[Any, Nothing, _]]
  type LogIO3AskStrict[F[_, _, _]] = LogIOStrict[F[LogIO3Strict[F], Nothing, _]]

  type LogIOStrict2[F[_, _]] = LogIO2Strict[F]
  val LogIOStrict2: LogIO2Strict.type = LogIO2Strict
  type LogIOStrict3[F[_, _, _]] = LogIO3Strict[F]
  val LogIOStrict3: LogIO3Strict.type = LogIO3Strict
  type LogIOStrict3Ask[F[_, _, _]] = LogIO3AskStrict[F]
  val LogIOStrict3Ask: LogIO3AskStrict.type = LogIO3AskStrict

  type LogZIOStrict = LogIO3Strict[ZIO]

  type IzStrictLogger = api.strict.IzStrictLogger
  val IzStrictLogger: api.strict.IzStrictLogger.type = api.strict.IzStrictLogger
}
