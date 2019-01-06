package com.github.pshirshov.izumi.fundamentals.platform.time

import java.time.ZonedDateTime

import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity

trait Clock[+F[_]] {
  /** Should return epoch time in milliseconds (UTC timezone)
    */
  def epoch: F[Long]

  /** Should return current time (UTC timezone)
    */
  def now: F[ZonedDateTime]
}

object Clock {
  def apply[F[_]: Clock]: Clock[F] = implicitly

  object Standard extends Clock[Identity] {

    override def epoch: Long = java.time.Clock.systemUTC().millis()

    override def now: ZonedDateTime = IzTime.utcNow
  }

  class Constant(time: ZonedDateTime) extends Clock[Identity] {

    override def epoch: Long = time.toEpochSecond

    override def now: ZonedDateTime = time
  }

}
