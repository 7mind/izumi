package com.github.pshirshov.izumi.functional.bio

import java.util
import java.util.concurrent._

import scalaz.zio.Exit.Cause
import scalaz.zio._
import scalaz.zio.internal.{Executor, NamedThreadFactory, Platform, PlatformLive}

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
               , handler: DefaultHandler = DefaultHandler.Default
               , yieldEveryNFlatMaps: Int = 1024
               ): BIORunner[IO] = {
    new ZIORunner(new ZIOEnvBase(cpuPool, handler, yieldEveryNFlatMaps))
  }

  def newZioTimerPool(): ScheduledExecutorService = Executors.newScheduledThreadPool(1, new NamedThreadFactory("zio-timer", true))

  sealed trait DefaultHandler

  object DefaultHandler {
    final case object Default extends DefaultHandler
    final case class Custom(handler: BIOExit.Failure[Any] => Unit) extends DefaultHandler
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

        case e: BIOExit.Error[_] =>
          e.error match {
            case t: Throwable =>
              throw t
            case o =>
              throw FiberFailure(Cause.fail(o))
          }
        case e: BIOExit.Termination =>
          throw e.compoundException
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
  , handler: DefaultHandler
  , yieldEveryNFlatMaps: Int
  ) extends Platform with BIOExit.ZIO {

    private[this] final val cpu = PlatformLive.ExecutorUtil.fromThreadPoolExecutor(_ => yieldEveryNFlatMaps)(cpuPool)

    override def reportFailure(cause: Exit.Cause[_]): Unit = {
      handler match {
        case DefaultHandler.Default =>
          // do not log interruptions
          if (!cause.interrupted) {
            println(cause.toString)
          }

        case DefaultHandler.Custom(f) =>
          f(toBIOExit(cause))
      }
    }

    override def executor: Executor = cpu

    override def fatal(t: Throwable): Boolean = t.isInstanceOf[VirtualMachineError]

    override def newWeakHashMap[A, B](): util.Map[A, B] = new util.WeakHashMap[A, B]()
  }

}
