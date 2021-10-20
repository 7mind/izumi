package logstage.strict

import izumi.logstage.api

trait LogstageStrict {
  type IzStrictLogger = api.strict.IzStrictLogger
  val IzStrictLogger: api.strict.IzStrictLogger.type = api.strict.IzStrictLogger
}
