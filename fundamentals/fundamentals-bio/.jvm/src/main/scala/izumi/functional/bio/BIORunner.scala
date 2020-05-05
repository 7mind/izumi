package izumi.functional.bio

import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger

import izumi.functional.bio.BIOExit.ZIOExit
import zio.internal.tracing.TracingConfig
import zio.internal.{Executor, Platform, Tracing}
import zio.{Cause, IO, Runtime}

trait BIORunner[F[_, _]] {
  def unsafeRun[E, A](io: => F[E, A]): A
  def unsafeRunSyncAsEither[E, A](io: => F[E, A]): BIOExit[E, A]

  def unsafeRunAsyncAsEither[E, A](io: => F[E, A])(callback: BIOExit[E, A] => Unit): Unit
}

object BIORunner {
  def apply[F[_, _]: BIORunner]: BIORunner[F] = implicitly

  def createZIO(platform: Platform): ZIORunner = new ZIORunner(platform)

  def createZIO(
                 cpuPool: ThreadPoolExecutor
               , handler: FailureHandler = FailureHandler.Default
               , yieldEveryNFlatMaps: Int = 1024
               , tracingConfig: TracingConfig = TracingConfig.enabled
               ): ZIORunner = {
    new ZIORunner(new ZIOPlatform(cpuPool, handler, yieldEveryNFlatMaps, tracingConfig))
  }

  def newZioTimerPool(): ScheduledExecutorService = {
    Executors.newScheduledThreadPool(1, new NamedThreadFactory("zio-timer", true))
  }

  sealed trait FailureHandler
  object FailureHandler {
    final case object Default extends FailureHandler
    final case class Custom(handler: BIOExit.Failure[Any] => Unit) extends FailureHandler
  }

  class ZIORunner
  (
    val platform: Platform
  ) extends BIORunner[IO] {

    val runtime: Runtime[Unit] = Runtime((), platform)

    override def unsafeRun[E, A](io: => IO[E, A]): A = {
      unsafeRunSyncAsEither(io) match {
        case BIOExit.Success(value) =>
          value

        case failure: BIOExit.Failure[_] =>
          throw failure.trace.unsafeAttachTrace(BIOBadBranch(_))
      }
    }

    override def unsafeRunAsyncAsEither[E, A](io: => IO[E, A])(callback: BIOExit[E, A] => Unit): Unit = {
      runtime.unsafeRunAsync[E, A](io)(exitResult => callback(ZIOExit.toBIOExit(exitResult)))
    }

    override def unsafeRunSyncAsEither[E, A](io: => IO[E, A]): BIOExit[E, A] = {
      val result = runtime.unsafeRunSync(io)
      ZIOExit.toBIOExit(result)
    }
  }

  class ZIOPlatform
  (
    cpuPool: ThreadPoolExecutor
  , handler: FailureHandler
  , yieldEveryNFlatMaps: Int
  , tracingConfig: TracingConfig
  ) extends Platform {

    override val executor: Executor = Executor.fromThreadPoolExecutor(_ => yieldEveryNFlatMaps)(cpuPool)

    override val tracing: Tracing = Tracing.enabledWith(tracingConfig)

    override def reportFailure(cause: Cause[Any]): Unit = {
      handler match {
        case FailureHandler.Default =>
          // do not log interruptions
          if (!cause.interrupted) {
            System.err.println(cause.prettyPrint)
          }

        case FailureHandler.Custom(f) =>
          f(ZIOExit.toBIOExit(cause))
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
    private val threadHash  = Integer.toUnsignedString(this.hashCode())

    override def newThread(r: Runnable): Thread = {
      val newThreadNumber = threadCount.getAndIncrement()

      val thread = new Thread(threadGroup, r)
      thread.setName(s"$name-$newThreadNumber-$threadHash")
      thread.setDaemon(daemon)

      thread
    }

  }

  final case class BIOBadBranch[A](prefixMessage: String, error: A)
    extends RuntimeException(s"${prefixMessage}Typed error of class=${error.getClass.getName}: $error", null, true, false)
  object BIOBadBranch {
    def apply[A](error: A): BIOBadBranch[A] = new BIOBadBranch("", error)
  }

}
