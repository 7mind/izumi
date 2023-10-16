package izumi.functional.bio

import izumi.functional.bio.BlockingIOInstances.BlockingZio
import izumi.functional.bio.PredefinedHelper.Predefined
import izumi.fundamentals.orphans.`zio.ZIO`
import izumi.fundamentals.platform.language.Quirks.Discarder
import zio.internal.stacktracer.{InteropTracer, Tracer}
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Executor, ZIO}

trait BlockingIO2[F[+_, +_]] extends BlockingIOInstances with DivergenceHelper with PredefinedHelper {

  /** Execute a blocking action in `Blocking` thread pool, current task will be safely parked until the blocking task finishes * */
  def shiftBlocking[E, A](f: F[E, A]): F[E, A]

  /** Execute a blocking impure task in `Blocking` thread pool, current task will be safely parked until the blocking task finishes * */
  def syncBlocking[A](f: => A): F[Throwable, A]

  /** Execute a blocking impure task in `Blocking` thread pool, current task will be safely parked until the blocking task finishes
    *
    * If canceled, the task _MAY_ be killed via [[java.lang.Thread#interrupt]], there is no guarantee that this method may promptly,
    * or ever, interrupt the enclosed task, and it may be legally implemented as an alias to [[syncBlocking]]
    *
    * THIS IS USUALLY UNSAFE unless calling well-written libraries that specifically handle [[java.lang.InterruptedException]]
    */
  def syncInterruptibleBlocking[A](f: => A): F[Throwable, A]

}
object BlockingIO2 {
  def apply[F[+_, +_]: BlockingIO2]: BlockingIO2[F] = implicitly
}

private[bio] sealed trait BlockingIOInstances
object BlockingIOInstances extends BlockingIOInstancesLowPriority {

  def BlockingZIOFromExecutor[R](blockingExecutor: Executor): BlockingIO2[ZIO[R, +_, +_]] = new BlockingIO2[ZIO[R, +_, +_]] {
    override def shiftBlocking[E, A](f: ZIO[R, E, A]): ZIO[R, E, A] = {
      implicit val trace: zio.Trace = Tracer.newTrace
      ZIO.environmentWithZIO[R] {
        r =>
          ZIO.provideLayer[Any, E, Any, Any, A](zio.Runtime.setBlockingExecutor(blockingExecutor)) {
            ZIO.blocking(f).provideEnvironment(r)
          }
      }
    }
    override def syncBlocking[A](f: => A): ZIO[Any, Throwable, A] = {
      val byName: () => A = () => f
      implicit val trace: zio.Trace = InteropTracer.newTrace(byName)
      ZIO.provideLayer(zio.Runtime.setBlockingExecutor(blockingExecutor)) {
        ZIO.attemptBlocking(f)
      }
    }
    override def syncInterruptibleBlocking[A](f: => A): ZIO[Any, Throwable, A] = {
      val byName: () => A = () => f
      implicit val trace: zio.Trace = InteropTracer.newTrace(byName)
      ZIO.provideLayer(zio.Runtime.setBlockingExecutor(blockingExecutor)) {
        ZIO.attemptBlockingInterrupt(f)
      }
    }
  }

  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have zio-core as a dependency without REQUIRING a zio-core dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  @inline implicit final def BlockingZIODefault[Zio[-_, +_, +_]: `zio.ZIO`]: Predefined.Of[BlockingIO2[Zio[Any, +_, +_]]] =
    Predefined(BlockingZio.asInstanceOf[BlockingIO2[Zio[Any, +_, +_]]])

  object BlockingZio extends BlockingZio[Any]
  open class BlockingZio[R] extends BlockingIO2[ZIO[R, +_, +_]] {
    override def shiftBlocking[E, A](f: ZIO[R, E, A]): ZIO[R, E, A] = ZIO.blocking(f)(Tracer.newTrace)

    override def syncBlocking[A](f: => A): ZIO[Any, Throwable, A] = {
      val byName: () => A = () => f
      implicit val trace: zio.Trace = InteropTracer.newTrace(byName)
      ZIO.attemptBlocking(f)
    }

    override def syncInterruptibleBlocking[A](f: => A): ZIO[Any, Throwable, A] = {
      val byName: () => A = () => f
      implicit val trace: zio.Trace = InteropTracer.newTrace(byName)
      ZIO.attemptBlockingInterrupt(f)
    }
  }

//  @inline final def BlockingMonixBIOFromScheduler(ioScheduler: Scheduler): BlockingIO2[monix.bio.IO] = new BlockingIO2[monix.bio.IO] {
//    override def shiftBlocking[R, E, A](f: monix.bio.IO[E, A]): monix.bio.IO[E, A] = f.executeOn(ioScheduler, forceAsync = true)
//    override def syncBlocking[A](f: => A): monix.bio.IO[Throwable, A] = shiftBlocking(monix.bio.IO.eval(f))
//    override def syncInterruptibleBlocking[A](f: => A): monix.bio.IO[Throwable, A] = syncBlocking(f)
//  }

  disableAutoTrace.discard()
}

sealed trait BlockingIOInstancesLowPriority {
  @inline implicit final def BlockingZIODefaultR[Zio[-_, +_, +_]: `zio.ZIO`, R]: Predefined.Of[BlockingIO2[Zio[R, +_, +_]]] =
    Predefined(BlockingZio.asInstanceOf[BlockingIO2[Zio[R, +_, +_]]])
}
