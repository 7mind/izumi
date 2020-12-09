package izumi.distage.roles.model

import izumi.distage.model.definition.Lifecycle
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams

sealed trait AbstractRole[+F[_]]

/**
  * A type of role representing a persistent service.
  *
  * Will be kept running forever up until the application is interrupted.
  */
trait RoleService[+F[_]] extends AbstractRole[F] {

  /**
    * Returns a [[izumi.distage.model.definition.Lifecycle]] with the start/shutdown of a service described
    * by its `acquire`/`release` actions. The acquired service will be kept alive until the application is interrupted or
    * is otherwise finished, then the specified `release` action of the Lifecycle will run for cleanup.
    *
    * Often [[start]] is implemented using the [[izumi.distage.model.definition.Lifecycle.fork_]] method
    * to spawn a daemon fiber running the service in background.
    *
    * {{{
    * import izumi.distage.roles.model.RoleService
    * import izumi.functional.bio.{F, IO2}
    * import logstage.LogIO2
    * import logstage.LogIO2.log
    *
    * final class HelloService[F[+_, +_]: IO2: LogIO2] extends RoleService[F] {
    *   def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): Lifecycle[F[Nothing, ?], Unit] = {
    *     Lifecycle.fork_(helloServer).void
    *   }
    *
    *   val helloServer: F[Throwable, Unit] = {
    *     (for {
    *       name <- F.syncThrowable { Console.in.readLine() }
    *       _    <- log.info(s"Hello $name!")
    *     } yield ()).forever
    *   }
    * }
    * }}}
    *
    * @note Resource initialization must be finite  application startup won't progress until the `acquire` phase of the returned Lifecycle is finished.
    * You may start a separate thread / fiber, etc during resource initialization.
    * All the shutdown logic has to be implemented in the resource finalizer.
    */
  def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): Lifecycle[F, Unit]

}

/**
  * A role representing a one-shot task. Shouldn't block forever.
  */
trait RoleTask[+F[_]] extends AbstractRole[F] {

  /**
    * Application startup wouldn't progress until the effect finishes.
    */
  def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): F[Unit]

}
