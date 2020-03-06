package izumi.functional.bio

import java.util.concurrent.ThreadPoolExecutor

import zio.blocking.Blocking
import zio.internal.Executor
import zio.{Has, IO, ZIO}

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
    val blocking: Blocking.Service = new Blocking.Service {
      override val blockingExecutor: Executor = executor
    }
    blockingIOZIO3Blocking(Has(blocking))
  }

  // FIXME: bad encoding for lifting to 2-parameters...
  implicit def blockingIOZIOBlocking[R](implicit blocking: Blocking): BlockingIO[ZIO[R, +?, +?]] = {
    blockingIOZIO3Blocking(blocking).asInstanceOf[BlockingIO[ZIO[R, +?, +?]]]
  }

  implicit final def blockingIOZIO3Blocking(implicit blocking: Blocking): BlockingIO3[ZIO] = new BlockingIO3[ZIO] {
    val b: Blocking.Service = blocking.get
    override def shiftBlocking[R, E, A](f: ZIO[R, E ,A]): ZIO[R, E, A] = b.blocking(f)
    override def syncBlocking[A](f: => A): ZIO[Any, Throwable, A] = b.blocking(IO(f))
    override def syncInterruptibleBlocking[A](f: => A): ZIO[Any, Throwable, A] = b.effectBlocking(f)
  }
}

trait LowPriorityBlockingIOInstances {
  type ZIOBlocking = { type l[-R, +E, +A] = ZIO[R with Blocking, E, A] }

  implicit def blockingIOZIOR[R]: BlockingIO[ZIO[R with Blocking, +?, +?]] = blockingIOZIO3R.asInstanceOf[BlockingIO[ZIO[R with Blocking, +?, +?]]]

  implicit final val blockingIOZIO3R: BlockingIO3[ZIOBlocking#l] = new BlockingIO3[ZIOBlocking#l] {
    override def shiftBlocking[R, E, A](f: ZIO[R with Blocking, E, A]): ZIO[R with Blocking, E, A] = zio.blocking.blocking(f)
    override def syncBlocking[A](f: => A): ZIO[Blocking, Throwable, A] = zio.blocking.effectBlocking(f)
    override def syncInterruptibleBlocking[A](f: => A): ZIO[Blocking, Throwable, A] = zio.blocking.effectBlockingInterrupt(f)
  }
}
