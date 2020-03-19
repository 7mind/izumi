package logstage

import izumi.logstage.api

package object strict extends Strict {
  override type IzStrictLogger = api.strict.IzStrictLogger
  override final val IzStrictLogger: api.strict.IzStrictLogger.type = api.strict.IzStrictLogger

  override type LogBIOStrict[F[_, _]] = LogIOStrict[F[Nothing, ?]]
  override type LogBIO3Strict[F[_, _, _]] = LogIOStrict[F[Any, Nothing, ?]]
}
