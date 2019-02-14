package com.github.pshirshov.izumi.functional.bio

import java.util
import java.util.concurrent._

import scalaz.zio._
import scalaz.zio.internal.{Env, Executor, NamedThreadFactory, Scheduler}
import scalaz.zio.internal.impls.Env

trait BIORunner[F[_, _]] {
  def unsafeRun[E, A](io: F[E, A]): A

  def unsafeRunSyncAsEither[E, A](io: F[E, A]): BIOExit[E, A]

  def unsafeRunAsyncAsEither[E, A](io: F[E, A])(callback: BIOExit[E, A] => Unit): Unit
}

object BIORunner {
  def apply[F[_, _]: BIORunner]: BIORunner[F] = implicitly

  def createZIO(env: Env): BIORunner[IO] = new ZIORunner(env)

  def createZIO(
                 cpuPool: ThreadPoolExecutor
               , blockingPool: ThreadPoolExecutor
               , handler: DefaultHandler = DefaultHandler.Default
               , yieldEveryNFlatMaps: Int = 1024
               , timerPool: ScheduledExecutorService = newZioTimerPool()
               ): BIORunner[IO] = {
    new ZIORunner(new ZIOEnvBase(cpuPool, blockingPool, handler, yieldEveryNFlatMaps, timerPool))
  }

  private[this] def newZioTimerPool(): ScheduledExecutorService = Executors.newScheduledThreadPool(1, new NamedThreadFactory("zio-timer", true))

  sealed trait DefaultHandler

  object DefaultHandler {
    case object Default extends DefaultHandler
    case class Custom(handler: BIOExit.Failure[Any] => IO[Nothing, Unit]) extends DefaultHandler
  }

  class ZIORunner(env: Env) extends BIORunner[IO] {
    override def unsafeRun[E, A](io: IO[E, A]): A = {
      env.unsafeRun(io)
    }
    override def unsafeRunAsyncAsEither[E, A](io: IO[E, A])(callback: BIOExit[E, A] => Unit): Unit = {
      env.unsafeRunAsync[E, A](io, exitResult => callback(BIO.BIOZio.toBIOExit(exitResult)))
    }
    override def unsafeRunSyncAsEither[E, A](io: IO[E, A]): BIOExit[E, A] = {
      val result = env.unsafeRunSync(io)
      BIO.BIOZio.toBIOExit(result)
    }
  }

  class ZIOEnvBase
  (
    cpuPool: ThreadPoolExecutor
  , blockingBool: ThreadPoolExecutor
  , handler: DefaultHandler
  , yieldEveryNFlatMaps: Int
  , timerPool: ScheduledExecutorService
  ) extends Env {

    private[this] final val cpu = Env.fromThreadPoolExecutor(Executor.Yielding, _ => yieldEveryNFlatMaps)(cpuPool)
    private[this] final val blocking = Env.fromThreadPoolExecutor(Executor.Unyielding, _ => Int.MaxValue)(blockingBool)

    override def reportFailure(cause: Exit.Cause[_]): IO[Nothing, _] = {
      handler match {
        case DefaultHandler.Default =>
          if (cause.interrupted) IO.unit // do not log interruptions
          else IO.sync(println(cause.toString))

        case DefaultHandler.Custom(f) =>
          f(BIO.BIOZio.toBIOExit(cause))
      }
    }

    override def executor(tpe: Executor.Role): Executor = tpe match {
      case Executor.Yielding => cpu
      case Executor.Unyielding => blocking
    }

    override def scheduler: Scheduler = Scheduler.fromScheduledExecutorService(timerPool)

    override def nonFatal(t: Throwable): Boolean = !t.isInstanceOf[VirtualMachineError]

    override def newWeakHashMap[A, B](): util.Map[A, B] = new util.WeakHashMap[A, B]()
  }

}
