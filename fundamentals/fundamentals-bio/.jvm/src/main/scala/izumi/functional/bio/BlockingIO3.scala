package izumi.functional.bio

import java.util.concurrent.ThreadPoolExecutor

import zio.blocking.Blocking
import zio.internal.Executor
import zio.{IO, UIO, ZIO}

trait BlockingIO3[F[_, _, _]] {

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

object BlockingIO3 extends LowPriorityBlockingIO3Instances {
  def apply[F[_, _, _]: BlockingIO3](implicit dummy: DummyImplicit): BlockingIO3[F] = implicitly
  def apply[F[_, _]: BlockingIO]: BlockingIO[F] = implicitly

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
    blockingIOZIO3(blocking)
  }

  // FIXME: bad encoding for lifting to 2-parameters...
  implicit def blockingIOIO[R](implicit serviceBlocking: Blocking): BlockingIO[ZIO[R, +?, +?]] = {
    blockingIOZIO3(serviceBlocking).asInstanceOf[BlockingIO[ZIO[R, +?, +?]]]
  }

  implicit final def blockingIOZIO3(implicit serviceBlocking: Blocking): BlockingIO3[ZIO] = new BlockingIO3[ZIO] {
    override def shiftBlocking[R, E, A](f: ZIO[R, E ,A]): ZIO[R, E, A] = serviceBlocking.blocking.blocking(f)
    override def syncBlocking[A](f: => A): ZIO[Any, Throwable, A] = serviceBlocking.blocking.blocking(IO(f))
    override def syncInterruptibleBlocking[A](f: => A): ZIO[Any, Throwable, A] = serviceBlocking.blocking.effectBlocking(f)
  }
}

trait LowPriorityBlockingIO3Instances {
  type ZIOBlocking = { type l[-R, +E, +A] = ZIO[R with Blocking, E, A] }

  implicit final def blockingIOZIO3: BlockingIO3[ZIOBlocking#l] = new BlockingIO3[ZIOBlocking#l] {
    override def shiftBlocking[R, E, A](f: ZIO[R with Blocking, E, A]): ZIO[R with Blocking, E, A] = ZIO.accessM(_.blocking.blocking(f))
    override def syncBlocking[A](f: => A): ZIO[Blocking, Throwable, A] = ZIO.accessM(_.blocking.blocking(IO(f)))
    override def syncInterruptibleBlocking[A](f: => A): ZIO[Blocking, Throwable, A] = ZIO.accessM(_.blocking.effectBlocking(f))
  }
}
