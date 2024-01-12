package izumi.functional.bio

import izumi.functional.bio.Clock1.ClockAccuracy
import izumi.functional.bio.DivergenceHelper.{Divergent, Nondivergent}
import izumi.fundamentals.platform.functional.Identity

import java.time.temporal.{ChronoUnit, TemporalUnit}
import java.time.{LocalDateTime, OffsetDateTime, ZoneId, ZonedDateTime}
import scala.annotation.unused
import scala.language.implicitConversions

trait Clock1[F[_]] extends DivergenceHelper {
  /** Should return epoch time in milliseconds (UTC timezone) */
  def epoch: F[Long]

  /** Should return current time (UTC timezone) */
  @deprecated("use nowZoned")
  def now(accuracy: ClockAccuracy = ClockAccuracy.RAW): F[ZonedDateTime]

  def nowZoned(accuracy: ClockAccuracy = ClockAccuracy.RAW): F[ZonedDateTime]
  def nowLocal(accuracy: ClockAccuracy = ClockAccuracy.RAW): F[LocalDateTime]
  def nowOffset(accuracy: ClockAccuracy = ClockAccuracy.RAW): F[OffsetDateTime]

  /** Should return a never decreasing measure of time, in nanoseconds */
  def monotonicNano: F[Long]

  @inline final def widen[G[x] >: F[x]]: Clock1[G] = this
}

object Clock1 extends LowPriorityClockInstances {
  def apply[F[_]: Clock1]: Clock1[F] = implicitly

  def fromImpure[F[_]: SyncSafe1](impureClock: Clock1[Identity]): Clock1[F] = fromImpureClock(impureClock, SyncSafe1[F])

  object Standard extends Clock1[Identity] {

    override def epoch: Long = {
      System.currentTimeMillis()
    }

    override def monotonicNano: Long = {
      System.nanoTime()
    }

    override def nowZoned(accuracy: ClockAccuracy): ZonedDateTime = {
      ClockAccuracy.applyAccuracy(ZonedDateTime.now(TZ_UTC), accuracy)
    }

    @deprecated("use nowZoned")
    override def now(accuracy: ClockAccuracy): ZonedDateTime = {
      nowZoned(accuracy)
    }

    override def nowLocal(accuracy: ClockAccuracy): LocalDateTime = {
      // do not reuse nowZoned because of sjs
      ClockAccuracy.applyAccuracy(LocalDateTime.now(TZ_UTC), accuracy)
    }

    override def nowOffset(accuracy: ClockAccuracy): OffsetDateTime = {
      // do not reuse nowZoned because of sjs
      ClockAccuracy.applyAccuracy(OffsetDateTime.now(TZ_UTC), accuracy)
    }

    private[this] final val TZ_UTC: ZoneId = ZoneId.of("UTC")
  }

  @deprecated("Use ConstantZoned/ConstantOffset")
  final class Constant(time: ZonedDateTime, nano: Long) extends Clock1[Identity] {
    private val underlying = new ConstantZoned(time, nano)

    override def epoch: Long = underlying.epoch
    @deprecated("use nowZoned")
    override def now(accuracy: ClockAccuracy): ZonedDateTime = underlying.now(accuracy)

    override def nowLocal(accuracy: ClockAccuracy): LocalDateTime = underlying.nowLocal(accuracy)
    override def nowOffset(accuracy: ClockAccuracy): OffsetDateTime = underlying.nowOffset(accuracy)
    override def nowZoned(accuracy: ClockAccuracy): Identity[ZonedDateTime] = underlying.nowZoned(accuracy)
    override def monotonicNano: Long = nano
  }

  final class ConstantZoned(time: ZonedDateTime, nano: Long) extends Clock1[Identity] {
    override def epoch: Long = time.toEpochSecond
    @deprecated("use nowZoned")
    override def now(accuracy: ClockAccuracy): ZonedDateTime = nowZoned(accuracy)

    override def nowLocal(accuracy: ClockAccuracy): LocalDateTime = ClockAccuracy.applyAccuracy(time.toLocalDateTime, accuracy)
    override def nowOffset(accuracy: ClockAccuracy): OffsetDateTime = ClockAccuracy.applyAccuracy(time.toOffsetDateTime, accuracy)
    override def nowZoned(accuracy: ClockAccuracy): Identity[ZonedDateTime] = ClockAccuracy.applyAccuracy(time, accuracy)
    override def monotonicNano: Long = nano
  }

  final class ConstantOffset(time: OffsetDateTime, nano: Long) extends Clock1[Identity] {
    override def epoch: Long = time.toEpochSecond
    @deprecated("use nowZoned")
    override def now(accuracy: ClockAccuracy): ZonedDateTime = nowZoned(accuracy)

    override def nowLocal(accuracy: ClockAccuracy): LocalDateTime = ClockAccuracy.applyAccuracy(time.toLocalDateTime, accuracy)
    override def nowOffset(accuracy: ClockAccuracy): OffsetDateTime = ClockAccuracy.applyAccuracy(time, accuracy)
    override def nowZoned(accuracy: ClockAccuracy): Identity[ZonedDateTime] = ClockAccuracy.applyAccuracy(time.toZonedDateTime, accuracy)
    override def monotonicNano: Long = nano
  }

  sealed trait ClockAccuracy
  object ClockAccuracy {
    @deprecated("Use ClockAccuracy.RAW (but better set the limit explicitly!)")
    case object DEFAULT extends ClockAccuracy

