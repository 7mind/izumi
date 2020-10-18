package logstage

import izumi.functional.bio.MonadAsk3
import izumi.reflect.TagK3

object LogBIOEnv {
  def apply[F[_, _, _]: LogBIOEnv]: LogBIOEnv[F] = implicitly

  /**
    * Lets you carry LogBIO3 capability in environment
    *
    * {{{
    *   import logstage.{LogBIO3, LogBIOEnv}
    *   import logstage.LogBIOEnv.log
    *   import zio.{Has. ZIO}
    *
    *   class Service[F[-_, +_, +_]: LogBIOEnv] {
    *     val fn: F[Has[LogBIO3[F]], Nothing, Unit] = {
    *       log.info(s"I'm logging with ${log}stage!")
    *     }
    *   }
    *
    *   new Service[ZIO](LogBIOEnv.make)
    * }}}
    */
  @inline def make[F[-_, +_, +_]: MonadAsk3: TagK3]: LogBIOEnv[F] = new LogBIOEnvInstance[F](_.get)

  /**
    * Lets you carry LogBIO3 capability in environment
    *
    * {{{
    *   import logstage.{LogBIO3, LogBIOEnv}
    *   import logstage.LogBIOEnv.log
    *   import zio.{Has. ZIO}
    *
    *   class Service[F[-_, +_, +_]: LogBIOEnv] {
    *     val fn: F[Has[LogBIO3[F]], Nothing, Unit] = {
    *       log.info(s"I'm logging with ${log}stage!")
    *     }
    *   }
    *
    *   new Service[ZIO](LogBIOEnv.make)
    * }}}
    */
  @inline def log[F[-_, +_, +_]](implicit l: LogBIOEnv[F]): l.type = l
}
