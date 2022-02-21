package izumi.functional.bio

import izumi.functional.bio.BlockingIOInstances.ZIOWithBlocking
import izumi.functional.bio.DivergenceHelper.{Divergent, Nondivergent}
import izumi.functional.bio.PredefinedHelper.Predefined
import zio.ZIO
import zio.blocking.Blocking
import zio.internal.Executor

import java.util.concurrent.ThreadPoolExecutor

trait BlockingIO3[F[-_, +_, +_]] extends BlockingIOInstances with DivergenceHelper with PredefinedHelper {

  /** Execute a blocking action in `Blocking` thread pool, current task will be safely parked until the blocking task finishes * */
  def shiftBlocking[R, E, A](f: F[R, E, A]): F[R, E, A]

  /** Execute a blocking impure task in `Blocking` thread pool, current task will be safely parked until the blocking task finishes * */
  def syncBlocking[A](f: => A): F[Any, Throwable, A]

  /** Execute a blocking impure task in `Blocking` thread pool, current task will be safely parked until the blocking task finishes
    *
    * If canceled, the task _MAY_ be killed via [[java.lang.Thread#interrupt]], there is no guarantee that this method may promptly,
    * or ever, interrupt the enclosed task, and it may be legally implemented as an alias to [[syncBlocking]]
    *
    * THIS IS USUALLY UNSAFE unless calling well-written libraries that specifically handle [[java.lang.InterruptedException]]
    */
  def syncInterruptibleBlocking[A](f: => A): F[Any, Throwable, A]
}
object BlockingIO3 {
  def apply[F[-_, +_, +_]: BlockingIO3]: BlockingIO3[F] = implicitly
}

private[bio] sealed trait BlockingIOInstances
object BlockingIOInstances extends LowPriorityBlockingIOInstances {

  def BlockingZIOFromThreadPool(blockingPool: ThreadPoolExecutor): BlockingIO3[ZIO] = {
    val executor = Executor.fromThreadPoolExecutor(_ => Int.MaxValue)(blockingPool)
    val blocking: zio.blocking.Blocking.Service = new zio.blocking.Blocking.Service {
      override val blockingExecutor: Executor = executor
    }
    BlockingZIOFromBlocking(blocking)
  }

  def BlockingZIOFromBlocking(b: zio.blocking.Blocking.Service): BlockingIO3[ZIO] = new BlockingIO3[ZIO] {
    override def shiftBlocking[R, E, A](f: ZIO[R, E, A]): ZIO[R, E, A] = b.blocking(f)
    override def syncBlocking[A](f: => A): ZIO[Any, Throwable, A] = b.effectBlocking(f)
    override def syncInterruptibleBlocking[A](f: => A): ZIO[Any, Throwable, A] = b.effectBlockingInterrupt(f)
  }

//  @inline final def BlockingMonixBIOFromScheduler(ioScheduler: Scheduler): BlockingIO2[monix.bio.IO] = new BlockingIO2[monix.bio.IO] {
//    override def shiftBlocking[R, E, A](f: monix.bio.IO[E, A]): monix.bio.IO[E, A] = f.executeOn(ioScheduler, forceAsync = true)
//    override def syncBlocking[A](f: => A): monix.bio.IO[Throwable, A] = shiftBlocking(monix.bio.IO.eval(f))
//    override def syncInterruptibleBlocking[A](f: => A): monix.bio.IO[Throwable, A] = syncBlocking(f)
//  }

  @inline implicit final def blockingZIOFromHasBlocking(implicit blocking: Blocking): Predefined.Of[BlockingIO3[ZIO]] = {
    Predefined(BlockingZIOFromBlocking(blocking.get[Blocking.Service]))
  }

}

sealed trait LowPriorityBlockingIOInstances extends LowPriorityBlockingIOInstances1 {
  type FWithEnv[FR[-_, +_, +_], R0, -R, +E, +A] = FR[R & R0, E, A]

  type IOWithBlocking[+E, +A] = ZIO[Blocking, E, A]
  type ZIOWithBlocking[-R, +E, +A] = ZIO[R & Blocking, E, A]

  implicit final def blockingZIOFromHasBlockingEnvironment2: Predefined.Of[BlockingIO2[IOWithBlocking]] = {
    Predefined(BlockingIOInstances.blockingZIOFromHasBlockingEnvironment3.asInstanceOf[BlockingIO2[IOWithBlocking]])
  }
}

sealed trait LowPriorityBlockingIOInstances1 extends LowPriorityBlockingIOInstances2 {

  implicit final def blockingZIOFromHasBlockingEnvironment3: Predefined.Of[BlockingIO3[ZIOWithBlocking]] = {
    Predefined(new BlockingIO3[ZIOWithBlocking] {
      override def shiftBlocking[R, E, A](f: ZIO[R & Blocking, E, A]): ZIO[R & Blocking, E, A] = zio.blocking.blocking(f)
      override def syncBlocking[A](f: => A): ZIO[Blocking, Throwable, A] = zio.blocking.effectBlocking(f)
      override def syncInterruptibleBlocking[A](f: => A): ZIO[Blocking, Throwable, A] = zio.blocking.effectBlockingInterrupt(f)
    })
  }

}

sealed trait LowPriorityBlockingIOInstances2 {

  @inline implicit final def blockingConvert3To2[C[f[-_, +_, +_]] <: BlockingIO3[f], FR[-_, +_, +_], R](
    implicit BlockingIO3: C[FR] { type Divergence = Nondivergent }
  ): Divergent.Of[BlockingIO2[FR[R, +_, +_]]] = {
    Divergent(BlockingIO3.asInstanceOf[BlockingIO2[FR[R, +_, +_]]])
  }

}
