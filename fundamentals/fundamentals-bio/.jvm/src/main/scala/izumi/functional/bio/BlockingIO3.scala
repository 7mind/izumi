package izumi.functional.bio

import java.util.concurrent.ThreadPoolExecutor

import izumi.functional.bio.BlockingIOInstances.blockingIOZIO3Blocking
import zio.blocking.Blocking
import zio.internal.Executor
import zio.{IO, UIO, ZIO}

trait BlockingIO3[F[_, _, _]] extends BlockingIOInstances {

  /** Execute a blocking action in `Blocking` thread pool, current task will be safely parked until the blocking task finishes **/
  def shiftBlocking[R, E, A](f: F[R, E ,A]): F[R, E, A]

  /** Execute a blocking impure task in `Blocking` thread pool, current task will be safely parked until the blocking task finishes **/
  def syncBlocking[A](f: => A): F[Any, Throwable, A]

  /** Execute a blocking impure task in `Blocking` thread pool, current task will be safely parked until the blocking task finishes
    *
    * If canceled, the task will be killed via [[Thread#interrupt]]
    *
    * THIS IS USUALLY UNSAFE unless calling well-written libraries that specifically handle [[InterruptedException]]
    * **/
  def syncInterruptibleBlocking[A](f: => A): F[Any, Throwable, A]
}

object BlockingIO3 {
  def apply[F[_, _, _]: BlockingIO3]: BlockingIO3[F] = implicitly
}

private[bio] sealed trait BlockingIOInstances
object BlockingIOInstances extends LowPriorityBlockingIOInstances {
  // FIXME: bad encoding for lifting to 2-parameters...
  def BlockingZIOFromThreadPool[R](blockingPool: ThreadPoolExecutor): BlockingIO[ZIO[R, ?, ?]] = {
    BlockingZIO3FromThreadPool(blockingPool).asInstanceOf[BlockingIO[ZIO[R, ?, ?]]]
  }

  def BlockingZIO3FromThreadPool(blockingPool: ThreadPoolExecutor): BlockingIO3[ZIO] = {
    val executor = Executor.fromThreadPoolExecutor(_ => Int.MaxValue)(blockingPool)
    val blocking = new Blocking {
      override val blocking: Blocking.Service[Any] = new Blocking.Service[Any] {
        override val blockingExecutor: ZIO[Any, Nothing, Executor] = UIO.succeed(executor)
      }
    }
    blockingIOZIO3Blocking(blocking)
  }

  // FIXME: bad encoding for lifting to 2-parameters...
  implicit def blockingIOZIOBlocking[R](implicit serviceBlocking: Blocking): BlockingIO[ZIO[R, +?, +?]] = {
    blockingIOZIO3Blocking(serviceBlocking).asInstanceOf[BlockingIO[ZIO[R, +?, +?]]]
  }

  implicit final def blockingIOZIO3Blocking(implicit serviceBlocking: Blocking): BlockingIO3[ZIO] = new BlockingIO3[ZIO] {
    override def shiftBlocking[R, E, A](f: ZIO[R, E ,A]): ZIO[R, E, A] = serviceBlocking.blocking.blocking(f)
    override def syncBlocking[A](f: => A): ZIO[Any, Throwable, A] = serviceBlocking.blocking.blocking(IO(f))
    override def syncInterruptibleBlocking[A](f: => A): ZIO[Any, Throwable, A] = serviceBlocking.blocking.effectBlocking(f)
  }
}

trait LowPriorityBlockingIOInstances {
  type ZIOBlocking = { type l[-R, +E, +A] = ZIO[R with Blocking, E, A] }

  implicit def blockingIOZIOR[R]: BlockingIO[ZIO[R with Blocking, +?, +?]] = {
    blockingIOZIO3R.asInstanceOf[BlockingIO[ZIO[R with Blocking, +?, +?]]]
  }

  implicit final def blockingIOZIO3R: BlockingIO3[ZIOBlocking#l] = new BlockingIO3[ZIOBlocking#l] {
    override def shiftBlocking[R, E, A](f: ZIO[R with Blocking, E, A]): ZIO[R with Blocking, E, A] = ZIO.accessM(_.blocking.blocking(f))
    override def syncBlocking[A](f: => A): ZIO[Blocking, Throwable, A] = ZIO.accessM(_.blocking.blocking(IO(f)))
    override def syncInterruptibleBlocking[A](f: => A): ZIO[Blocking, Throwable, A] = ZIO.accessM(_.blocking.effectBlocking(f))
  }
}
