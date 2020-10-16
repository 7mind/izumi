package logstage.strict

import izumi.functional.bio.MonadAsk3
import izumi.reflect.TagK3

object LogBIOEnvStrict {
  def apply[F[_, _, _]: LogBIOEnvStrict]: LogBIOEnvStrict[F] = implicitly

  /**
    * Lets you carry LogBIO3 capability in environment
    *
    * {{{
    *   import logstage.{LogBIO3Strict, LogBIOEnvStrict}
    *   import logstage.LogBIOEnvStrict.log
    *   import zio.{Has. ZIO}
    *
    *   class Service[F[-_, +_, +_]: LogBIOEnvStrict] {
    *     val fn: F[Has[LogBIO3Strict[F]], Nothing, Unit] = {
    *       log.info(s"I'm logging with ${log}stage!")
    *     }
    *   }
    *
    *   new Service[ZIO](LogBIOEnvStrict.make)
    * }}}
    */
  @inline def make[F[-_, +_, +_]: MonadAsk3: TagK3]: LogBIOEnvStrict[F] = new LogBIOEnvStrictInstance[F](_.get)

  /**
    * Lets you carry LogBIO3 capability in environment
    *
    * {{{
    *   import logstage.{LogBIO3Strict, LogBIOEnvStrict}
    *   import logstage.LogBIOEnvStrict.log
    *   import zio.{Has. ZIO}
    *
    *   class Service[F[-_, +_, +_]: LogBIOEnvStrict] {
    *     val fn: F[Has[LogBIO3Strict[F]], Nothing, Unit] = {
    *       log.info(s"I'm logging with ${log}stage!")
    *     }
    *   }
    *
    *   new Service[ZIO](LogBIOEnvStrict.make)
    * }}}
    */
  @inline def log[F[-_, +_, +_]](implicit l: LogBIOEnvStrict[F]): l.type = l
}
