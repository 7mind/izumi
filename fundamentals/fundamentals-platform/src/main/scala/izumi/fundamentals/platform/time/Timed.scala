package izumi.fundamentals.platform.time

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions

final case class Timed[U](value: U, duration: FiniteDuration)

object Timed {
  implicit def toValue[U](timed: Timed[U]): U = timed.value

  def apply[U](f: => U): Timed[U] = {
    val before = System.nanoTime()
    val out = f
    val after = System.nanoTime()
    Timed(out, FiniteDuration.apply(after - before, TimeUnit.NANOSECONDS))
  }
}
