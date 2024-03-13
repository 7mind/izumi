package izumi.functional.bio.impl

import izumi.functional.bio.Exit.ZIOExit
import izumi.functional.bio.{Fiber2, Fork2}
import izumi.fundamentals.platform.language.Quirks.Discarder
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.ZIO

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.ExecutionContext

object ForkZio extends ForkZio[Any]

open class ForkZio[R] extends Fork2[ZIO[R, +_, +_]] {

  override def fork[E, A](f: ZIO[R, E, A]): ZIO[R, Nothing, Fiber2[ZIO[R, +_, +_], E, A]] = {
    implicit val trace: zio.Trace = Tracer.instance.empty

    val interrupted = new AtomicBoolean(true) // fiber could be interrupted before executing a single op
    ZIOExit
      .ZIOSignalOnNoExternalInterruptFailure {
        // FIXME: ZIO Bug / feature (interruption inheritance) breaks behavior in bracket/Lifecycle
        //  unless wrapped in `interruptible`
        //  see: https://github.com/zio/zio/issues/945
        f.interruptible
      }(ZIO.succeed(interrupted.set(true)))
      .forkDaemon
      .map(Fiber2.fromZIO(ZIO.succeed(interrupted.get())))
  }

  override def forkOn[E, A](ec: ExecutionContext)(f: ZIO[R, E, A]): ZIO[R, Nothing, Fiber2[ZIO[R, +_, +_], E, A]] = {
    implicit val trace: zio.Trace = Tracer.instance.empty

    val interrupted = new AtomicBoolean(true) // fiber could be interrupted before executing a single op
    ZIOExit
      .ZIOSignalOnNoExternalInterruptFailure {
        // FIXME: ZIO Bug / feature (interruption inheritance) breaks behavior in bracket/Lifecycle
        //  unless wrapped in `interruptible`
        //  see: https://github.com/zio/zio/issues/945
        f.interruptible
          .onExecutionContext(ec)
      }(ZIO.succeed(interrupted.set(true)))
      .forkDaemon
      .map(Fiber2.fromZIO(ZIO.succeed(interrupted.get())))
  }

  disableAutoTrace.discard()
}
