package izumi.functional.bio.impl

import izumi.functional.bio.{Clock3, Temporal3}
import zio.ZIO
import zio.duration.Duration.fromScala

import scala.concurrent.duration.Duration

open class TemporalZio(
  override val clock: Clock3[ZIO],
  protected val zioClock: zio.clock.Clock,
) extends AsyncZio
  with Temporal3[ZIO] {

  @inline override final def sleep(duration: Duration): ZIO[Any, Nothing, Unit] = {
    zioClock.get.sleep(fromScala(duration))
  }

  @inline override final def timeout[R, E, A](duration: Duration)(r: ZIO[R, E, A]): ZIO[R, E, Option[A]] = {
    race(r.map(Some(_)).interruptible, this.sleep(duration).as(None).interruptible)
  }

}
