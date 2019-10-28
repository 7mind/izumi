package izumi.fundamentals.platform.time

import java.time.{LocalDateTime, OffsetDateTime, ZoneId, ZonedDateTime}

import izumi.fundamentals.platform.functional.Identity

trait Clock[+F[_]] {
  /** Should return epoch time in milliseconds (UTC timezone)
    */
  def epoch: F[Long]

  /** Should return current time (UTC timezone)
    */
  def now(accuracy: ClockAccuracy = ClockAccuracy.DEFAULT): F[ZonedDateTime]

  def nowLocal(accuracy: ClockAccuracy = ClockAccuracy.DEFAULT): F[LocalDateTime]
  def nowOffset(accuracy: ClockAccuracy = ClockAccuracy.DEFAULT): F[OffsetDateTime]
}

object Clock {
  final val TZ_UTC: ZoneId = ZoneId.of("UTC")

  def apply[F[_]: Clock]: Clock[F] = implicitly

  class Standard extends Clock[Identity] {
    private def now(): ZonedDateTime = ZonedDateTime.now(TZ_UTC)

    override def epoch: Long = java.time.Clock.systemUTC().millis()

    override def now(accuracy: ClockAccuracy): Identity[ZonedDateTime] = {
      ClockAccuracy.applyAccuracy(now(), accuracy)
    }

    override def nowLocal(accuracy: ClockAccuracy): Identity[LocalDateTime] = {
      now(accuracy).toLocalDateTime
    }

    override def nowOffset(accuracy: ClockAccuracy): Identity[OffsetDateTime] = {
      now(accuracy).toOffsetDateTime
    }
  }

  class Constant(time: ZonedDateTime) extends Clock[Identity] {

    override def epoch: Long = time.toEpochSecond

    override def now(accuracy: ClockAccuracy): Identity[ZonedDateTime] = {
      ClockAccuracy.applyAccuracy(time, accuracy)
    }

    override def nowLocal(accuracy: ClockAccuracy): Identity[LocalDateTime] = {
      now(accuracy).toLocalDateTime
    }

    override def nowOffset(accuracy: ClockAccuracy): Identity[OffsetDateTime] = {
      now(accuracy).toOffsetDateTime
    }
  }

}
