package izumi.functional.bio

import izumi.functional.bio.data.InterruptAction
import zio.{Executor, Supervisor, ZEnvironment, ZLayer}
//import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.concurrent.Future

/**
  * Scala.js does not support running effects synchronously so only async interface is available
  */
trait UnsafeRun2[F[_, _]] {
  def unsafeRunAsync[E, A](io: => F[E, A])(callback: Exit[E, A] => Unit): Unit
  def unsafeRunAsyncAsFuture[E, A](io: => F[E, A]): Future[Exit[E, A]]

  def unsafeRunAsyncInterruptible[E, A](io: => F[E, A])(callback: Exit[E, A] => Unit): InterruptAction[F]
  def unsafeRunAsyncAsInterruptibleFuture[E, A](io: => F[E, A]): (Future[Exit[E, A]], InterruptAction[F])
}

object UnsafeRun2 {
  @inline def apply[F[_, _]](implicit ev: UnsafeRun2[F]): UnsafeRun2[F] = ev

  /**
    * @param customCpuPool             will replace [[zio.internal.ZScheduler]] if set
    * @param customBlockingPool        will replace [[zio.internal.Blocking.blockingExecutor]] if set
    * @param handler                   will add a Supervisor for fiber failure exits if not Default
    * @param otherRuntimeConfiguration zio.Runtime.* layers can be used to set other configuration options for [[zio.Runtime]]
    * @param initialEnv                initial environment
    */
  def createZIO[R](
    customCpuPool: Option[Executor] = None,
    customBlockingPool: Option[Executor] = None,
    handler: FailureHandler = FailureHandler.Default,
    otherRuntimeConfiguration: List[ZLayer[Any, Nothing, Any]] = List.empty,
    initialEnv: ZEnvironment[R] = ZEnvironment.empty,
  ): ZIORunner[R] = {
    new ZIORunner(ZIORunnerSupport.configuration(customCpuPool, customBlockingPool, handler, otherRuntimeConfiguration), initialEnv)
  }

  //  def createMonixBIO(s: Scheduler, opts: monix.bio.IO.Options): UnsafeRun2[monix.bio.IO] = new MonixBIORunner(s, opts)

  sealed trait FailureHandler
  object FailureHandler {
    case object Default extends FailureHandler
    final case class Custom(handler: Exit.Failure[Any] => Unit) extends FailureHandler
  }

  class ZIORunner[R](
    val runtimeConfiguration: ZLayer[Any, Nothing, Any], // zio.Runtime.* layers combined with `>+>`
    val initialEnv: ZEnvironment[R],
  ) extends ZIOAsyncRunner[R]

  object ZIORunner {

    def failureHandlerSupervisor(handler: FailureHandler.Custom): Supervisor[Unit] = ZIORunnerSupport.failureHandlerSupervisor(handler)
  }
}
