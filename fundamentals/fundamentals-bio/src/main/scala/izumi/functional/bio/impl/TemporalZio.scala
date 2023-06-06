package izumi.functional.bio.impl

import izumi.functional.bio.{Clock3, Temporal3}
import zio.Duration.fromScala
import zio.ZIO

import scala.concurrent.duration.Duration

open class TemporalZio(
  override val clock: Clock3[ZIO]
) extends AsyncZio // use own implementation of timeout to match CE race behavior
  with Temporal3[ZIO] {

  @inline override final def sleep(duration: Duration): ZIO[Any, Nothing, Unit] = {
    zio.Clock.sleep(fromScala(duration))
  }

  @inline override final def timeout[R, E, A](duration: Duration)(r: ZIO[R, E, A]): ZIO[R, E, Option[A]] = {
    this.race(r.map(Some(_)).interruptible, this.sleep(duration).as(None).interruptible)
  }

}
