package izumi.functional.bio

import izumi.functional.bio.Exit.ZIOExit
import izumi.functional.bio.UnsafeRun2.InterruptAction
import zio.{Executor, Fiber, Runtime, Supervisor, Trace, UIO, Unsafe, ZEnvironment, ZIO, ZLayer}

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import java.util.concurrent.{Executors, ThreadFactory}
import scala.annotation.nowarn
import scala.concurrent.{ExecutionContext, Future}

trait UnsafeRun2[F[_, _]] {
  def unsafeRun[E, A](io: => F[E, A]): A
  def unsafeRunSync[E, A](io: => F[E, A]): Exit[E, A]

  def unsafeRunAsync[E, A](io: => F[E, A])(callback: Exit[E, A] => Unit): Unit
  def unsafeRunAsyncAsFuture[E, A](io: => F[E, A]): Future[Exit[E, A]]

  def unsafeRunAsyncInterruptible[E, A](io: => F[E, A])(callback: Exit[E, A] => Unit): InterruptAction[F]
  def unsafeRunAsyncAsInterruptibleFuture[E, A](io: => F[E, A]): (Future[Exit[E, A]], InterruptAction[F])
}

object UnsafeRun2 {
  def apply[F[_, _]: UnsafeRun2]: UnsafeRun2[F] = implicitly

  /**
    * @param customCpuPool will replace [[zio.internal.ZScheduler]] if set
    * @param customBlockingPool will replace [[zio.internal.Blocking.blockingExecutor]] if set
    * @param handler will add a Supervisor for fiber failure exits if not Default
    * @param otherRuntimeConfiguration zio.Runtime.* layers can be used to set other configuration options for [[zio.Runtime]]
    * @param initialEnv initial environment
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

    override def unsafeRun[E, A](io: => ZIO[R, E, A]): A = {
      unsafeRunSync(io) match {
        case Exit.Success(value) =>
          value

        case failure: Exit.Failure[?] =>
          throw failure.trace.unsafeAttachTrace(TypedError(_))
      }
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

    override def unsafeRunSync[E, A](io: => ZIO[R, E, A]): Exit[E, A] = {
      val interrupted = new AtomicBoolean(true)
      val result = Unsafe.unsafe {
        implicit unsafe =>
          Runtime.default.unsafe.run {
            applyRuntimeConfiguration {
              ZIOExit.ZIOSignalOnNoExternalInterruptFailure(io)(ZIO.succeed(interrupted.set(false)))
            }
          }
      }
      ZIOExit.toExit(result)(interrupted.get())
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

  //  class MonixBIORunner(val s: Scheduler, val opts: monix.bio.IO.Options) extends UnsafeRun2[monix.bio.IO] {
  //    override def unsafeRun[E, A](io: => bio.IO[E, A]): A = {
  //      io.leftMap(TypedError(_)).runSyncUnsafeOpt()(s, opts, implicitly, implicitly)
  //    }
  //    override def unsafeRunSync[E, A](io: => bio.IO[E, A]): Exit[E, A] = {
  //      io.sandboxExit.runSyncUnsafeOpt()(s, opts, implicitly, implicitly)
  //    }
  //    override def unsafeRunAsync[E, A](io: => bio.IO[E, A])(callback: Exit[E, A] => Unit): Unit = {
  //      io.runAsyncOpt(exit => callback(Exit.MonixExit.toExit(exit)))(s, opts); ()
  //    }
  //    override def unsafeRunAsyncAsFuture[E, A](io: => bio.IO[E, A]): Future[Exit[E, A]] = {
  //      val p = scala.concurrent.Promise[Exit[E, A]]()
  //      unsafeRunAsync(io)(p.success)
  //      p.future
  //    }
  //    override def unsafeRunAsyncInterruptible[E, A](io: => bio.IO[E, A])(callback: Exit[E, A] => Unit): InterruptAction[bio.IO] = {
  //      val canceler = io.runAsyncOptF {
  //        case Left(e) => callback(Exit.MonixExit.toExit(e))
  //        case Right(value) => callback(Exit.Success(value))
  //      }(s, opts)
  //      InterruptAction(canceler)
  //    }
  //
  //    override def unsafeRunAsyncAsInterruptibleFuture[E, A](io: => bio.IO[E, A]): (Future[Exit[E, A]], InterruptAction[bio.IO]) = {
  //      val p = scala.concurrent.Promise[Exit[E, A]]()
  //      val canceler = unsafeRunAsyncInterruptible(io)(p.success)
  //      (p.future, canceler)
  //    }
  //  }

  final class NamedThreadFactory(name: String, daemon: Boolean) extends ThreadFactory {
    @nowarn("msg=deprecated")
    private val parentGroup =
      Option(System.getSecurityManager).fold(Thread.currentThread().getThreadGroup)(_.getThreadGroup)

    private val threadGroup = new ThreadGroup(parentGroup, name)
    private val threadCount = new AtomicInteger(1)
    private val threadHash = Integer.toUnsignedString(this.hashCode())

    override def newThread(r: Runnable): Thread = {
      val newThreadNumber = threadCount.getAndIncrement()

      val thread = new Thread(threadGroup, r)
      thread.setName(s"$name-$newThreadNumber-$threadHash")
      thread.setDaemon(daemon)

      thread
    }

  }

  object NamedThreadFactory {
    private final lazy val factory = new NamedThreadFactory("QuasiIO-cached-pool", daemon = true)

    final lazy val QuasiAsyncIdentityPool = ExecutionContext.fromExecutorService {
      Executors.newCachedThreadPool(factory)
    }

    final def QuasiAsyncIdentityThreadFactory(max: Int): ExecutionContext = {
      ExecutionContext.fromExecutorService {
        Executors.newFixedThreadPool(max, factory)
      }
    }

  }
}
