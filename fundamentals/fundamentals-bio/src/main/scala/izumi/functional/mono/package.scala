package izumi.functional

import izumi.functional.bio.{Clock1, Entropy1, SyncSafe1}

package object mono {

  @deprecated("Renamed to izumi.functional.bio.Clock1")
  type Clock[F[_]] = Clock1[F]
  @deprecated("Renamed to izumi.functional.bio.Clock1")
  lazy val Clock: Clock1.type = Clock1

  @deprecated("Renamed to izumi.functional.bio.Clock1.ClockAccuracy")
  type ClockAccuracy = Clock1.ClockAccuracy
  @deprecated("Renamed to izumi.functional.bio.Clock1.ClockAccuracy")
  lazy val ClockAccuracy: Clock1.ClockAccuracy.type = Clock1.ClockAccuracy

  @deprecated("Renamed to izumi.functional.bio.Entropy1")
  type Entropy[F[_]] = Entropy1[F]
  @deprecated("Renamed to izumi.functional.bio.Entropy1")
  lazy val Entropy: Entropy1.type = Entropy1

  @deprecated("Renamed to izumi.functional.bio.SyncSafe1")
  type SyncSafe[F[_]] = SyncSafe1[F]
  @deprecated("Renamed to izumi.functional.bio.SyncSafe1")
  lazy val SyncSafe: SyncSafe1.type = SyncSafe1
}
