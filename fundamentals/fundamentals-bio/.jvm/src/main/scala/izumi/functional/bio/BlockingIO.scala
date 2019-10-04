package izumi.functional.bio

import java.util.concurrent.ThreadPoolExecutor

import zio.blocking.Blocking
import zio.internal.{Executor, PlatformLive}
import zio.{IO, UIO, ZIO}

trait BlockingIO[F[_, _]] {

  /** Execute a blocking action in `Blocking` thread pool, current task will be safely parked until the blocking task finishes **/
  def shiftBlocking[E, A](f: F[E ,A]): F[E, A]

  /** Execute a blocking impure task in `Blocking` thread pool, current task will be safely parked until the blocking task finishes **/
  def syncBlocking[A](f: => A): F[Throwable, A]

  /** Execute a blocking impure task in `Blocking` thread pool, current task will be safely parked until the blocking task finishes
    *
    * If canceled, the task will be killed via [[Thread.interrupt]]
    *
    * THIS IS USUALLY UNSAFE unless calling well-written libraries that specifically handle [[InterruptedException]]
    * **/
  def syncInterruptibleBlocking[A](f: => A): F[Throwable, A]

}

object BlockingIO {
  def apply[F[_, _]: BlockingIO]: BlockingIO[F] = implicitly

  def BlockingZIOFromThreadPool(blockingPool: ThreadPoolExecutor): BlockingIO[IO] = {
    val executor = Executor.fromThreadPoolExecutor(_ => Int.MaxValue)(blockingPool)
    val blocking = new Blocking {
      override val blocking: Blocking.Service[Any] = new Blocking.Service[Any] {
        override val blockingExecutor: ZIO[Any, Nothing, Executor] = UIO.succeed(executor)
      }
    }
    BlockingIO.blockingIOZIO(blocking)
  }

  implicit final def blockingIOZIO(implicit serviceBlocking: Blocking): BlockingIO[IO] = new BlockingIO[IO] {
    override def shiftBlocking[E, A](f: IO[E ,A]): IO[E, A] = serviceBlocking.blocking.blocking(f)
    override def syncBlocking[A](f: => A): IO[Throwable, A] = serviceBlocking.blocking.blocking(IO(f))
    override def syncInterruptibleBlocking[A](f: => A): IO[Throwable, A] = serviceBlocking.blocking.effectBlocking(f)
  }

}