    /** The accuracy will not be changed. Generally speaking, it's a bad idea because the actual accuracy might differ between JVM versions (e.g. 11 vs 17)
      */
    case object RAW extends ClockAccuracy

    /** Highest precision, although it might be unsafe to use it because some tools (e.g. PostgreSQL) may be implicitly truncating nanosecond-precision timestamps.
      */
    case object NANO extends ClockAccuracy
    case object MILLIS extends ClockAccuracy

    /**
      * This is the safest choice for PostgreSQL which is known to truncate timestamps to microseconds
      */
    case object MICROS extends ClockAccuracy
    case object SECONDS extends ClockAccuracy
    case object MINUTES extends ClockAccuracy
    case object HOURS extends ClockAccuracy

    private trait TruncatableTime[T] {
      def truncatedTo(timestamp: T, unit: TemporalUnit): T
    }
    private object TruncatableTime {
      implicit object TruncatableZoned extends TruncatableTime[ZonedDateTime] {
        override def truncatedTo(timestamp: ZonedDateTime, unit: TemporalUnit): ZonedDateTime = timestamp.truncatedTo(unit)
      }
      implicit object TruncatableOffset extends TruncatableTime[OffsetDateTime] {
        override def truncatedTo(timestamp: OffsetDateTime, unit: TemporalUnit): OffsetDateTime = timestamp.truncatedTo(unit)
      }
      implicit object TruncatableLocal extends TruncatableTime[LocalDateTime] {
        override def truncatedTo(timestamp: LocalDateTime, unit: TemporalUnit): LocalDateTime = timestamp.truncatedTo(unit)
      }
    }

    private def applyTTAccuracy[T: TruncatableTime](now: T, clockAccuracy: ClockAccuracy): T = {
      val tt = implicitly[TruncatableTime[T]]
      clockAccuracy match {
        case ClockAccuracy.DEFAULT => now
        case ClockAccuracy.RAW => now
        case ClockAccuracy.NANO => tt.truncatedTo(now, ChronoUnit.NANOS)
        case ClockAccuracy.MILLIS => tt.truncatedTo(now, ChronoUnit.MILLIS)
        case ClockAccuracy.MICROS => tt.truncatedTo(now, ChronoUnit.MICROS)
        case ClockAccuracy.SECONDS => tt.truncatedTo(now, ChronoUnit.SECONDS)
        case ClockAccuracy.MINUTES => tt.truncatedTo(now, ChronoUnit.MINUTES)
        case ClockAccuracy.HOURS => tt.truncatedTo(now, ChronoUnit.HOURS)
      }
    }

    def applyAccuracy(now: ZonedDateTime, clockAccuracy: ClockAccuracy): ZonedDateTime = applyTTAccuracy(now, clockAccuracy)

    def applyAccuracy(now: OffsetDateTime, clockAccuracy: ClockAccuracy): OffsetDateTime = applyTTAccuracy(now, clockAccuracy)

    def applyAccuracy(now: LocalDateTime, clockAccuracy: ClockAccuracy): LocalDateTime = applyTTAccuracy(now, clockAccuracy)
  }

  @inline implicit final def impureClock: Clock1[Identity] = Standard

  /**
    * Emulate covariance. We're forced to employ these because
    * we can't make Clock covariant, because covariant implicits
    * are broken (see scalac bug)
    *
    * Safe because `F` appears only in a covariant position
    *
    * @see https://github.com/scala/bug/issues/11427
    */
  @inline implicit final def limitedCovariance2[C[f[_]] <: Clock1[f], F[_, _], E](
    implicit F: C[F[Nothing, _]] { type Divergence = Nondivergent }
  ): Divergent.Of[C[F[E, _]]] = {
    Divergent(F.asInstanceOf[C[F[E, _]]])
  }

  @inline implicit final def limitedCovariance3[C[f[_]] <: Clock1[f], FR[_, _, _], R0, E](
    implicit F: C[FR[Any, Nothing, _]] { type Divergence = Nondivergent }
  ): Divergent.Of[C[FR[R0, E, _]]] = {
    Divergent(F.asInstanceOf[C[FR[R0, E, _]]])
  }

  @inline implicit final def covarianceConversion[F[_], G[_]](clock: Clock1[F])(implicit @unused ev: F[Unit] <:< G[Unit]): Clock1[G] = {
    clock.asInstanceOf[Clock1[G]]
  }
}

sealed trait LowPriorityClockInstances {

  @inline implicit final def fromImpureClock[F[_]](implicit impureClock: Clock1[Identity], F: SyncSafe1[F]): Clock1[F] = {
    new Clock1[F] {
      override val epoch: F[Long] = F.syncSafe(impureClock.epoch)
      @deprecated("use nowZoned")
      override def now(accuracy: ClockAccuracy): F[ZonedDateTime] = nowZoned(accuracy)
      override def nowLocal(accuracy: ClockAccuracy): F[LocalDateTime] = F.syncSafe(impureClock.nowLocal(accuracy))
      override def nowOffset(accuracy: ClockAccuracy): F[OffsetDateTime] = F.syncSafe(impureClock.nowOffset(accuracy))
      override def nowZoned(accuracy: ClockAccuracy): F[ZonedDateTime] = F.syncSafe(impureClock.nowZoned(accuracy))
      override val monotonicNano: F[Long] = F.syncSafe(impureClock.monotonicNano)

    }
  }

}
