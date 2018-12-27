package com.github.pshirshov.izumi.fundamentals.platform.time

import java.time.ZonedDateTime

trait Clock[+F[_]] {
  /** Should return epoch time in milliseconds (UTC timezone)
    */
  def epoch: F[Long]

  /** Should return current time (UTC timezone)
    */
  def now: F[ZonedDateTime]
}

object Clock {

  object Standard extends Clock[Lambda[A => A]] {

    override def epoch: Long = java.time.Clock.systemUTC().millis()

    override def now: ZonedDateTime = IzTime.utcNow
  }

}
