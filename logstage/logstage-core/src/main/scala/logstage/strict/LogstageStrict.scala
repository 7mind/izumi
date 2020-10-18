package logstage.strict

import izumi.logstage.api
import zio.Has

trait LogstageStrict {
  type IzStrictLogger = api.strict.IzStrictLogger
  val IzStrictLogger: api.strict.IzStrictLogger.type = api.strict.IzStrictLogger

  @deprecated("renamed to `LogIO3AskStrict.LogIO3AskStrictImpl`", "1.0")
  type LogBIOEnvStrictInstance[F[-_, +_, +_]] = LogIO3AskStrict.LogIO3AskStrictImpl[F]

  @deprecated("renamed to LogIO2Strict", "1.0")
  type LogBIOStrict[F[_, _]] = LogIOStrict[F[Nothing, ?]]
  @deprecated("renamed to LogIO2Strict", "1.0")
  lazy val LogBIOStrict: LogIO2Strict.type = LogIO2Strict

  @deprecated("renamed to LogIO3Strict", "1.0")
  type LogBIO3Strict[F[_, _, _]] = LogIOStrict[F[Any, Nothing, ?]]
  @deprecated("renamed to LogIO3Strict", "1.0")
  lazy val LogBIO3Strict: LogIO3Strict.type = LogIO3Strict

  @deprecated("renamed to LogIO3AskStrict", "1.0")
  type LogBIOEnvStrict[F[_, _, _]] = LogIOStrict[F[Has[LogIO3Strict[F]], Nothing, ?]]
  @deprecated("renamed to LogIO3AskStrict", "1.0")
  lazy val LogBIOEnvStrict: LogIO3AskStrict.type = LogIO3AskStrict
}
