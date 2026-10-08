package izumi.functional.bio

import izumi.functional.bio.Exit.ZIOExit
import izumi.functional.bio.UnsafeRun2.FailureHandler
import izumi.functional.bio.data.InterruptAction
import zio._izumicompat_.__ZIOSucceedCompat.zioSucceed
import zio.{Executor, Fiber, FiberId, Runtime, Supervisor, Trace, UIO, Unsafe, ZEnvironment, ZIO, ZLayer}

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Future

private[bio] abstract class ZIOAsyncRunner[R] extends UnsafeRun2[ZIO[R, +_, +_]] {
  def runtimeConfiguration: ZLayer[Any, Nothing, Any]
  def initialEnv: ZEnvironment[R]

  lazy val runtime: Runtime[R] = {
    Runtime.unsafe
      .fromLayer(runtimeConfiguration)(using implicitly[zio.Trace], Unsafe)
      .mapEnvironment(_ => initialEnv)
  }

  override def unsafeRunAsync[E, A](io: => ZIO[R, E, A])(callback: Exit[E, A] => Unit): Unit = {
    val interrupted = new AtomicBoolean(true)
    val fiber = runtime.unsafe.fork(ZIOExit.ZIOSignalOnNoExternalInterruptFailure(io)(zioSucceed(interrupted.set(false))))(using implicitly[zio.Trace], Unsafe)
    fiber.unsafe.addObserver(exitResult => callback(ZIOExit.toExit(exitResult)(interrupted.get())))(using Unsafe)
  }

  override def unsafeRunAsyncAsFuture[E, A](io: => ZIO[R, E, A]): Future[Exit[E, A]] = {
    val p = scala.concurrent.Promise[Exit[E, A]]()
    unsafeRunAsync(io)(p.success)
    p.future
  }

  override def unsafeRunAsyncInterruptible[E, A](io: => ZIO[R, E, A])(callback: Exit[E, A] => Unit): InterruptAction[ZIO[R, +_, +_]] = {
    val interrupted = new AtomicBoolean(true)
    val fiber = runtime.unsafe.fork(ZIOExit.ZIOSignalOnNoExternalInterruptFailure(io)(zioSucceed(interrupted.set(false))))(using implicitly[zio.Trace], Unsafe)
    fiber.unsafe.addObserver(exitResult => callback(ZIOExit.toExit(exitResult)(interrupted.get())))(using Unsafe)
    InterruptAction(fiber.interruptAs(FiberId.None).void)
  }

  override def unsafeRunAsyncAsInterruptibleFuture[E, A](io: => ZIO[R, E, A]): (Future[Exit[E, A]], InterruptAction[ZIO[R, +_, +_]]) = {
    val p = scala.concurrent.Promise[Exit[E, A]]()
    val canceler = unsafeRunAsyncInterruptible(io)(p.success)
    (p.future, canceler)
  }
}

private[bio] object ZIORunnerSupport {
  def configuration(
    customCpuPool: Option[Executor],
    customBlockingPool: Option[Executor],
    handler: FailureHandler,
    otherRuntimeConfiguration: List[ZLayer[Any, Nothing, Any]],
  ): ZLayer[Any, Nothing, Any] = {
    val cpuLayer = customCpuPool.fold(ZLayer.empty)(ec => Runtime.setExecutor(ec))
    val blockingLayer = customBlockingPool.fold(ZLayer.empty)(ec => Runtime.setBlockingExecutor(ec))
    val handlerSupervisorLayer = handler match {
      case FailureHandler.Default => ZLayer.empty
      case handler @ FailureHandler.Custom(_) => Runtime.addSupervisor(failureHandlerSupervisor(handler))
    }
    cpuLayer >+> blockingLayer >+> handlerSupervisorLayer >+>
    otherRuntimeConfiguration.foldLeft(ZLayer.empty)(_ >+> _)
  }

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
