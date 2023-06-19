package izumi.functional.bio

import izumi.functional.bio.Exit.ZIOExit
import izumi.functional.bio.UnsafeRun2.InterruptAction
import zio.{Executor, Fiber, Runtime, Supervisor, Trace, UIO, Unsafe, ZEnvironment, ZIO, ZLayer}
//import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.AtomicBoolean
import scala.annotation.unused
import scala.concurrent.{ExecutionContext, Future}

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
    val runtimeConfiguration = {
      val cpuLayer = customCpuPool.fold(ZLayer.empty)(ec => Runtime.setExecutor(ec))
      val blockingLayer = customBlockingPool.fold(ZLayer.empty)(ec => Runtime.setBlockingExecutor(ec))
      val handlerSupervisorLayer = handler match {
        case FailureHandler.Default => ZLayer.empty
        case handler @ FailureHandler.Custom(_) => Runtime.addSupervisor(ZIORunner.failureHandlerSupervisor(handler))
      }
      cpuLayer >+> blockingLayer >+> handlerSupervisorLayer >+>
      otherRuntimeConfiguration.foldLeft(ZLayer.empty)(_ >+> _)
    }

    new ZIORunner(
      runtimeConfiguration,
      initialEnv,
    )
  }

  //  def createMonixBIO(s: Scheduler, opts: monix.bio.IO.Options): UnsafeRun2[monix.bio.IO] = new MonixBIORunner(s, opts)

  /**
    * @param interrupt May semantically block until the target computation either finishes completely or finishes running
    *                  its finalizers, depending on the underlying effect type.
    */
  final case class InterruptAction[F[_, _]](interrupt: F[Nothing, Unit]) extends AnyVal

  sealed trait FailureHandler
  object FailureHandler {
    case object Default extends FailureHandler
    final case class Custom(handler: Exit.Failure[Any] => Unit) extends FailureHandler
  }

  class ZIORunner[R](
    val runtimeConfiguration: ZLayer[Any, Nothing, Any], // zio.Runtime.* layers combined with `++`
    val initialEnv: ZEnvironment[R],
  ) extends UnsafeRun2[ZIO[R, +_, +_]] {

    def applyRuntimeConfiguration[E, A](io: => ZIO[R, E, A]): ZIO[Any, E, A] = {
      io.provideEnvironment(initialEnv)
        .provideLayer(runtimeConfiguration)
    }

    override def unsafeRunAsync[E, A](io: => ZIO[R, E, A])(callback: Exit[E, A] => Unit): Unit = {
      val interrupted = new AtomicBoolean(true)
      Unsafe.unsafe {
        implicit unsafe =>
          Runtime.default.unsafe
            .fork(applyRuntimeConfiguration {
              ZIOExit.ZIOSignalOnNoExternalInterruptFailure(io)(ZIO.succeed(interrupted.set(false)))
            })
            .unsafe
            .addObserver(exitResult => callback(ZIOExit.toExit(exitResult)(interrupted.get())))
      }
    }

    override def unsafeRunAsyncAsFuture[E, A](io: => ZIO[R, E, A]): Future[Exit[E, A]] = {
      val p = scala.concurrent.Promise[Exit[E, A]]()
      unsafeRunAsync(io)(p.success)
      p.future
    }

    override def unsafeRunAsyncInterruptible[E, A](io: => ZIO[R, E, A])(callback: Exit[E, A] => Unit): InterruptAction[ZIO[R, +_, +_]] = {
      val interrupted = new AtomicBoolean(true)

      val cancelerEffect = Unsafe.unsafe {
        implicit u =>
          Runtime.default.unsafe
            .run(applyRuntimeConfiguration {
              ZIO
                .acquireReleaseExitWith(ZIO.descriptor)(
                  (descriptor, exit: zio.Exit[E, A]) =>
                    ZIO.succeed {
                      exit match {
                        case zio.Exit.Failure(cause) if !cause.interruptors.forall(_ == descriptor.id) =>
                          ()
                        case _ =>
                          callback(ZIOExit.toExit(exit)(interrupted.get()))
                      }
                    }
                )(_ => ZIOExit.ZIOSignalOnNoExternalInterruptFailure(io)(ZIO.succeed(interrupted.set(false))))
                .interruptible
                .forkDaemon
                .map(_.interrupt.unit)
            }).getOrThrowFiberFailure()
      }

      InterruptAction(cancelerEffect)
    }

    override def unsafeRunAsyncAsInterruptibleFuture[E, A](io: => ZIO[R, E, A]): (Future[Exit[E, A]], InterruptAction[ZIO[R, +_, +_]]) = {
      val p = scala.concurrent.Promise[Exit[E, A]]()
      val canceler = unsafeRunAsyncInterruptible(io)(p.success)
      (p.future, canceler)
    }
  }

  object ZIORunner {

    def failureHandlerSupervisor(handler: FailureHandler.Custom): Supervisor[Unit] = new Supervisor[Unit] {
      // @formatter:off
      override def value(implicit trace: Trace): UIO[Unit] = ZIO.unit
      override def onStart[R, E, A](environment: ZEnvironment[R], effect: ZIO[R, E, A], parent: Option[Fiber.Runtime[Any, Any]], fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit = ()
      // @formatter:on

      override def onEnd[R, E, A](exit: zio.Exit[E, A], fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit = {
        exit match {
          case zio.Exit.Success(_) => ()
          case zio.Exit.Failure(cause) =>
            handler.handler.apply(ZIOExit.toExit(cause)(outerInterruptionConfirmed = true))
        }
      }
    }
  }

  object NamedThreadFactory {
    final lazy val QuasiAsyncIdentityPool: ExecutionContext = ExecutionContext.Implicits.global
    final def QuasiAsyncIdentityThreadFactory(@unused max: Int): ExecutionContext = QuasiAsyncIdentityPool
  }
}
