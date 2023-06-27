package logstage

import izumi.logstage.api
import zio.ZIO

package object strict extends LogstageStrict {
  override type LogIO2Strict[F[_, _]] = LogIOStrict[F[Nothing, _]]
  override type LogIO3Strict[F[_, _, _]] = LogIOStrict[F[Any, Nothing, _]]
  override type LogIO3AskStrict[F[_, _, _]] = LogIOStrict[F[LogIO3Strict[F], Nothing, _]]

  override type LogIOStrict2[F[_, _]] = LogIO2Strict[F]
  override val LogIOStrict2: LogIO2Strict.type = LogIO2Strict
  override type LogIOStrict3[F[_, _, _]] = LogIO3Strict[F]
  override val LogIOStrict3: LogIO3Strict.type = LogIO3Strict
  override type LogIOStrict3Ask[F[_, _, _]] = LogIO3AskStrict[F]
  override val LogIOStrict3Ask: LogIO3AskStrict.type = LogIO3AskStrict

  override type LogZIOStrict = LogIO3Strict[ZIO]

  override type IzStrictLogger = api.strict.IzStrictLogger
  override final val IzStrictLogger: api.strict.IzStrictLogger.type = api.strict.IzStrictLogger
}
