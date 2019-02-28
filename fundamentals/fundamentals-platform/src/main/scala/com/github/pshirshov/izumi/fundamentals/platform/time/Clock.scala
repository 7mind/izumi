package com.github.pshirshov.izumi.fundamentals.platform.time

import java.time.ZonedDateTime

import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity

trait Clock[+F[_]] {
  /** Should return epoch time in milliseconds (UTC timezone)
    */
  def epoch: F[Long]

  /** Should return current time (UTC timezone)
    */
  def now(accuracy: ClockAccuracy = ClockAccuracy.DEFAULT): F[ZonedDateTime]
}

object Clock {
  def apply[F[_] : Clock]: Clock[F] = implicitly

  object Standard extends Clock[Identity] {

    override def epoch: Long = java.time.Clock.systemUTC().millis()

    override def now(accuracy: ClockAccuracy): Identity[ZonedDateTime] = {
      val current = IzTime.utcNow
      ClockAccuracy.applyAccuracy(current, accuracy)
    }
  }

  class Constant(time: ZonedDateTime) extends Clock[Identity] {

    override def epoch: Long = time.toEpochSecond

    override def now(accuracy: ClockAccuracy): Identity[ZonedDateTime] = {
      ClockAccuracy.applyAccuracy(time, accuracy)
    }
  }

}
