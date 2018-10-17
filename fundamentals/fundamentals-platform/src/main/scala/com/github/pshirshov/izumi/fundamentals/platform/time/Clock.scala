package com.github.pshirshov.izumi.fundamentals.platform.time

import java.time.ZonedDateTime

trait Clock {
  /** Should return epoch time in milliseconds (UTC timezone)
    */
  def epoch: Long

  /** Should return current time (UTC timezone)
    */
  def now: ZonedDateTime
}

object Clock {

  object Standard extends Clock {

    override def epoch: Long = java.time.Clock.systemUTC().millis()

    override def now: ZonedDateTime = IzTime.utcNow
  }

}
