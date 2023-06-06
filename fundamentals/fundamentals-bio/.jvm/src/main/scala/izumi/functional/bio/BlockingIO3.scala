package izumi.functional.bio

import izumi.functional.bio.PredefinedHelper.Predefined
import zio.Executor

import java.util.concurrent.ThreadPoolExecutor
//import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.ZIO

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
object BlockingIOInstances extends BlockingIOLowPriorityVersionSpecific {

  def BlockingZIOFromThreadPool(blockingPool: ThreadPoolExecutor): BlockingIO3[ZIO] = {
    val blockingExecutor = Executor.fromThreadPoolExecutor(blockingPool)
    new BlockingIO3[ZIO] {
      override def shiftBlocking[R, E, A](f: ZIO[R, E, A]): ZIO[R, E, A] = {
        ZIO.environmentWithZIO[R] {
          r =>
            ZIO.provideLayer[Any, E, Any, Any, A](zio.Runtime.setBlockingExecutor(blockingExecutor)) {
              ZIO.blocking(f).provideEnvironment(r)
            }
        }
      }
      override def syncBlocking[A](f: => A): ZIO[Any, Throwable, A] = {
        ZIO.provideLayer(zio.Runtime.setBlockingExecutor(blockingExecutor)) {
          ZIO.attemptBlocking(f)
        }
      }
      override def syncInterruptibleBlocking[A](f: => A): ZIO[Any, Throwable, A] = {
        ZIO.provideLayer(zio.Runtime.setBlockingExecutor(blockingExecutor)) {
          ZIO.attemptBlockingInterrupt(f)
        }
      }
    }
  }

  @inline implicit final def BlockingZIODefault: Predefined.Of[BlockingIO3[ZIO]] = Predefined(new BlockingIO3[ZIO] {
    override def shiftBlocking[R, E, A](f: ZIO[R, E, A]): ZIO[R, E, A] = ZIO.blocking(f)
    override def syncBlocking[A](f: => A): ZIO[Any, Throwable, A] = ZIO.attemptBlocking(f)
    override def syncInterruptibleBlocking[A](f: => A): ZIO[Any, Throwable, A] = ZIO.attemptBlockingInterrupt(f)
  })

//  @inline final def BlockingMonixBIOFromScheduler(ioScheduler: Scheduler): BlockingIO2[monix.bio.IO] = new BlockingIO2[monix.bio.IO] {
//    override def shiftBlocking[R, E, A](f: monix.bio.IO[E, A]): monix.bio.IO[E, A] = f.executeOn(ioScheduler, forceAsync = true)
//    override def syncBlocking[A](f: => A): monix.bio.IO[Throwable, A] = shiftBlocking(monix.bio.IO.eval(f))
//    override def syncInterruptibleBlocking[A](f: => A): monix.bio.IO[Throwable, A] = syncBlocking(f)
//  }

}
