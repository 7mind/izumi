package com.github.pshirshov.izumi.functional.bio

import java.util
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger

import zio.Exit.Cause
import zio._
import zio.internal.tracing.TracingConfig
import zio.internal.{Executor, Platform, PlatformLive, Tracing}

trait BIORunner[F[_, _]] {
  def unsafeRun[E, A](io: F[E, A]): A

  def unsafeRunSyncAsEither[E, A](io: F[E, A]): BIOExit[E, A]

  def unsafeRunAsyncAsEither[E, A](io: F[E, A])(callback: BIOExit[E, A] => Unit): Unit
}

object BIORunner {
  def apply[F[_, _]: BIORunner]: BIORunner[F] = implicitly

  def createZIO(platform: Platform): BIORunner[IO] = new ZIORunner(platform)

  def createZIO(
                 cpuPool: ThreadPoolExecutor
               , handler: FailureHandler = FailureHandler.Default
               , yieldEveryNFlatMaps: Int = 1024
               , tracingConfig: TracingConfig = TracingConfig.enabled
               ): BIORunner[IO] = {
    new ZIORunner(new ZIOEnvBase(cpuPool, handler, yieldEveryNFlatMaps, tracingConfig))
  }

  def newZioTimerPool(): ScheduledExecutorService = Executors.newScheduledThreadPool(1, new NamedThreadFactory("zio-timer", true))

  sealed trait FailureHandler
  object FailureHandler {
    final case object Default extends FailureHandler
    final case class Custom(handler: BIOExit.Failure[Any] => Unit) extends FailureHandler
  }

  class ZIORunner
  (
    val platform: Platform
  ) extends BIORunner[IO] with BIOExit.ZIO {

    val runtime = Runtime((), platform)

    override def unsafeRun[E, A](io: IO[E, A]): A = {
      unsafeRunSyncAsEither(io) match {
        case BIOExit.Success(value) =>
          value

        case BIOExit.Error(error, trace) =>
          error match {
            case t: Throwable =>
              throw t
            case o =>
              throw FiberFailure(Cause.fail(o))
          }

        case BIOExit.Termination(compoundException, _, trace) =>
          throw compoundException
      }
    }

    override def unsafeRunAsyncAsEither[E, A](io: IO[E, A])(callback: BIOExit[E, A] => Unit): Unit = {
      runtime.unsafeRunAsync[E, A](io)(exitResult => callback(toBIOExit(exitResult)))
    }

    override def unsafeRunSyncAsEither[E, A](io: IO[E, A]): BIOExit[E, A] = {
      val result = runtime.unsafeRunSync(io)
      toBIOExit(result)
    }
  }

  class ZIOEnvBase
  (
    cpuPool: ThreadPoolExecutor
  , handler: FailureHandler
  , yieldEveryNFlatMaps: Int
  , tracingConfig: TracingConfig
  ) extends Platform with BIOExit.ZIO {

    private[this] final val cpu = PlatformLive.ExecutorUtil.fromThreadPoolExecutor(_ => yieldEveryNFlatMaps)(cpuPool)

    override def reportFailure(cause: Exit.Cause[_]): Unit = {
      handler match {
        case FailureHandler.Default =>
          // do not log interruptions
          if (!cause.interrupted) {
            println(cause.toString)
          }

        case FailureHandler.Custom(f) =>
          f(toBIOExit(cause))
      }
    }

    override def executor: Executor = cpu

    override def fatal(t: Throwable): Boolean = t.isInstanceOf[VirtualMachineError]

    override def tracing: Tracing = Tracing.enabledWith(tracingConfig)

    override def reportFatal(t: Throwable): Nothing = {
      t.printStackTrace()
      sys.exit(-1)
    }

    override def newWeakHashMap[A, B](): util.Map[A, B] = new util.WeakHashMap[A, B]()
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

}
