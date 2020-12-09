package izumi.functional.mono

import java.time.{LocalDateTime, OffsetDateTime, ZoneId, ZonedDateTime}

import izumi.fundamentals.platform.functional.Identity

trait Clock[+F[_]] {
  /** Should return epoch time in milliseconds (UTC timezone) */
  def epoch: F[Long]

  /** Should return current time (UTC timezone) */
  def now(accuracy: ClockAccuracy = ClockAccuracy.DEFAULT): F[ZonedDateTime]
  def nowLocal(accuracy: ClockAccuracy = ClockAccuracy.DEFAULT): F[LocalDateTime]
  def nowOffset(accuracy: ClockAccuracy = ClockAccuracy.DEFAULT): F[OffsetDateTime]
}

object Clock {
  def apply[F[_]: Clock]: Clock[F] = implicitly

  private[this] final val TZ_UTC: ZoneId = ZoneId.of("UTC")

  object Standard extends Clock[Identity] {
    override def epoch: Long = {
      java.time.Clock.systemUTC().millis()
    }
    override def now(accuracy: ClockAccuracy): ZonedDateTime = {
      ClockAccuracy.applyAccuracy(ZonedDateTime.now(TZ_UTC), accuracy)
    }
    override def nowLocal(accuracy: ClockAccuracy): LocalDateTime = {
      now(accuracy).toLocalDateTime
    }
    override def nowOffset(accuracy: ClockAccuracy): OffsetDateTime = {
      now(accuracy).toOffsetDateTime
    }
  }

  class Constant(time: ZonedDateTime) extends Clock[Identity] {
    override def epoch: Long = time.toEpochSecond
    override def now(accuracy: ClockAccuracy): ZonedDateTime = ClockAccuracy.applyAccuracy(time, accuracy)
    override def nowLocal(accuracy: ClockAccuracy): LocalDateTime = now(accuracy).toLocalDateTime
    override def nowOffset(accuracy: ClockAccuracy): OffsetDateTime = now(accuracy).toOffsetDateTime
  }

  def fromImpure[F[_]](impureClock: Clock[Identity])(implicit F: SyncSafe[F]): Clock[F] = {
    new Clock[F] {
      override val epoch: F[Long] = F.syncSafe(impureClock.epoch)
      override def now(accuracy: ClockAccuracy): F[ZonedDateTime] = F.syncSafe(impureClock.now(accuracy))
      override def nowLocal(accuracy: ClockAccuracy): F[LocalDateTime] = F.syncSafe(impureClock.nowLocal(accuracy))
      override def nowOffset(accuracy: ClockAccuracy): F[OffsetDateTime] = F.syncSafe(impureClock.nowOffset(accuracy))
    }
  }

}
