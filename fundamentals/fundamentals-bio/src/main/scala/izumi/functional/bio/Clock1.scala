package izumi.functional.bio

import izumi.functional.bio.Clock1.ClockAccuracy
import izumi.functional.bio.DivergenceHelper.{Divergent, Nondivergent}
import izumi.fundamentals.platform.functional.Identity

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, OffsetDateTime, ZoneId, ZonedDateTime}
import scala.language.implicitConversions

trait Clock1[F[_]] extends DivergenceHelper {
  /** Should return epoch time in milliseconds (UTC timezone) */
  def epoch: F[Long]

  /** Should return current time (UTC timezone) */
  def now(accuracy: ClockAccuracy = ClockAccuracy.DEFAULT): F[ZonedDateTime]
  def nowLocal(accuracy: ClockAccuracy = ClockAccuracy.DEFAULT): F[LocalDateTime]
  def nowOffset(accuracy: ClockAccuracy = ClockAccuracy.DEFAULT): F[OffsetDateTime]

  @inline final def widen[G[x] >: F[x]]: Clock1[G] = this
}

object Clock1 {
  def apply[F[_]: Clock1]: Clock1[F] = implicitly

  private[this] final val TZ_UTC: ZoneId = ZoneId.of("UTC")

  implicit object Standard extends Clock1[Identity] {
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

  final class Constant(time: ZonedDateTime) extends Clock1[Identity] {
    override def epoch: Long = time.toEpochSecond
    override def now(accuracy: ClockAccuracy): ZonedDateTime = ClockAccuracy.applyAccuracy(time, accuracy)
    override def nowLocal(accuracy: ClockAccuracy): LocalDateTime = now(accuracy).toLocalDateTime
    override def nowOffset(accuracy: ClockAccuracy): OffsetDateTime = now(accuracy).toOffsetDateTime
  }

  implicit def fromImpure[F[_]](implicit impureClock: Clock1[Identity], F: SyncSafe1[F]): Clock1[F] = {
    new Clock1[F] {
      override val epoch: F[Long] = F.syncSafe(impureClock.epoch)
      override def now(accuracy: ClockAccuracy): F[ZonedDateTime] = F.syncSafe(impureClock.now(accuracy))
      override def nowLocal(accuracy: ClockAccuracy): F[LocalDateTime] = F.syncSafe(impureClock.nowLocal(accuracy))
      override def nowOffset(accuracy: ClockAccuracy): F[OffsetDateTime] = F.syncSafe(impureClock.nowOffset(accuracy))
    }
  }

  sealed trait ClockAccuracy
  object ClockAccuracy {
    case object DEFAULT extends ClockAccuracy
    case object NANO extends ClockAccuracy
    case object MILLIS extends ClockAccuracy
    case object MICROS extends ClockAccuracy
    case object SECONDS extends ClockAccuracy
    case object MINUTES extends ClockAccuracy
    case object HOURS extends ClockAccuracy

    def applyAccuracy(now: ZonedDateTime, clockAccuracy: ClockAccuracy): ZonedDateTime = {
      clockAccuracy match {
        case ClockAccuracy.DEFAULT => now
        case ClockAccuracy.NANO => now.truncatedTo(ChronoUnit.NANOS)
        case ClockAccuracy.MILLIS => now.truncatedTo(ChronoUnit.MILLIS)
        case ClockAccuracy.MICROS => now.truncatedTo(ChronoUnit.MICROS)
        case ClockAccuracy.SECONDS => now.truncatedTo(ChronoUnit.SECONDS)
        case ClockAccuracy.MINUTES => now.truncatedTo(ChronoUnit.MINUTES)
        case ClockAccuracy.HOURS => now.truncatedTo(ChronoUnit.HOURS)
      }
    }
  }

  /**
    * Emulate covariance. We're forced to employ these because
    * we can't make Clock covariant, because covariant implicits
    * are broken (see scalac bug)
    *
    * Safe because `F` appears only in a covariant position
    *
    * @see https://github.com/scala/bug/issues/11427
    */
  @inline implicit final def limitedCovariance2[C[f[_]] <: Clock1[f], FR[_, _], R0](
    implicit F: C[FR[Nothing, _]] { type Divergence = Nondivergent }
  ): Divergent.Of[C[FR[R0, _]]] = {
    Divergent(F.asInstanceOf[C[FR[R0, _]]])
  }

  @inline implicit final def limitedCovariance3[C[f[_]] <: Clock1[f], FR[_, _, _], R0, E](
    implicit F: C[FR[Any, Nothing, _]] { type Divergence = Nondivergent }
  ): Divergent.Of[C[FR[R0, E, _]]] = {
    Divergent(F.asInstanceOf[C[FR[R0, E, _]]])
  }

  @inline implicit final def covarianceConversion[F[_], G[_]](clock: Clock1[F])(implicit ev: F[?] <:< G[?]): Clock1[G] = {
    val _ = ev; clock.asInstanceOf[Clock1[G]]
  }
}
