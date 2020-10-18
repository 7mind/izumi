package izumi.functional.bio

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, ScheduledExecutorService, ThreadFactory, ThreadPoolExecutor}

import izumi.functional.bio.Exit.ZIOExit
import monix.bio
import monix.execution.Scheduler
import zio.internal.tracing.TracingConfig
import zio.internal.{Executor, Platform, Tracing}
import zio.{Cause, IO, Runtime, Supervisor}

import scala.concurrent.Future

trait UnsafeRun2[F[_, _]] {
  def unsafeRun[E, A](io: => F[E, A]): A
  def unsafeRunSync[E, A](io: => F[E, A]): Exit[E, A]
  def unsafeRunAsync[E, A](io: => F[E, A])(callback: Exit[E, A] => Unit): Unit
  def unsafeRunAsyncAsFuture[E, A](io: => F[E, A]): Future[Exit[E, A]]

  @deprecated("use `unsafeRunSync`", "1.0")
  final def unsafeRunSyncAsEither[E, A](io: => F[E, A]): Exit[E, A] = unsafeRunSync(io)
  @deprecated("use `unsafeRunAsync`", "1.0")
  final def unsafeRunAsyncAsEither[E, A](io: => F[E, A])(callback: Exit[E, A] => Unit): Unit = unsafeRunAsync(io)(callback)
}

object UnsafeRun2 {
  def apply[F[_, _]: UnsafeRun2]: UnsafeRun2[F] = implicitly

  def createZIO(platform: Platform): ZIORunner = new ZIORunner(platform)

  def createZIO(
    cpuPool: ThreadPoolExecutor,
    handler: FailureHandler = FailureHandler.Default,
    yieldEveryNFlatMaps: Int = 1024,
    tracingConfig: TracingConfig = TracingConfig.enabled,
  ): ZIORunner = {
    new ZIORunner(new ZIOPlatform(cpuPool, handler, yieldEveryNFlatMaps, tracingConfig))
  }

  def newZioTimerPool(): ScheduledExecutorService = {
    Executors.newScheduledThreadPool(1, new NamedThreadFactory("zio-timer", true))
  }

  def createMonixBIO(s: Scheduler, opts: monix.bio.IO.Options): UnsafeRun2[monix.bio.IO] = new MonixBIORunner(s, opts)

  class MonixBIORunner(val s: Scheduler, val opts: monix.bio.IO.Options) extends UnsafeRun2[monix.bio.IO] {
    override def unsafeRun[E, A](io: => bio.IO[E, A]): A = {
      io.leftMap(TypedError(_)).runSyncUnsafeOpt()(s, opts, implicitly, implicitly)
    }
    override def unsafeRunSync[E, A](io: => bio.IO[E, A]): Exit[E, A] = {
      io.sandboxExit.runSyncUnsafeOpt()(s, opts, implicitly, implicitly)
    }
    override def unsafeRunAsync[E, A](io: => bio.IO[E, A])(callback: Exit[E, A] => Unit): Unit = {
      io.runAsyncOpt(exit => callback(Exit.MonixExit.toExit(exit)))(s, opts); ()
    }
    override def unsafeRunAsyncAsFuture[E, A](io: => bio.IO[E, A]): Future[Exit[E, A]] = {
      io.sandboxExit.runToFutureOpt(s, opts, implicitly)
    }
  }

  sealed trait FailureHandler
  object FailureHandler {
    final case object Default extends FailureHandler
    final case class Custom(handler: Exit.Failure[Any] => Unit) extends FailureHandler
  }

  class ZIORunner(
    val platform: Platform
  ) extends UnsafeRun2[IO] {

    val runtime: Runtime[Unit] = Runtime((), platform)

    override def unsafeRun[E, A](io: => IO[E, A]): A = {
      unsafeRunSync(io) match {
        case Exit.Success(value) =>
          value

        case failure: Exit.Failure[_] =>
          throw failure.trace.unsafeAttachTrace(TypedError(_))
      }
    }

    override def unsafeRunAsync[E, A](io: => IO[E, A])(callback: Exit[E, A] => Unit): Unit = {
      runtime.unsafeRunAsync[E, A](io)(exitResult => callback(ZIOExit.toExit(exitResult)))
    }

    override def unsafeRunSync[E, A](io: => IO[E, A]): Exit[E, A] = {
      val result = runtime.unsafeRunSync(io)
      ZIOExit.toExit(result)
    }

    override def unsafeRunAsyncAsFuture[E, A](io: => IO[E, A]): Future[Exit[E, A]] = {
      val p = scala.concurrent.Promise[Exit[E, A]]()
      unsafeRunAsync(io)(p.success)
      p.future
    }
  }

  class ZIOPlatform(
    cpuPool: ThreadPoolExecutor,
    handler: FailureHandler,
    yieldEveryNFlatMaps: Int,
    tracingConfig: TracingConfig,
  ) extends Platform {

    override val executor: Executor = Executor.fromThreadPoolExecutor(_ => yieldEveryNFlatMaps)(cpuPool)
    override val tracing: Tracing = Tracing.enabledWith(tracingConfig)
    override val supervisor: Supervisor[Any] = Supervisor.none

    override def reportFailure(cause: Cause[Any]): Unit = {
      handler match {
        case FailureHandler.Default =>
          // do not log interruptions
          if (!cause.interrupted) {
            System.err.println(cause.prettyPrint)
          }

        case FailureHandler.Custom(f) =>
          f(ZIOExit.toExit(cause))
      }
    }

    override def fatal(t: Throwable): Boolean = t.isInstanceOf[VirtualMachineError]

    override def reportFatal(t: Throwable): Nothing = {
      t.printStackTrace()
      sys.exit(-1)
    }
  }

  final class NamedThreadFactory(name: String, daemon: Boolean) extends ThreadFactory {

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

  @deprecated("renamed to TypedError", "1.0")
  type BIOBadBranch[+A] = TypedError[A]
  @deprecated("renamed to TypedError", "1.0")
  lazy val BIOBadBranch: TypedError.type = TypedError

}
