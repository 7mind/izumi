package logstage.strict

import izumi.functional.bio.{BIOMonadAsk, SyncSafe3}
import izumi.logstage.api.logger.AbstractLogger

object LogBIO3Strict {
  def apply[F[_, _, _]: LogBIO3Strict]: LogBIO3Strict[F] = implicitly

  def fromLogger[F[_, _, _]: SyncSafe3](logger: AbstractLogger): LogBIO3Strict[F] = {
    LogIOStrict.fromLogger(logger)
  }

  /** Lets you carry LogBIO capability in environment */
  def log[F[-_, +_, +_]: BIOMonadAsk: zio.TaggedF3] = new LogBIO3StrictEnvInstance[F](_.get)
}
