package logstage.strict

import izumi.logstage.api

trait Strict {
  type IzStrictLogger = api.strict.IzStrictLogger
  val IzStrictLogger: api.strict.IzStrictLogger.type = api.strict.IzStrictLogger

  type LogBIOStrict[F[_, _]] = LogIOStrict[F[Nothing, ?]]
}
