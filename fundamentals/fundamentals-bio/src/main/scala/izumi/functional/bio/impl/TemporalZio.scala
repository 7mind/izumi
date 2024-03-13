package izumi.functional.bio.impl

import izumi.functional.bio.Temporal2
import izumi.fundamentals.platform.language.Quirks.Discarder
import zio.Duration.fromScala
import zio.ZIO
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.concurrent.duration.Duration

object TemporalZio extends TemporalZio[Any]

open class TemporalZio[R]
  extends AsyncZio[R] // use own implementation of timeout to match CE race behavior
  with Temporal2[ZIO[R, +_, +_]] {

  @inline override final def sleep(duration: Duration): ZIO[R, Nothing, Unit] = {
    implicit val trace: zio.Trace = Tracer.newTrace

    zio.Clock.sleep(fromScala(duration))
  }

  @inline override final def timeout[E, A](duration: Duration)(r: ZIO[R, E, A]): ZIO[R, E, Option[A]] = {
    implicit val trace: zio.Trace = Tracer.newTrace

    this.race(r.map(Some(_)).interruptible, this.sleep(duration).as(None).interruptible)
  }

  disableAutoTrace.discard()
}
